package com.taaz.relaxaedorme.data.offline

import com.taaz.relaxaedorme.data.model.MusicTrack
import com.taaz.relaxaedorme.data.remote.CloudflareR2Config
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Status do download offline de uma faixa musical.
 */
enum class TrackDownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    PAUSED_PARTIAL,
    DOWNLOADED,
    ERROR
}

/**
 * Estado detalhado de disponibilidade e download de uma faixa musical individual.
 */
data class TrackDownloadState(
    val trackId: String,
    val status: TrackDownloadStatus = TrackDownloadStatus.NOT_DOWNLOADED,
    val progressFraction: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val isBundledAsset: Boolean = false,
    val isDownloadedFile: Boolean = false,
    val canDownloadFromRemote: Boolean = false,
    val errorMessage: String? = null
) {
    /**
     * Indica se a faixa está pronta para reprodução 100% offline (arquivo baixado ou asset do APK).
     */
    val isAvailableOffline: Boolean
        get() = isDownloadedFile || isBundledAsset
}

/**
 * Resposta de uma requisição HTTP aberta pelo [HttpStreamClient].
 */
data class HttpStreamResponse(
    val statusCode: Int,
    val contentLength: Long,
    val contentRangeHeader: String?,
    val bodyStream: InputStream?,
    val closeConnection: () -> Unit = {}
)

/**
 * Cliente HTTP enxuto para leitura em stream com suporte ao cabeçalho `Range`.
 */
fun interface HttpStreamClient {
    @Throws(IOException::class)
    fun openStream(url: String, rangeStartBytes: Long): HttpStreamResponse
}

/**
 * Implementação padrão baseada em [HttpURLConnection] da plataforma Android/Java.
 */
class UrlConnectionHttpStreamClient(
    private val connectTimeoutMs: Int = CloudflareR2Config.CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = CloudflareR2Config.READ_TIMEOUT_MS
) : HttpStreamClient {

    override fun openStream(url: String, rangeStartBytes: Long): HttpStreamResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept-Encoding", "identity")
            if (rangeStartBytes > 0L) {
                setRequestProperty("Range", "bytes=$rangeStartBytes-")
            }
        }

        try {
            connection.connect()
            val code = connection.responseCode
            val contentLength = connection.contentLengthLong
            val contentRange = connection.getHeaderField("Content-Range")
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            return HttpStreamResponse(
                statusCode = code,
                contentLength = contentLength,
                contentRangeHeader = contentRange,
                bodyStream = stream,
                closeConnection = {
                    try {
                        stream?.close()
                    } catch (_: Exception) {
                    }
                    connection.disconnect()
                }
            )
        } catch (e: Exception) {
            connection.disconnect()
            throw if (e is IOException) e else IOException("Falha na conexão HTTP: ${e.message}", e)
        }
    }
}

/**
 * Representa os valores extraídos de um cabeçalho HTTP `Content-Range` válido.
 * Formatos suportados:
 * - `bytes start-end/total` (para `206 Partial Content`)
 * - `bytes start-end/*` (para `206 Partial Content` com total desconhecido)
 * - `bytes */total` (para `416 Range Not Satisfiable`)
 */
data class ParsedContentRange(
    val startByte: Long?,
    val endByte: Long?,
    val totalBytes: Long?
)

/**
 * Gerenciador de downloads offline de músicas com suporte a retomada (`HTTP Range`),
 * validação rigorosa de integridade e prevenção de downloads duplicados simultâneos.
 */
class MusicDownloadManager(
    private val storageManager: MusicOfflineStorageManager,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val httpClient: HttpStreamClient = UrlConnectionHttpStreamClient()
) {
    private val _downloadStates = MutableStateFlow<Map<String, TrackDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, TrackDownloadState>> = _downloadStates.asStateFlow()

    private val _isLoadingDownloads = MutableStateFlow(false)
    val isLoadingDownloads: StateFlow<Boolean> = _isLoadingDownloads.asStateFlow()

    private val _downloadsErrorMessage = MutableStateFlow<String?>(null)
    val downloadsErrorMessage: StateFlow<String?> = _downloadsErrorMessage.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val trackMutexes = ConcurrentHashMap<String, Mutex>()
    private val jobStartLock = Any()

    /**
     * Sincroniza o estado inicial ou atualizado de uma lista de faixas a partir do armazenamento local.
     */
    fun refreshTracksState(tracks: List<MusicTrack>) {
        _isLoadingDownloads.value = true
        try {
            _downloadStates.update { currentMap ->
                val updated = currentMap.toMutableMap()
                for (track in tracks) {
                    val existingState = updated[track.id]
                    val isCurrentlyDownloading = activeJobs[track.id]?.isActive == true
                    if (isCurrentlyDownloading && existingState != null) {
                        continue
                    }
                    updated[track.id] = buildDiskSnapshotState(track, existingState?.errorMessage)
                }
                updated
            }
            _downloadsErrorMessage.value = null
        } catch (e: Exception) {
            _downloadsErrorMessage.value =
                "Não foi possível consultar os downloads armazenados no dispositivo."
        } finally {
            _isLoadingDownloads.value = false
        }
    }

    /**
     * Constrói o estado atual de uma faixa inspecionando os arquivos `.ogg` e `.ogg.part` no disco.
     */
    fun getTrackState(track: MusicTrack): TrackDownloadState {
        return _downloadStates.value[track.id] ?: buildDiskSnapshotState(track, errorMessage = null)
    }

    private fun buildDiskSnapshotState(
        track: MusicTrack,
        errorMessage: String?
    ): TrackDownloadState {
        val isDownloaded = storageManager.isTrackDownloaded(track)
        val isBundled = storageManager.isBundledAssetAvailable(track)
        val canDownloadRemote = storageManager.canDownloadTrackFromRemote(track)
        val downloadedFile = storageManager.getDownloadedTrackFile(track)
        val partialBytes = storageManager.getPartialDownloadedBytes(track)
        val expectedTotal = track.fileSizeBytes?.takeIf { it > 0L }

        if (isDownloaded) {
            val size = downloadedFile.length().coerceAtLeast(0L)
            return TrackDownloadState(
                trackId = track.id,
                status = TrackDownloadStatus.DOWNLOADED,
                progressFraction = 1f,
                downloadedBytes = size,
                totalBytes = expectedTotal ?: size,
                isBundledAsset = isBundled,
                isDownloadedFile = true,
                canDownloadFromRemote = canDownloadRemote,
                errorMessage = null
            )
        }

        val partialFraction = if (expectedTotal != null && expectedTotal > 0L && partialBytes > 0L) {
            (partialBytes.toFloat() / expectedTotal.toFloat()).coerceIn(0f, 0.99f)
        } else {
            0f
        }

        val status = when {
            errorMessage != null -> TrackDownloadStatus.ERROR
            partialBytes > 0L -> TrackDownloadStatus.PAUSED_PARTIAL
            else -> TrackDownloadStatus.NOT_DOWNLOADED
        }

        return TrackDownloadState(
            trackId = track.id,
            status = status,
            progressFraction = partialFraction,
            downloadedBytes = partialBytes,
            totalBytes = expectedTotal,
            isBundledAsset = isBundled,
            isDownloadedFile = false,
            canDownloadFromRemote = canDownloadRemote,
            errorMessage = errorMessage
        )
    }

    /**
     * Inicia ou retoma o download individual de uma faixa musical.
     * Se a faixa já estiver sendo baixada, ignora a chamada duplicada mantendo o job existente.
     */
    fun downloadTrack(track: MusicTrack): Job {
        synchronized(jobStartLock) {
            val existingJob = activeJobs[track.id]
            if (existingJob != null && existingJob.isActive) {
                return existingJob
            }

            val job = scope.launch {
                val mutex = trackMutexes.getOrPut(track.id) { Mutex() }
                mutex.withLock {
                    executeTrackDownload(track)
                }
            }
            activeJobs[track.id] = job
            job.invokeOnCompletion {
                synchronized(jobStartLock) {
                    if (activeJobs[track.id] === job) {
                        activeJobs.remove(track.id)
                    }
                }
            }
            return job
        }
    }

    /**
     * Inicia o download de todas as faixas de uma categoria/álbum que ainda não foram baixadas.
     */
    fun downloadCategoryTracks(tracks: List<MusicTrack>) {
        for (track in tracks) {
            if (!storageManager.isTrackDownloaded(track) && storageManager.canDownloadTrackFromRemote(track)) {
                downloadTrack(track)
            }
        }
    }

    /**
     * Cancela o download em andamento de uma faixa, preservando o arquivo `.ogg.part` parcial
     * para retomada posterior via HTTP Range.
     */
    fun cancelDownload(track: MusicTrack) {
        val job = synchronized(jobStartLock) {
            activeJobs.remove(track.id)
        }
        job?.cancel()
        _downloadStates.update { current ->
            current + (track.id to buildDiskSnapshotState(track, errorMessage = null))
        }
    }

    /**
     * Cancela todos os downloads ativos de uma categoria.
     */
    fun cancelCategoryDownloads(tracks: List<MusicTrack>) {
        for (track in tracks) {
            if (activeJobs[track.id]?.isActive == true) {
                cancelDownload(track)
            }
        }
    }

    /**
     * Exclui o arquivo `.ogg` efetivamente baixado de uma faixa individual no armazenamento interno,
     * preservando arquivos `.part` de downloads ativos, assets de demonstração e dados do usuário.
     * Atualiza imediatamente [downloadStates].
     */
    fun deleteTrackDownload(track: MusicTrack): Boolean {
        val isActive = synchronized(jobStartLock) {
            activeJobs[track.id]?.isActive == true
        }
        val deleted = storageManager.deleteDownloadedTrackFileOnly(
            track = track,
            isDownloadActive = isActive
        )
        _downloadStates.update { current ->
            if (isActive) {
                current
            } else {
                current + (track.id to buildDiskSnapshotState(track, errorMessage = null))
            }
        }
        return deleted
    }

    /**
     * Exclui todos os arquivos `.ogg` efetivamente baixados das faixas de um álbum,
     * nunca removendo arquivos `.part` de downloads em andamento.
     * Retorna a quantidade de arquivos `.ogg` removidos.
     */
    fun deleteAlbumDownloads(tracks: List<MusicTrack>): Int {
        var removedCount = 0
        val updatedEntries = mutableMapOf<String, TrackDownloadState>()
        for (track in tracks) {
            val isActive = synchronized(jobStartLock) {
                activeJobs[track.id]?.isActive == true
            }
            if (isActive) {
                continue
            }
            if (storageManager.deleteDownloadedTrackFileOnly(track = track, isDownloadActive = false)) {
                removedCount++
            }
            updatedEntries[track.id] = buildDiskSnapshotState(track, errorMessage = null)
        }
        if (updatedEntries.isNotEmpty()) {
            _downloadStates.update { current ->
                current + updatedEntries
            }
        }
        return removedCount
    }

    private suspend fun executeTrackDownload(track: MusicTrack) = withContext(ioDispatcher) {
        if (storageManager.isTrackDownloaded(track)) {
            _downloadStates.update { current ->
                current + (track.id to buildDiskSnapshotState(track, errorMessage = null))
            }
            return@withContext
        }

        val remoteUrl = storageManager.resolveRemoteHttpsUrl(track)
        if (remoteUrl == null) {
            _downloadStates.update { current ->
                current + (track.id to buildDiskSnapshotState(
                    track = track,
                    errorMessage = "Servidor de músicas ainda não configurado."
                ))
            }
            return@withContext
        }

        val partFile = storageManager.getPartialTrackFile(track)
        val finalFile = storageManager.getDownloadedTrackFile(track)

        try {
            partFile.parentFile?.mkdirs()
            val initialBytes = if (partFile.exists() && partFile.isFile) {
                partFile.length().coerceAtLeast(0L)
            } else {
                0L
            }

            updateDownloadingProgress(
                track = track,
                downloadedBytes = initialBytes,
                totalBytes = track.fileSizeBytes
            )

            performDownloadAttempt(
                track = track,
                remoteUrl = remoteUrl,
                partFile = partFile,
                finalFile = finalFile,
                allowRetryAfter416 = true
            )

            _downloadStates.update { current ->
                current + (track.id to buildDiskSnapshotState(track, errorMessage = null))
            }
        } catch (ce: CancellationException) {
            _downloadStates.update { current ->
                current + (track.id to buildDiskSnapshotState(track, errorMessage = null))
            }
            throw ce
        } catch (e: Exception) {
            _downloadStates.update { current ->
                current + (track.id to buildDiskSnapshotState(
                    track = track,
                    errorMessage = e.message ?: "Falha ao baixar \"${track.title}\"."
                ))
            }
        }
    }

    private suspend fun performDownloadAttempt(
        track: MusicTrack,
        remoteUrl: String,
        partFile: File,
        finalFile: File,
        allowRetryAfter416: Boolean
    ) {
        val existingBytes = if (partFile.exists() && partFile.isFile) {
            partFile.length().coerceAtLeast(0L)
        } else {
            0L
        }

        val response = httpClient.openStream(
            url = remoteUrl,
            rangeStartBytes = existingBytes
        )

        try {
            when (response.statusCode) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    if (existingBytes <= 0L) {
                        throw IOException("Resposta HTTP 206 inesperada para requisição sem Range.")
                    }
                    val parsedRange = parseContentRange(response.contentRangeHeader)
                        ?: throw IOException("Cabeçalho Content-Range ausente ou inválido na resposta 206.")

                    if (parsedRange.startByte != existingBytes) {
                        throw IOException(
                            "Content-Range inválido: início esperado $existingBytes, recebido ${parsedRange.startByte}."
                        )
                    }
                    val rangeEnd = parsedRange.endByte
                        ?: throw IOException("Content-Range sem byte final válido.")
                    if (rangeEnd < parsedRange.startByte) {
                        throw IOException("Content-Range inconsistente: fim ($rangeEnd) menor que início ($existingBytes).")
                    }

                    val expectedSessionBytes = (rangeEnd - parsedRange.startByte) + 1L
                    if (response.contentLength > 0L && response.contentLength != expectedSessionBytes) {
                        throw IOException(
                            "Content-Length (${response.contentLength}) diverge do intervalo Content-Range ($expectedSessionBytes)."
                        )
                    }

                    val expectedTotalBytes = parsedRange.totalBytes ?: (existingBytes + expectedSessionBytes)
                    if (parsedRange.totalBytes != null && rangeEnd != parsedRange.totalBytes - 1L) {
                        throw IOException(
                            "Content-Range incompleto: fim ($rangeEnd) não corresponde ao total (${parsedRange.totalBytes})."
                        )
                    }
                    val declaredTrackSize = track.fileSizeBytes?.takeIf { it > 0L }
                    if (declaredTrackSize != null && expectedTotalBytes != declaredTrackSize) {
                        throw IOException(
                            "Tamanho remoto ($expectedTotalBytes) diverge do catálogo ($declaredTrackSize)."
                        )
                    }

                    val inputStream = response.bodyStream
                        ?: throw IOException("Fluxo de dados vazio na resposta HTTP 206.")

                    streamToPartFile(
                        track = track,
                        inputStream = inputStream,
                        partFile = partFile,
                        append = true,
                        startingOffsetBytes = existingBytes,
                        expectedSessionBytes = expectedSessionBytes,
                        expectedTotalBytes = expectedTotalBytes
                    )

                    promotePartFileIfComplete(
                        track = track,
                        partFile = partFile,
                        finalFile = finalFile,
                        expectedTotalBytes = expectedTotalBytes
                    )
                }

                HttpURLConnection.HTTP_OK -> {
                    val contentLength = response.contentLength.takeIf { it > 0L }
                    val declaredTrackSize = track.fileSizeBytes?.takeIf { it > 0L }
                    if (contentLength != null && declaredTrackSize != null && contentLength != declaredTrackSize) {
                        throw IOException(
                            "Tamanho do arquivo ($contentLength) diverge do esperado no catálogo ($declaredTrackSize)."
                        )
                    }
                    val expectedTotalBytes = contentLength ?: declaredTrackSize
                    val inputStream = response.bodyStream
                        ?: throw IOException("Fluxo de dados vazio na resposta HTTP 200.")

                    // Resposta 200 envia o arquivo completo desde o byte 0; sobrescreve .part
                    streamToPartFile(
                        track = track,
                        inputStream = inputStream,
                        partFile = partFile,
                        append = false,
                        startingOffsetBytes = 0L,
                        expectedSessionBytes = contentLength,
                        expectedTotalBytes = expectedTotalBytes
                    )

                    promotePartFileIfComplete(
                        track = track,
                        partFile = partFile,
                        finalFile = finalFile,
                        expectedTotalBytes = expectedTotalBytes
                    )
                }

                HTTP_RANGE_NOT_SATISFIABLE -> {
                    val parsedRange = parseContentRange(response.contentRangeHeader)
                    val remoteTotal = parsedRange?.totalBytes?.takeIf { it > 0L }
                        ?: track.fileSizeBytes?.takeIf { it > 0L }

                    // Se o arquivo .part já possui exatamente o tamanho total esperado, promove-o
                    if (remoteTotal != null && existingBytes == remoteTotal && existingBytes > 0L) {
                        promotePartFileIfComplete(
                            track = track,
                            partFile = partFile,
                            finalFile = finalFile,
                            expectedTotalBytes = remoteTotal
                        )
                        return
                    }

                    // Caso contrário, o .part está fora de faixa/corrompido: remove e reinicia do zero uma única vez
                    if (partFile.exists()) {
                        partFile.delete()
                    }
                    if (!allowRetryAfter416) {
                        throw IOException("Intervalo HTTP 416 inválido mesmo após reiniciar o download.")
                    }
                    response.closeConnection()
                    performDownloadAttempt(
                        track = track,
                        remoteUrl = remoteUrl,
                        partFile = partFile,
                        finalFile = finalFile,
                        allowRetryAfter416 = false
                    )
                }

                else -> {
                    throw IOException("Erro HTTP ${response.statusCode} ao baixar \"${track.title}\".")
                }
            }
        } finally {
            response.closeConnection()
        }
    }

    private suspend fun streamToPartFile(
        track: MusicTrack,
        inputStream: InputStream,
        partFile: File,
        append: Boolean,
        startingOffsetBytes: Long,
        expectedSessionBytes: Long?,
        expectedTotalBytes: Long?
    ) {
        var sessionBytesRead = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE_BYTES)

        FileOutputStream(partFile, append).use { output ->
            while (true) {
                kotlin.coroutines.coroutineContext.ensureActive()
                val readCount = inputStream.read(buffer)
                if (readCount == -1) break
                if (readCount > 0) {
                    output.write(buffer, 0, readCount)
                    sessionBytesRead += readCount
                    val totalSoFar = startingOffsetBytes + sessionBytesRead
                    updateDownloadingProgress(
                        track = track,
                        downloadedBytes = totalSoFar,
                        totalBytes = expectedTotalBytes
                    )
                }
            }
            output.flush()
            try {
                output.fd.sync()
            } catch (_: Exception) {
            }
        }

        if (expectedSessionBytes != null && expectedSessionBytes >= 0L && sessionBytesRead != expectedSessionBytes) {
            throw IOException(
                "Transferência incompleta: recebidos $sessionBytesRead de $expectedSessionBytes bytes esperados."
            )
        }
    }

    private fun promotePartFileIfComplete(
        track: MusicTrack,
        partFile: File,
        finalFile: File,
        expectedTotalBytes: Long?
    ) {
        if (!partFile.exists() || !partFile.isFile) {
            throw IOException("Arquivo temporário ausente após transferência.")
        }

        val finalLength = partFile.length()
        if (finalLength <= 0L) {
            partFile.delete()
            throw IOException("Arquivo baixado está vazio (0 bytes).")
        }

        if (expectedTotalBytes != null && expectedTotalBytes > 0L && finalLength != expectedTotalBytes) {
            throw IOException(
                "Tamanho final do arquivo ($finalLength bytes) diverge do esperado ($expectedTotalBytes bytes)."
            )
        }

        val catalogSize = track.fileSizeBytes?.takeIf { it > 0L }
        if (catalogSize != null && finalLength != catalogSize) {
            throw IOException(
                "Tamanho do arquivo ($finalLength bytes) diverge do tamanho registrado no catálogo ($catalogSize bytes)."
            )
        }

        finalFile.parentFile?.mkdirs()
        try {
            Files.move(
                partFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            try {
                Files.move(
                    partFile.toPath(),
                    finalFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                if (finalFile.exists()) {
                    finalFile.delete()
                }
                if (!partFile.renameTo(finalFile)) {
                    throw IOException("Não foi possível finalizar o arquivo de áudio offline.")
                }
            }
        }
    }

    private fun updateDownloadingProgress(
        track: MusicTrack,
        downloadedBytes: Long,
        totalBytes: Long?
    ) {
        val isBundled = storageManager.isBundledAssetAvailable(track)
        val fraction = if (totalBytes != null && totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
        } else {
            0f
        }
        _downloadStates.update { current ->
            current + (track.id to TrackDownloadState(
                trackId = track.id,
                status = TrackDownloadStatus.DOWNLOADING,
                progressFraction = fraction,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                isBundledAsset = isBundled,
                isDownloadedFile = false,
                canDownloadFromRemote = true,
                errorMessage = null
            ))
        }
    }

    companion object {
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val DEFAULT_BUFFER_SIZE_BYTES = 16 * 1024

        private val CONTENT_RANGE_PARTIAL_REGEX =
            Regex("""^bytes\s+(\d+)-(\d+)/(\d+|\*)$""", RegexOption.IGNORE_CASE)
        private val CONTENT_RANGE_UNSATISFIED_REGEX =
            Regex("""^bytes\s+\*/(\d+)$""", RegexOption.IGNORE_CASE)

        /**
         * Faz o parse seguro do cabeçalho HTTP `Content-Range`.
         */
        fun parseContentRange(headerValue: String?): ParsedContentRange? {
            val clean = headerValue?.trim() ?: return null
            if (clean.isEmpty()) return null

            CONTENT_RANGE_PARTIAL_REGEX.matchEntire(clean)?.let { match ->
                val start = match.groupValues[1].toLongOrNull() ?: return null
                val end = match.groupValues[2].toLongOrNull() ?: return null
                val totalRaw = match.groupValues[3]
                val total = if (totalRaw == "*") null else totalRaw.toLongOrNull() ?: return null
                return ParsedContentRange(
                    startByte = start,
                    endByte = end,
                    totalBytes = total
                )
            }

            CONTENT_RANGE_UNSATISFIED_REGEX.matchEntire(clean)?.let { match ->
                val total = match.groupValues[1].toLongOrNull() ?: return null
                return ParsedContentRange(
                    startByte = null,
                    endByte = null,
                    totalBytes = total
                )
            }

            return null
        }
    }
}
