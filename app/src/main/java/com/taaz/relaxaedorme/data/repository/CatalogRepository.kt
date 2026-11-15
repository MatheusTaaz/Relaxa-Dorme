package com.taaz.relaxaedorme.data.repository

import android.content.Context
import com.taaz.relaxaedorme.data.catalog.SoundCatalog
import com.taaz.relaxaedorme.data.model.AmbientSound
import com.taaz.relaxaedorme.data.model.MusicAlbum
import com.taaz.relaxaedorme.data.model.MusicTrack
import com.taaz.relaxaedorme.data.model.SoundCategory
import com.taaz.relaxaedorme.data.remote.CloudflareR2Config
import com.taaz.relaxaedorme.data.remote.RemoteCatalogParser
import com.taaz.relaxaedorme.ui.components.ArtworkCacheLoader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Repositório de leitura das duas bibliotecas independentes do aplicativo:
 * - Biblioteca Musical organizada exclusivamente por álbuns do Cloudflare R2 (`catalog.json` + cache local)
 * - Biblioteca de Sons Ambientes ([SoundCatalog] 100% embarcado no APK)
 */
class CatalogRepository(
    private val remoteCatalogCacheFile: File? = null,
    private val baseUrlProvider: () -> String = { CloudflareR2Config.BASE_PUBLIC_URL },
    private val minRefreshIntervalMs: Long = DEFAULT_MIN_REFRESH_INTERVAL_MS,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val soundAssetExistsChecker: (String) -> Boolean = { true }
) {
    constructor(context: Context) : this(
        remoteCatalogCacheFile = File(
            context.applicationContext.filesDir,
            REMOTE_CATALOG_CACHE_RELATIVE_PATH
        ),
        baseUrlProvider = { CloudflareR2Config.BASE_PUBLIC_URL },
        soundAssetExistsChecker = { assetPath ->
            val clean = assetPath.trim().trimStart('/')
            clean.isNotEmpty() && try {
                context.applicationContext.assets.open(clean).close()
                true
            } catch (_: Exception) {
                false
            }
        }
    )

    @Volatile
    private var lastSuccessfulRefreshAtMs: Long = 0L

    @Volatile
    private var lastAppliedRawJson: String? = null

    private val _musicAlbumsFlow = MutableStateFlow<List<MusicAlbum>>(emptyList())
    val musicAlbumsFlow: StateFlow<List<MusicAlbum>> = _musicAlbumsFlow.asStateFlow()

    private val _allMusicTracksFlow = MutableStateFlow<List<MusicTrack>>(emptyList())
    val allMusicTracksFlow: StateFlow<List<MusicTrack>> = _allMusicTracksFlow.asStateFlow()

    private val _isLoadingCatalogFlow = MutableStateFlow(false)
    val isLoadingCatalogFlow: StateFlow<Boolean> = _isLoadingCatalogFlow.asStateFlow()

    private val _catalogStatusMessageFlow = MutableStateFlow<String?>(null)
    val catalogStatusMessageFlow: StateFlow<String?> = _catalogStatusMessageFlow.asStateFlow()

    val musicAlbums: List<MusicAlbum>
        get() = _musicAlbumsFlow.value

    val allMusicTracks: List<MusicTrack>
        get() = _allMusicTracksFlow.value

    val soundCategories: List<SoundCategory>
        get() = SoundCatalog.categories

    val allAmbientSounds: List<AmbientSound> by lazy {
        SoundCatalog.sounds.filter { soundAssetExistsChecker(it.audioAssetPath) }
    }

    init {
        loadCachedRemoteCatalogIfPresent()
    }

    private fun loadCachedRemoteCatalogIfPresent() {
        val cacheFile = remoteCatalogCacheFile ?: return
        if (!cacheFile.exists() || !cacheFile.isFile) return
        try {
            val cachedJson = cacheFile.readText(StandardCharsets.UTF_8)
            applyRemoteCatalogJson(cachedJson, persistToDisk = false)
        } catch (_: Exception) {
            // Se o arquivo de cache estiver ilegível, mantém a lista vazia sem inventar dados
        }
    }

    /**
     * Aplica o JSON do catálogo remoto (`catalog.json`), atualizando os álbuns e músicas
     * exibidos no aplicativo e salvando no cache local quando [persistToDisk] for `true`.
     */
    fun applyRemoteCatalogJson(rawJson: String, persistToDisk: Boolean = true): Boolean {
        val dto = RemoteCatalogParser.parseCatalogJson(rawJson) ?: return false
        val albums = RemoteCatalogParser.toDomainAlbums(dto)
        val tracks = albums.flatMap { it.tracks }

        val hasChanged = lastAppliedRawJson != rawJson || _musicAlbumsFlow.value.isEmpty()
        if (hasChanged) {
            lastAppliedRawJson = rawJson
            _musicAlbumsFlow.value = albums
            _allMusicTracksFlow.value = tracks
            ArtworkCacheLoader.clearFailedCache()
        }
        _catalogStatusMessageFlow.value = null

        if (persistToDisk && remoteCatalogCacheFile != null && hasChanged) {
            try {
                remoteCatalogCacheFile.parentFile?.mkdirs()
                remoteCatalogCacheFile.writeText(rawJson, StandardCharsets.UTF_8)
            } catch (_: Exception) {
            }
        }
        return true
    }

    /**
     * Consulta o `catalog.json` hospedado no Cloudflare R2, atualizando o cache local e os
     * fluxos reativos da biblioteca de álbuns.
     *
     * Caso não haja internet ou o catálogo ainda não esteja publicado, preserva o último
     * catálogo válido em cache (ou mantém a biblioteca vazia com mensagem informativa, sem
     * inventar álbuns ou músicas).
     */
    suspend fun refreshRemoteCatalog(
        force: Boolean = false,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Boolean = withContext(ioDispatcher) {
        val nowMs = nowProvider()
        if (!force &&
            lastSuccessfulRefreshAtMs > 0L &&
            (nowMs - lastSuccessfulRefreshAtMs) < minRefreshIntervalMs
        ) {
            return@withContext false
        }

        val catalogUrl = CloudflareR2Config.buildCatalogUrl(baseUrl = baseUrlProvider())
        if (catalogUrl == null) {
            if (_musicAlbumsFlow.value.isEmpty()) {
                _catalogStatusMessageFlow.value = "Catálogo remoto não configurado."
            }
            return@withContext false
        }

        if (!_isLoadingCatalogFlow.compareAndSet(expect = false, update = true)) {
            return@withContext false
        }

        val connection = try {
            (URL(catalogUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CloudflareR2Config.CONNECT_TIMEOUT_MS
                readTimeout = CloudflareR2Config.READ_TIMEOUT_MS
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
                setRequestProperty("Pragma", "no-cache")
            }
        } catch (_: Exception) {
            _isLoadingCatalogFlow.value = false
            if (_musicAlbumsFlow.value.isEmpty()) {
                _catalogStatusMessageFlow.value =
                    "Sem conexão para carregar os álbuns. Conecte-se à internet e tente novamente."
            }
            return@withContext false
        }

        try {
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                if (_musicAlbumsFlow.value.isEmpty()) {
                    _catalogStatusMessageFlow.value =
                        "Catálogo musical indisponível no momento. Tente atualizar novamente em instantes."
                }
                return@withContext false
            }
            val rawJson = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use {
                it.readText()
            }
            val applied = applyRemoteCatalogJson(rawJson, persistToDisk = true)
            if (applied) {
                lastSuccessfulRefreshAtMs = nowProvider()
            } else if (_musicAlbumsFlow.value.isEmpty()) {
                _catalogStatusMessageFlow.value =
                    "Não foi possível ler o catálogo musical remoto."
            }
            applied
        } catch (_: Exception) {
            if (_musicAlbumsFlow.value.isEmpty()) {
                _catalogStatusMessageFlow.value =
                    "Sem conexão com a internet. Conecte-se para carregar a biblioteca de álbuns."
            }
            false
        } finally {
            _isLoadingCatalogFlow.value = false
            connection.disconnect()
        }
    }

    fun getMusicAlbumById(albumId: String): MusicAlbum? =
        _musicAlbumsFlow.value.firstOrNull { it.id == albumId }

    fun getMusicTracksByAlbum(albumId: String): List<MusicTrack> =
        _musicAlbumsFlow.value
            .firstOrNull { it.id == albumId }
            ?.tracks
            ?: _allMusicTracksFlow.value
                .filter { it.albumId == albumId }
                .sortedBy { it.trackOrder }

    fun getMusicTrackById(trackId: String): MusicTrack? =
        _allMusicTracksFlow.value.firstOrNull { it.id == trackId }

    fun getSoundCategoryById(categoryId: String): SoundCategory? =
        SoundCatalog.getCategoryById(categoryId)

    fun getAmbientSoundsByCategory(categoryId: String): List<AmbientSound> =
        allAmbientSounds.filter { it.categoryId == categoryId }

    fun getAmbientSoundById(soundId: String): AmbientSound? =
        allAmbientSounds.firstOrNull { it.id == soundId }

    companion object {
        const val REMOTE_CATALOG_CACHE_RELATIVE_PATH = "catalog/remote_catalog_cache.json"
        const val DEFAULT_MIN_REFRESH_INTERVAL_MS = 5L * 60L * 1000L
    }
}
