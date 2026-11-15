package com.taaz.relaxaedorme.data.offline

import android.content.Context
import com.taaz.relaxaedorme.data.model.MusicTrack
import com.taaz.relaxaedorme.data.remote.CloudflareR2Config
import com.taaz.relaxaedorme.data.remote.LocalDemoMusicAssets
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Representa a origem resolvida para reprodução de uma [MusicTrack] segundo a hierarquia:
 * 1. [DOWNLOADED_FILE]: Arquivo `.ogg` completo salvo no armazenamento privado (`filesDir/offline_music/`).
 * 2. [BUNDLED_ASSET]: Asset `.ogg` de demonstração embarcado no APK (`assets/demo_music/`).
 * 3. [REMOTE_STREAM]: URL HTTPS pública no Cloudflare R2.
 */
enum class ResolvedAudioSourceType {
    DOWNLOADED_FILE,
    BUNDLED_ASSET,
    REMOTE_STREAM
}

/**
 * Resultado da resolução de fonte de áudio para o [ExoPlayer].
 */
data class ResolvedAudioSource(
    val uriString: String,
    val sourceType: ResolvedAudioSourceType,
    val isOfflineReady: Boolean
)

/**
 * Gerenciador de armazenamento offline e resolvedor de fontes de áudio em 3 camadas.
 *
 * Regras estritas de resolução:
 * 1. **Prioridade máxima — Arquivo baixado completo (`.ogg`):**
 *    Se existir `filesDir/offline_music/<albumId>/<trackId>.ogg` com tamanho `> 0` bytes,
 *    reproduz o arquivo local baixado.
 * 2. **Segunda prioridade — Asset de demonstração embarcado no APK (`file:///android_asset/demo_music/...`):**
 *    Se o `track.id` estiver registrado explicitamente em [LocalDemoMusicAssets] e o arquivo exato
 *    existir em `assets/demo_music/`, reproduz o asset local sem internet.
 * 3. **Terceira prioridade — Streaming HTTPS (Cloudflare R2):**
 *    Reproduz via streaming HTTPS a partir de [MusicTrack.remoteAudioPath].
 *
 * Segurança:
 * - Arquivos temporários de download (`.ogg.part`) **nunca** são retornados para reprodução.
 */
class MusicOfflineStorageManager(
    private val offlineRootDir: File,
    private val assetExistsChecker: (String) -> Boolean,
    private val baseUrlProvider: () -> String = { CloudflareR2Config.BASE_PUBLIC_URL }
) {

    constructor(context: Context) : this(
        offlineRootDir = File(context.applicationContext.filesDir, OFFLINE_MUSIC_DIR_NAME),
        assetExistsChecker = createDefaultAssetChecker(context.applicationContext),
        baseUrlProvider = { CloudflareR2Config.BASE_PUBLIC_URL }
    )

    /**
     * Retorna o diretório do álbum dentro do armazenamento privado (`filesDir/offline_music/<albumId>`).
     */
    fun getAlbumDirectory(albumId: String): File {
        val safeAlbum = sanitizeFileSegment(albumId)
        return File(offlineRootDir, safeAlbum)
    }

    /**
     * Retorna o arquivo final `.ogg` de uma faixa baixada.
     * Nunca aponta para um arquivo `.part`.
     */
    fun getDownloadedTrackFile(track: MusicTrack): File {
        val albumDir = getAlbumDirectory(track.albumId)
        val safeTrackId = sanitizeFileSegment(track.id)
        return File(albumDir, "$safeTrackId.ogg")
    }

    /**
     * Retorna o arquivo temporário `.ogg.part` utilizado durante o download com suporte a retomada (HTTP Range).
     */
    fun getPartialTrackFile(track: MusicTrack): File {
        val albumDir = getAlbumDirectory(track.albumId)
        val safeTrackId = sanitizeFileSegment(track.id)
        return File(albumDir, "$safeTrackId.ogg.part")
    }

    /**
     * Verifica se a faixa já possui arquivo `.ogg` final validado e íntegro no armazenamento privado.
     * Arquivos `.part` são estritamente ignorados.
     */
    fun isTrackDownloaded(track: MusicTrack): Boolean {
        val finalFile = getDownloadedTrackFile(track)
        if (!finalFile.exists() || !finalFile.isFile || finalFile.name.endsWith(".part")) {
            return false
        }
        val length = finalFile.length()
        if (length <= 0L) return false
        val expectedSize = track.fileSizeBytes
        if (expectedSize != null && expectedSize > 0L && length != expectedSize) {
            return false
        }
        return true
    }

    /**
     * Verifica se existe um download parcial (`.ogg.part`) salvo em disco que pode ser retomado.
     */
    fun getPartialDownloadedBytes(track: MusicTrack): Long {
        val partFile = getPartialTrackFile(track)
        return if (partFile.exists() && partFile.isFile) {
            partFile.length().coerceAtLeast(0L)
        } else {
            0L
        }
    }

    /**
     * Resolve o caminho exato do asset de demonstração em `assets/demo_music/` por associação
     * explícita de ID ([LocalDemoMusicAssets.getExactAssetPathForTrackId]), sem aproximações.
     */
    fun resolveExactBundledDemoAssetPath(track: MusicTrack): String? {
        val exactPath = LocalDemoMusicAssets.getExactAssetPathForTrackId(track.id)
            ?: track.localAssetPath?.takeIf { it.isNotBlank() }
            ?: return null
        return exactPath.takeIf { assetExistsChecker(it) }
    }

    /**
     * Verifica se a faixa é uma das 4 músicas de demonstração embarcadas em `assets/demo_music/`.
     */
    fun isBundledAssetAvailable(track: MusicTrack): Boolean {
        return resolveExactBundledDemoAssetPath(track) != null
    }

    /**
     * Verifica se a faixa pode ser reproduzida totalmente offline neste momento
     * (seja por arquivo `.ogg` baixado ou por asset de demonstração embarcado no APK).
     */
    fun isTrackAvailableOffline(track: MusicTrack): Boolean {
        return isTrackDownloaded(track) || isBundledAssetAvailable(track)
    }

    /**
     * Constrói a URL HTTPS remota para streaming ou download da faixa no Cloudflare R2,
     * utilizando exclusivamente [MusicTrack.remoteAudioPath].
     */
    fun resolveRemoteHttpsUrl(track: MusicTrack): String? {
        return CloudflareR2Config.buildPublicUrl(
            relativePath = track.remoteAudioPath,
            baseUrl = baseUrlProvider()
        )
    }

    /**
     * Indica se a faixa pode ser baixada a partir do Cloudflare R2.
     */
    fun canDownloadTrackFromRemote(track: MusicTrack): Boolean {
        return resolveRemoteHttpsUrl(track) != null
    }

    /**
     * Resolve a fonte de reprodução seguindo estritamente a prioridade:
     * `Arquivo baixado (.ogg) > Asset de demonstração embarcado no APK > Streaming HTTPS`.
     *
     * Nunca retorna arquivos `.part`.
     */
    fun resolvePlaybackSource(track: MusicTrack): ResolvedAudioSource? {
        // 1. Prioridade: Arquivo baixado completo no armazenamento privado
        val downloadedFile = getDownloadedTrackFile(track)
        if (isTrackDownloaded(track)) {
            return ResolvedAudioSource(
                uriString = downloadedFile.toURI().toString(),
                sourceType = ResolvedAudioSourceType.DOWNLOADED_FILE,
                isOfflineReady = true
            )
        }

        // 2. Prioridade: Asset de demonstração embarcado no APK (assets/demo_music/...)
        return resolvePlaybackSourceWithoutDownloadedFile(track)
    }

    /**
     * Resolve a fonte de reprodução alternativa ignorando o arquivo `.ogg` baixado localmente
     * (`Asset de demonstração embarcado no APK > Streaming HTTPS`).
     *
     * Utilizado para transicionar com segurança uma música em execução antes de excluir seu
     * arquivo `.ogg` baixado do armazenamento interno, sem interromper a reprodução.
     */
    fun resolvePlaybackSourceWithoutDownloadedFile(track: MusicTrack): ResolvedAudioSource? {
        val exactBundledPath = resolveExactBundledDemoAssetPath(track)
        if (exactBundledPath != null) {
            return ResolvedAudioSource(
                uriString = "file:///android_asset/$exactBundledPath",
                sourceType = ResolvedAudioSourceType.BUNDLED_ASSET,
                isOfflineReady = true
            )
        }

        val remoteUrl = resolveRemoteHttpsUrl(track)
        if (remoteUrl != null) {
            return ResolvedAudioSource(
                uriString = remoteUrl,
                sourceType = ResolvedAudioSourceType.REMOTE_STREAM,
                isOfflineReady = false
            )
        }

        return null
    }

    /**
     * Remove exclusivamente o arquivo `.ogg` completo baixado no armazenamento privado do aplicativo.
     *
     * Regras de segurança:
     * - Nunca exclui arquivos `.ogg.part` de downloads ativos.
     * - Nunca altera ou exclui os arquivos de demonstração em `assets/demo_music/` nem arquivos no R2.
     */
    fun deleteDownloadedTrackFileOnly(
        track: MusicTrack,
        isDownloadActive: Boolean = false
    ): Boolean {
        val finalFile = getDownloadedTrackFile(track)
        var deleted = false
        if (finalFile.exists() && finalFile.isFile && !finalFile.name.endsWith(".part")) {
            deleted = finalFile.delete()
        }
        if (!isDownloadActive) {
            val partFile = getPartialTrackFile(track)
            if (partFile.exists() && partFile.isFile) {
                partFile.delete()
            }
        }
        val albumDir = getAlbumDirectory(track.albumId)
        if (albumDir.exists() && albumDir.isDirectory && albumDir.listFiles().isNullOrEmpty()) {
            albumDir.delete()
        }
        return deleted
    }

    /**
     * Remove o arquivo baixado de uma faixa específica preservando `.part` caso [isDownloadActive] seja `true`.
     */
    fun deleteTrackOfflineFiles(
        track: MusicTrack,
        isDownloadActive: Boolean = false
    ): Boolean {
        return deleteDownloadedTrackFileOnly(track = track, isDownloadActive = isDownloadActive)
    }

    companion object {
        const val OFFLINE_MUSIC_DIR_NAME = "offline_music"

        private val assetDirectoryCache = ConcurrentHashMap<String, Set<String>>()

        fun sanitizeFileSegment(raw: String): String {
            return raw.trim()
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .ifEmpty { "unknown" }
        }

        private fun createDefaultAssetChecker(context: Context): (String) -> Boolean {
            return { assetPath ->
                val normalized = assetPath.trim().trimStart('/')
                val parentDir = normalized.substringBeforeLast('/', missingDelimiterValue = "")
                val fileName = normalized.substringAfterLast('/')
                if (fileName.isEmpty()) {
                    false
                } else {
                    val entries = assetDirectoryCache.getOrPut(parentDir) {
                        try {
                            context.assets.list(parentDir)?.toSet() ?: emptySet()
                        } catch (_: Exception) {
                            emptySet()
                        }
                    }
                    fileName in entries
                }
            }
        }
    }
}
