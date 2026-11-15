package com.taaz.relaxaedorme

import com.sun.net.httpserver.HttpServer
import com.taaz.relaxaedorme.data.repository.CatalogRepository
import java.io.File
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CatalogAutoRefreshTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun sampleCatalogJson(albumCount: Int): String {
        val albums = (1..albumCount).joinToString(",") { idx ->
            """
            {
              "id": "album_$idx",
              "title": "Álbum $idx",
              "artist": "mTaaz",
              "description": "Descrição $idx",
              "category": "SLEEP",
              "coverUrl": "albums/album_$idx/cover.png",
              "tracks": [
                {
                  "id": "track_${idx}_1",
                  "title": "Faixa ${idx}.1",
                  "artist": "mTaaz",
                  "remoteAudioPath": "albums/album_$idx/track_1.mp3",
                  "coverUrl": "albums/album_$idx/track_1.png",
                  "durationMs": 180000,
                  "trackOrder": 1
                }
              ]
            }
            """.trimIndent()
        }
        return """{"version": 1, "albums": [$albums]}"""
    }

    @Test
    fun `loads local cache immediately and updates when remote catalog adds new album`() = runBlocking {
        val cacheFile = File(tempFolder.root, "catalog_cache.json")
        cacheFile.writeText(sampleCatalogJson(1), StandardCharsets.UTF_8)

        val serverPayload = AtomicReference(sampleCatalogJson(2))
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/catalog.json") { exchange ->
            val bytes = serverPayload.get().toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val repo = CatalogRepository(
                remoteCatalogCacheFile = cacheFile,
                baseUrlProvider = { baseUrl }
            )

            // 1. Carregamento imediato do cache
            assertEquals(1, repo.musicAlbums.size)

            // 2. Atualização remota com álbum novo
            val updated = repo.refreshRemoteCatalog(force = true)
            assertTrue(updated)
            assertEquals(2, repo.musicAlbums.size)
            assertEquals("Álbum 2", repo.musicAlbums[1].title)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `respects 5 minute interval on foreground return and bypasses when forced`() = runBlocking {
        val cacheFile = File(tempFolder.root, "catalog_cache.json")
        val requestCount = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/catalog.json") { exchange ->
            requestCount.incrementAndGet()
            val bytes = sampleCatalogJson(1).toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            var nowMs = 1_000_000L
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val repo = CatalogRepository(
                remoteCatalogCacheFile = cacheFile,
                baseUrlProvider = { baseUrl },
                minRefreshIntervalMs = CatalogRepository.DEFAULT_MIN_REFRESH_INTERVAL_MS,
                nowProvider = { nowMs }
            )

            assertTrue(repo.refreshRemoteCatalog(force = false))
            assertEquals(1, requestCount.get())

            // Retorno ao primeiro plano antes de 5 minutos -> não consulta
            nowMs += 60_000L
            assertFalse(repo.refreshRemoteCatalog(force = false))
            assertEquals(1, requestCount.get())

            // Atualização manual (force = true) -> ignora intervalo de 5 minutos
            assertTrue(repo.refreshRemoteCatalog(force = true))
            assertEquals(2, requestCount.get())

            // Retorno ao primeiro plano após 5 minutos -> consulta novamente
            nowMs += CatalogRepository.DEFAULT_MIN_REFRESH_INTERVAL_MS + 1_000L
            assertTrue(repo.refreshRemoteCatalog(force = false))
            assertEquals(3, requestCount.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `preserves cached library when offline or when remote catalog is invalid`() = runBlocking {
        val cacheFile = File(tempFolder.root, "catalog_cache.json")
        cacheFile.writeText(sampleCatalogJson(1), StandardCharsets.UTF_8)

        // Sem internet (porta fechada)
        val offlineRepo = CatalogRepository(
            remoteCatalogCacheFile = cacheFile,
            baseUrlProvider = { "http://127.0.0.1:1" }
        )
        assertFalse(offlineRepo.refreshRemoteCatalog(force = true))
        assertEquals(1, offlineRepo.musicAlbums.size)

        // Catálogo remoto inválido
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/catalog.json") { exchange ->
            val bytes = "{invalid_json".toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val invalidRepo = CatalogRepository(
                remoteCatalogCacheFile = cacheFile,
                baseUrlProvider = { "http://127.0.0.1:${server.address.port}" }
            )
            assertFalse(invalidRepo.refreshRemoteCatalog(force = true))
            assertEquals(1, invalidRepo.musicAlbums.size)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `avoids duplicate concurrent remote requests`() = runBlocking {
        val cacheFile = File(tempFolder.root, "catalog_cache.json")
        val requestCount = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/catalog.json") { exchange ->
            requestCount.incrementAndGet()
            Thread.sleep(150)
            val bytes = sampleCatalogJson(1).toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val repo = CatalogRepository(
                remoteCatalogCacheFile = cacheFile,
                baseUrlProvider = { "http://127.0.0.1:${server.address.port}" }
            )
            val results = listOf(
                async { repo.refreshRemoteCatalog(force = true) },
                async { repo.refreshRemoteCatalog(force = true) }
            ).awaitAll()

            assertEquals(1, requestCount.get())
            assertEquals(1, results.count { it })
        } finally {
            server.stop(0)
        }
    }
}
