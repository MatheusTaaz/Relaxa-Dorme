package com.taaz.relaxaedorme

import com.taaz.relaxaedorme.data.model.MusicAlbum
import com.taaz.relaxaedorme.data.model.MusicTrack
import com.taaz.relaxaedorme.data.offline.HttpStreamClient
import com.taaz.relaxaedorme.data.offline.HttpStreamResponse
import com.taaz.relaxaedorme.data.offline.MusicDownloadManager
import com.taaz.relaxaedorme.data.offline.MusicOfflineStorageManager
import com.taaz.relaxaedorme.data.offline.ResolvedAudioSourceType
import com.taaz.relaxaedorme.data.offline.TrackDownloadStatus
import com.taaz.relaxaedorme.data.remote.CloudflareR2Config
import com.taaz.relaxaedorme.data.remote.LocalDemoMusicAssets
import com.taaz.relaxaedorme.data.remote.RemoteCatalogParser
import com.taaz.relaxaedorme.data.repository.CatalogRepository
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareR2AndOfflinePlaybackTest {

    private val testR2BaseUrl = "https://cdn.example.org/r2"

    private fun createTempDir(): File =
        Files.createTempDirectory("relaxa_r2_test_").toFile().apply { deleteOnExit() }

    @Test
    fun `CloudflareR2Config builds catalog and track URLs and encodes spaces as percent20`() {
        assertTrue(CloudflareR2Config.isRemoteConfigured(testR2BaseUrl))
        assertEquals(
            "https://cdn.example.org/r2/catalog.json",
            CloudflareR2Config.buildCatalogUrl(baseUrl = testR2BaseUrl)
        )
        assertEquals(
            "https://cdn.example.org/r2/albums/nordic_fantasy/above%20the%20fjord.ogg",
            CloudflareR2Config.buildPublicUrl(
                relativePath = "albums/nordic_fantasy/above the fjord.ogg",
                baseUrl = testR2BaseUrl
            )
        )
    }

    @Test
    fun `LocalDemoMusicAssets maps the 4 official demo tracks explicitly by ID without fuzzy matching`() {
        assertEquals(4, LocalDemoMusicAssets.OFFICIAL_DEMO_TRACK_ASSETS.size)
        assertEquals(
            "demo_music/nordic_fantasy/above_the_fjord.ogg",
            LocalDemoMusicAssets.getExactAssetPathForTrackId("music_nf_001")
        )
        assertEquals(
            "demo_music/nordic_fantasy/dawn_over_frozen_pine.ogg",
            LocalDemoMusicAssets.getExactAssetPathForTrackId("music_nf_002")
        )
        assertEquals(
            "demo_music/elemental_dreams/air_nomads.ogg",
            LocalDemoMusicAssets.getExactAssetPathForTrackId("music_ed_001")
        )
        assertEquals(
            "demo_music/elemental_dreams/water_tribes.ogg",
            LocalDemoMusicAssets.getExactAssetPathForTrackId("music_ed_002")
        )
        assertNull(LocalDemoMusicAssets.getExactAssetPathForTrackId("music_ed_003"))
    }

    @Test
    fun `RemoteCatalogParser parses single nested albums contract and binds explicit local demo assets`() {
        val rawCatalogJson = """
            {
              "version": 1,
              "albums": [
                {
                  "id": "nordic_fantasy",
                  "title": "Nordic Fantasy",
                  "artist": "Taaz",
                  "description": "Montanhas, aurora boreal e florestas geladas.",
                  "coverUrl": "albums/nordic_fantasy/cover.png",
                  "tracks": [
                    {
                      "id": "music_nf_001",
                      "title": "Above the Fjord",
                      "artist": "Taaz",
                      "remoteAudioPath": "albums/nordic_fantasy/above_the_fjord.ogg",
                      "durationMs": 174262,
                      "fileSizeBytes": 2845120,
                      "trackOrder": 1
                    },
                    {
                      "id": "music_nf_002",
                      "title": "Dawn Over Frozen Pine",
                      "artist": "Taaz",
                      "remoteAudioPath": "albums/nordic_fantasy/dawn_over_frozen_pine.ogg",
                      "durationMs": 188948,
                      "fileSizeBytes": 3102400,
                      "trackOrder": 2
                    }
                  ]
                },
                {
                  "id": "elemental_dreams",
                  "title": "Elemental Dreams",
                  "artist": "Taaz",
                  "description": "Harmonias etéreas inspiradas nos elementos da natureza.",
                  "coverUrl": "albums/elemental_dreams/cover.png",
                  "tracks": [
                    {
                      "id": "music_ed_001",
                      "title": "Air Nomads",
                      "artist": "Taaz",
                      "remoteAudioPath": "albums/elemental_dreams/air_nomads.ogg",
                      "durationMs": 195000,
                      "fileSizeBytes": 3210000,
                      "trackOrder": 1
                    },
                    {
                      "id": "music_ed_002",
                      "title": "Water Tribes",
                      "artist": "Taaz",
                      "remoteAudioPath": "albums/elemental_dreams/water_tribes.ogg",
                      "durationMs": 204000,
                      "fileSizeBytes": 3390000,
                      "trackOrder": 2
                    },
                    {
                      "id": "music_ed_003",
                      "title": "Earth Kingdom",
                      "artist": "Taaz",
                      "remoteAudioPath": "albums/elemental_dreams/earth_kingdom.ogg",
                      "durationMs": 210000,
                      "fileSizeBytes": 3500000,
                      "trackOrder": 3
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val tempDir = createTempDir()
        val cacheFile = File(tempDir, "catalog/remote_catalog_cache.json")
        val repository = CatalogRepository(
            remoteCatalogCacheFile = cacheFile,
            baseUrlProvider = { testR2BaseUrl }
        )

        // Antes de sincronizar e sem cache, a biblioteca está vazia
        assertTrue(repository.musicAlbums.isEmpty())

        assertTrue(repository.applyRemoteCatalogJson(rawCatalogJson, persistToDisk = true))
        assertTrue(cacheFile.exists())

        assertEquals(2, repository.musicAlbums.size)
        assertEquals(5, repository.allMusicTracks.size)

        val nordicAlbum = repository.getMusicAlbumById(MusicAlbum.ID_NORDIC_FANTASY)
        assertNotNull(nordicAlbum)
        assertEquals("Nordic Fantasy", nordicAlbum?.title)
        assertEquals(2, nordicAlbum?.tracks?.size)

        val elementalAlbum = repository.getMusicAlbumById(MusicAlbum.ID_ELEMENTAL_DREAMS)
        assertNotNull(elementalAlbum)
        assertEquals("Elemental Dreams", elementalAlbum?.title)
        assertEquals(3, elementalAlbum?.tracks?.size)

        // As 4 músicas demo possuem localAssetPath explícito; a 5ª música é somente remota
        val nf001 = repository.getMusicTrackById("music_nf_001")!!
        assertEquals("demo_music/nordic_fantasy/above_the_fjord.ogg", nf001.localAssetPath)
        assertEquals("albums/nordic_fantasy/above_the_fjord.ogg", nf001.remoteAudioPath)

        val ed001 = repository.getMusicTrackById("music_ed_001")!!
        assertEquals("demo_music/elemental_dreams/air_nomads.ogg", ed001.localAssetPath)

        val ed002 = repository.getMusicTrackById("music_ed_002")!!
        assertEquals("demo_music/elemental_dreams/water_tribes.ogg", ed002.localAssetPath)

        val ed003 = repository.getMusicTrackById("music_ed_003")!!
        assertNull(ed003.localAssetPath)
        assertEquals("albums/elemental_dreams/earth_kingdom.ogg", ed003.remoteAudioPath)
    }

    @Test
    fun validateRootCatalogJsonIntegrityAndElementalDreamsRelease() {
        val controlledCatalogJson = """
            {
              "version": 1,
              "albums": [
                {
                  "id": "nordic_fantasy",
                  "title": "Nordic Fantasy",
                  "artist": "mTaaz",
                  "description": "Paisagens sonoras nórdicas.",
                  "coverUrl": "covers/nordic_fantasy.png",
                  "tracks": [
                    {
                      "id": "music_nf_001",
                      "title": "Above the Fjord",
                      "artist": "mTaaz",
                      "remoteAudioPath": "albums/nordic_fantasy/above_the_fjord.ogg",
                      "coverUrl": "covers/nordic_fantasy/above_the_fjord.webp"
                    },
                    {
                      "id": "music_nf_002",
                      "title": "Dawn Over Frozen Pine",
                      "artist": "mTaaz",
                      "remoteAudioPath": "albums/nordic_fantasy/dawn_over_frozen_pine.ogg",
                      "coverUrl": "covers/nordic_fantasy/dawn_over_frozen_pine.webp"
                    }
                  ]
                },
                {
                  "id": "elemental_dreams",
                  "title": "Elemental Dreams",
                  "artist": "mTaaz",
                  "description": "Jornada instrumental pelos elementos.",
                  "coverUrl": "covers/elemental_dreams.png",
                  "tracks": [
                    {
                      "id": "music_ed_001",
                      "title": "Air Nomads",
                      "artist": "mTaaz",
                      "remoteAudioPath": "albums/elemental_dreams/air_nomads.ogg",
                      "coverUrl": "covers/elemental_dreams/air_nomads.webp"
                    },
                    {
                      "id": "music_ed_002",
                      "title": "Water Tribes",
                      "artist": "mTaaz",
                      "remoteAudioPath": "albums/elemental_dreams/water_tribes.ogg",
                      "coverUrl": "covers/elemental_dreams/water_tribes.webp"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val dto = RemoteCatalogParser.parseCatalogJson(controlledCatalogJson)
        assertNotNull(dto)
        val albums = RemoteCatalogParser.toDomainAlbums(dto)
        assertEquals(2, albums.size)

        val nordic = albums.first { it.id == "nordic_fantasy" }
        assertEquals(2, nordic.tracks.size)

        val elemental = albums.first { it.id == "elemental_dreams" }
        assertEquals("Elemental Dreams", elemental.title)
        assertEquals("mTaaz", elemental.artist)
        assertEquals(2, elemental.tracks.size)

        val allTrackIds = albums.flatMap { it.tracks.map { t -> t.id } }
        assertEquals(4, allTrackIds.size)
        assertEquals(4, allTrackIds.toSet().size)

        val allPaths = albums.flatMap { it.tracks.map { t -> t.remoteAudioPath } }
        assertEquals(4, allPaths.size)
        assertEquals(4, allPaths.toSet().size)
    }

    @Test
    fun `MusicOfflineStorageManager enforces priority Downloaded over Bundled Asset over HTTPS and never plays part files`() {
        val tempDir = createTempDir()
        val bundledAssets = mutableSetOf("demo_music/nordic_fantasy/above_the_fjord.ogg")
        var baseUrl = testR2BaseUrl

        val storage = MusicOfflineStorageManager(
            offlineRootDir = tempDir,
            assetExistsChecker = { path -> path in bundledAssets },
            baseUrlProvider = { baseUrl }
        )

        val expectedBundledBytes = "complete_ogg_bytes".toByteArray()
        val bundledTrack = MusicTrack(
            id = "music_nf_001",
            title = "Above the Fjord",
            albumId = MusicAlbum.ID_NORDIC_FANTASY,
            remoteAudioPath = "albums/nordic_fantasy/above_the_fjord.ogg",
            localAssetPath = "demo_music/nordic_fantasy/above_the_fjord.ogg",
            fileSizeBytes = expectedBundledBytes.size.toLong()
        )
        val remoteOnlyTrack = MusicTrack(
            id = "music_ed_003",
            title = "Earth Kingdom",
            albumId = MusicAlbum.ID_ELEMENTAL_DREAMS,
            remoteAudioPath = "albums/elemental_dreams/earth_kingdom.ogg",
            localAssetPath = null,
            fileSizeBytes = 12L
        )

        // 1. Sem arquivo baixado e sem asset embarcado -> resolve para REMOTE_STREAM
        val remoteResolved = storage.resolvePlaybackSource(remoteOnlyTrack)
        assertNotNull(remoteResolved)
        assertEquals(ResolvedAudioSourceType.REMOTE_STREAM, remoteResolved?.sourceType)
        assertEquals(
            "https://cdn.example.org/r2/albums/elemental_dreams/earth_kingdom.ogg",
            remoteResolved?.uriString
        )

        // 2. Arquivo .part NUNCA é reproduzido nem considerado baixado
        val partFile = storage.getPartialTrackFile(remoteOnlyTrack)
        partFile.parentFile?.mkdirs()
        partFile.writeBytes("partial".toByteArray())
        assertFalse(storage.isTrackDownloaded(remoteOnlyTrack))
        assertEquals(
            ResolvedAudioSourceType.REMOTE_STREAM,
            storage.resolvePlaybackSource(remoteOnlyTrack)?.sourceType
        )

        // 3. Faixa embarcada no APK sem download -> resolve para BUNDLED_ASSET, mas permite download individual
        val assetResolved = storage.resolvePlaybackSource(bundledTrack)
        assertEquals(ResolvedAudioSourceType.BUNDLED_ASSET, assetResolved?.sourceType)
        assertEquals(bundledTrack.localAssetUri, assetResolved?.uriString)
        assertTrue(storage.canDownloadTrackFromRemote(bundledTrack))

        // 4. Quando a faixa (mesmo embarcada) é baixada em .ogg -> prioridade 1 é DOWNLOADED_FILE
        val downloadedBundledFile = storage.getDownloadedTrackFile(bundledTrack)
        downloadedBundledFile.parentFile?.mkdirs()
        downloadedBundledFile.writeBytes(expectedBundledBytes)

        val downloadedResolved = storage.resolvePlaybackSource(bundledTrack)
        assertEquals(ResolvedAudioSourceType.DOWNLOADED_FILE, downloadedResolved?.sourceType)
        assertTrue(downloadedResolved!!.uriString.endsWith("music_nf_001.ogg"))
        assertFalse(downloadedResolved.uriString.endsWith(".part"))

        // 5. Quando baseUrl está vazia e a faixa não é asset nem baixada -> retorna null
        baseUrl = ""
        assertNull(storage.resolvePlaybackSource(remoteOnlyTrack))
    }

    @Test
    fun `MusicDownloadManager resumes partial downloads with HTTP 206 Range and validates Content-Range`() = runBlocking {
        val tempDir = createTempDir()
        val fullContent = "0123456789ABCDEFGHIJ".toByteArray() // 20 bytes
        val track = MusicTrack(
            id = "music_ed_003",
            title = "Earth Kingdom",
            albumId = MusicAlbum.ID_ELEMENTAL_DREAMS,
            remoteAudioPath = "albums/elemental_dreams/earth_kingdom.ogg",
            fileSizeBytes = fullContent.size.toLong()
        )

        val storage = MusicOfflineStorageManager(
            offlineRootDir = tempDir,
            assetExistsChecker = { false },
            baseUrlProvider = { testR2BaseUrl }
        )

        // Simula 8 bytes já salvos anteriormente em .ogg.part antes do app fechar
        val partFile = storage.getPartialTrackFile(track)
        partFile.parentFile?.mkdirs()
        partFile.writeBytes(fullContent.copyOfRange(0, 8))

        var capturedRangeStart = -1L
        val client = HttpStreamClient { _, rangeStartBytes ->
            capturedRangeStart = rangeStartBytes
            val remaining = fullContent.copyOfRange(rangeStartBytes.toInt(), fullContent.size)
            HttpStreamResponse(
                statusCode = 206,
                contentLength = remaining.size.toLong(),
                contentRangeHeader = "bytes $rangeStartBytes-${fullContent.size - 1}/${fullContent.size}",
                bodyStream = ByteArrayInputStream(remaining)
            )
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = MusicDownloadManager(
            storageManager = storage,
            scope = scope,
            ioDispatcher = Dispatchers.Unconfined,
            httpClient = client
        )

        manager.downloadTrack(track).join()

        assertEquals(8L, capturedRangeStart)
        assertFalse(partFile.exists())
        val finalFile = storage.getDownloadedTrackFile(track)
        assertTrue(finalFile.exists())
        assertArrayEquals(fullContent, finalFile.readBytes())
        assertEquals(
            TrackDownloadStatus.DOWNLOADED,
            manager.getTrackState(track).status
        )
    }

    @Test
    fun `MusicDownloadManager rejects truncated stream or mismatched Content-Range and preserves part file`() = runBlocking {
        val tempDir = createTempDir()
        val fullContent = "0123456789ABCDEFGHIJ".toByteArray() // 20 bytes
        val track = MusicTrack(
            id = "music_ed_004",
            title = "Fire Nation",
            albumId = MusicAlbum.ID_ELEMENTAL_DREAMS,
            remoteAudioPath = "albums/elemental_dreams/fire_nation.ogg",
            fileSizeBytes = fullContent.size.toLong()
        )

        val storage = MusicOfflineStorageManager(
            offlineRootDir = tempDir,
            assetExistsChecker = { false },
            baseUrlProvider = { testR2BaseUrl }
        )

        // Servidor promete 20 bytes no Content-Length mas entrega apenas 10 bytes
        val truncatedClient = HttpStreamClient { _, _ ->
            HttpStreamResponse(
                statusCode = 200,
                contentLength = 20L,
                contentRangeHeader = null,
                bodyStream = ByteArrayInputStream(fullContent.copyOfRange(0, 10))
            )
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = MusicDownloadManager(
            storageManager = storage,
            scope = scope,
            ioDispatcher = Dispatchers.Unconfined,
            httpClient = truncatedClient
        )

        manager.downloadTrack(track).join()

        // Nunca considera download incompleto como concluído: .ogg não existe e .part é preservado com 10 bytes
        assertFalse(storage.getDownloadedTrackFile(track).exists())
        assertTrue(storage.getPartialTrackFile(track).exists())
        assertEquals(10L, storage.getPartialDownloadedBytes(track))
        assertEquals(TrackDownloadStatus.ERROR, manager.getTrackState(track).status)
    }

    @Test
    fun `MusicDownloadManager recovers from HTTP 416 Range Not Satisfiable by restarting from byte 0`() = runBlocking {
        val tempDir = createTempDir()
        val fullContent = " COMPLETE_AUDIO_PAYLOAD ".toByteArray()
        val track = MusicTrack(
            id = "music_ed_005",
            title = "Spirit World",
            albumId = MusicAlbum.ID_ELEMENTAL_DREAMS,
            remoteAudioPath = "albums/elemental_dreams/spirit_world.ogg",
            fileSizeBytes = fullContent.size.toLong()
        )

        val storage = MusicOfflineStorageManager(
            offlineRootDir = tempDir,
            assetExistsChecker = { false },
            baseUrlProvider = { testR2BaseUrl }
        )

        // Cria um .part inválido maior que o arquivo real
        val partFile = storage.getPartialTrackFile(track)
        partFile.parentFile?.mkdirs()
        partFile.writeBytes(ByteArray(fullContent.size + 50))

        val callCount = AtomicInteger(0)
        val client = HttpStreamClient { _, rangeStart ->
            val attempt = callCount.incrementAndGet()
            if (attempt == 1 && rangeStart > 0L) {
                HttpStreamResponse(
                    statusCode = 416,
                    contentLength = 0L,
                    contentRangeHeader = "bytes */${fullContent.size}",
                    bodyStream = null
                )
            } else {
                HttpStreamResponse(
                    statusCode = 200,
                    contentLength = fullContent.size.toLong(),
                    contentRangeHeader = null,
                    bodyStream = ByteArrayInputStream(fullContent)
                )
            }
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = MusicDownloadManager(
            storageManager = storage,
            scope = scope,
            ioDispatcher = Dispatchers.Unconfined,
            httpClient = client
        )

        manager.downloadTrack(track).join()

        assertEquals(2, callCount.get())
        assertTrue(storage.isTrackDownloaded(track))
        assertArrayEquals(fullContent, storage.getDownloadedTrackFile(track).readBytes())
    }

    @Test
    fun `MusicDownloadManager prevents duplicate concurrent downloads for the same track`() = runBlocking {
        val tempDir = createTempDir()
        val fullContent = "AUDIO_DATA_12345".toByteArray()
        val track = MusicTrack(
            id = "music_nf_002",
            title = "Dawn Over Frozen Pine",
            albumId = MusicAlbum.ID_NORDIC_FANTASY,
            remoteAudioPath = "albums/nordic_fantasy/dawn_over_frozen_pine.ogg",
            localAssetPath = "demo_music/nordic_fantasy/dawn_over_frozen_pine.ogg",
            fileSizeBytes = fullContent.size.toLong()
        )

        val storage = MusicOfflineStorageManager(
            offlineRootDir = tempDir,
            assetExistsChecker = { true },
            baseUrlProvider = { testR2BaseUrl }
        )

        val gate = CompletableDeferred<Unit>()
        val httpInvocations = AtomicInteger(0)

        val slowClient = HttpStreamClient { _, _ ->
            httpInvocations.incrementAndGet()
            runBlocking { gate.await() }
            HttpStreamResponse(
                statusCode = 200,
                contentLength = fullContent.size.toLong(),
                contentRangeHeader = null,
                bodyStream = object : InputStream() {
                    private var index = 0
                    override fun read(): Int {
                        return if (index < fullContent.size) {
                            fullContent[index++].toInt() and 0xFF
                        } else {
                            -1
                        }
                    }
                }
            )
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = MusicDownloadManager(
            storageManager = storage,
            scope = scope,
            ioDispatcher = Dispatchers.IO,
            httpClient = slowClient
        )

        val job1 = manager.downloadTrack(track)
        val job2 = manager.downloadTrack(track)

        assertSame(job1, job2)
        gate.complete(Unit)
        job1.join()

        assertEquals(1, httpInvocations.get())
        assertTrue(storage.isTrackDownloaded(track))
    }

    @Test
    fun `LocalDemoMusicAssets resolves individual R2 webp covers for all 30 Elemental Dreams tracks and derives fallback paths`() {
        assertEquals(30, LocalDemoMusicAssets.OFFICIAL_TRACK_COVERS.size)

        // Mesmo quando coverUrl não vem no JSON da faixa, resolve a capa individual .webp determinística
        assertEquals(
            "covers/elemental_dreams/air_nomads.webp",
            LocalDemoMusicAssets.resolveTrackCoverUrl(
                albumId = "elemental_dreams",
                trackId = "music_ed_001",
                remoteAudioPath = "albums/elemental_dreams/air nomads.ogg",
                explicitTrackCoverUrl = null
            )
        )
        assertEquals(
            "covers/elemental_dreams/a_cup_of_peace.webp",
            LocalDemoMusicAssets.resolveTrackCoverUrl(
                albumId = "elemental_dreams",
                trackId = "music_ed_007",
                remoteAudioPath = "albums/elemental_dreams/a cup of peace .ogg",
                explicitTrackCoverUrl = null
            )
        )
        assertEquals(
            "covers/elemental_dreams/wa_shin_tongs_curiosity.webp",
            LocalDemoMusicAssets.resolveTrackCoverUrl(
                albumId = "elemental_dreams",
                trackId = "music_ed_008",
                remoteAudioPath = "albums/elemental_dreams/wa shin tong's curiosity.ogg",
                explicitTrackCoverUrl = null
            )
        )
        assertEquals(
            "covers/elemental_dreams/its_the_moon_itself.webp",
            LocalDemoMusicAssets.resolveTrackCoverUrl(
                albumId = "elemental_dreams",
                trackId = "music_ed_019",
                remoteAudioPath = "albums/elemental_dreams/it's the moon itself.ogg",
                explicitTrackCoverUrl = null
            )
        )

        // Quando explicitTrackCoverUrl é informado no catalog.json, prevalece
        assertEquals(
            "covers/nordic_fantasy.jpg",
            LocalDemoMusicAssets.resolveTrackCoverUrl(
                albumId = "nordic_fantasy",
                trackId = "music_nf_001",
                remoteAudioPath = "albums/nordic_fantasy/above_the_fjord.ogg",
                explicitTrackCoverUrl = "covers/nordic_fantasy.jpg"
            )
        )
    }

    @Test
    fun `MusicDownloadManager deletes individual and album downloads safely while preserving active part files and demo assets`() {
        val tempDir = createTempDir()
        val bundledAssets = setOf("demo_music/elemental_dreams/air_nomads.ogg")

        val storage = MusicOfflineStorageManager(
            offlineRootDir = tempDir,
            assetExistsChecker = { path -> path in bundledAssets },
            baseUrlProvider = { testR2BaseUrl }
        )

        val demoTrack = MusicTrack(
            id = "music_ed_001",
            title = "Air Nomads",
            albumId = MusicAlbum.ID_ELEMENTAL_DREAMS,
            remoteAudioPath = "albums/elemental_dreams/air nomads.ogg",
            localAssetPath = "demo_music/elemental_dreams/air_nomads.ogg",
            fileSizeBytes = 16L
        )
        val remoteTrack1 = MusicTrack(
            id = "music_ed_003",
            title = "Earth Kingdom",
            albumId = MusicAlbum.ID_ELEMENTAL_DREAMS,
            remoteAudioPath = "albums/elemental_dreams/earth kingdom.ogg",
            fileSizeBytes = 16L
        )
        val remoteTrack2 = MusicTrack(
            id = "music_ed_004",
            title = "Fire Nation",
            albumId = MusicAlbum.ID_ELEMENTAL_DREAMS,
            remoteAudioPath = "albums/elemental_dreams/fire nation.ogg",
            fileSizeBytes = 16L
        )

        val payload = "0123456789ABCDEF".toByteArray()
        listOf(demoTrack, remoteTrack1, remoteTrack2).forEach { t ->
            val f = storage.getDownloadedTrackFile(t)
            f.parentFile?.mkdirs()
            f.writeBytes(payload)
        }

        // Cria um arquivo .part em andamento para outra faixa e garante que nunca é deletado se ativo
        val activeTrack = MusicTrack(
            id = "music_ed_005",
            title = "Spirit World",
            albumId = MusicAlbum.ID_ELEMENTAL_DREAMS,
            remoteAudioPath = "albums/elemental_dreams/spirit world.ogg",
            fileSizeBytes = 32L
        )
        val activePartFile = storage.getPartialTrackFile(activeTrack)
        activePartFile.parentFile?.mkdirs()
        activePartFile.writeBytes("PARTIAL_BYTES".toByteArray())
        assertFalse(
            storage.deleteDownloadedTrackFileOnly(activeTrack, isDownloadActive = true)
        )
        assertTrue(activePartFile.exists())

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = MusicDownloadManager(
            storageManager = storage,
            scope = scope,
            ioDispatcher = Dispatchers.Unconfined
        )
        manager.refreshTracksState(listOf(demoTrack, remoteTrack1, remoteTrack2))

        assertTrue(manager.getTrackState(demoTrack).isDownloadedFile)
        assertTrue(manager.getTrackState(remoteTrack1).isDownloadedFile)

        // Exclusão individual da faixa demo baixada: remove apenas o .ogg em filesDir e mantém asset embarcado
        assertTrue(manager.deleteTrackDownload(demoTrack))
        assertFalse(storage.getDownloadedTrackFile(demoTrack).exists())
        val demoStateAfterDelete = manager.getTrackState(demoTrack)
        assertFalse(demoStateAfterDelete.isDownloadedFile)
        assertTrue(demoStateAfterDelete.isBundledAsset)
        assertTrue(demoStateAfterDelete.isAvailableOffline)
        assertEquals(TrackDownloadStatus.NOT_DOWNLOADED, demoStateAfterDelete.status)
        assertEquals(
            ResolvedAudioSourceType.BUNDLED_ASSET,
            storage.resolvePlaybackSource(demoTrack)?.sourceType
        )

        // Exclusão dos downloads restantes do álbum: remove os .ogg de remoteTrack1 e remoteTrack2
        val deletedAlbumCount = manager.deleteAlbumDownloads(listOf(demoTrack, remoteTrack1, remoteTrack2))
        assertEquals(2, deletedAlbumCount)
        assertFalse(storage.getDownloadedTrackFile(remoteTrack1).exists())
        assertFalse(storage.getDownloadedTrackFile(remoteTrack2).exists())
        assertEquals(TrackDownloadStatus.NOT_DOWNLOADED, manager.getTrackState(remoteTrack1).status)
        assertEquals(
            ResolvedAudioSourceType.REMOTE_STREAM,
            storage.resolvePlaybackSourceWithoutDownloadedFile(remoteTrack1)?.sourceType
        )
    }
}
