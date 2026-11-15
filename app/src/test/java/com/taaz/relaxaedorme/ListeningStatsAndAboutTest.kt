package com.taaz.relaxaedorme

import com.taaz.relaxaedorme.audio.ActiveAmbientLayer
import com.taaz.relaxaedorme.audio.AmbientMixerState
import com.taaz.relaxaedorme.audio.ListeningStatsTracker
import com.taaz.relaxaedorme.audio.formatListeningDuration
import com.taaz.relaxaedorme.data.catalog.SoundCatalog
import com.taaz.relaxaedorme.data.repository.ListeningStatsSnapshot
import com.taaz.relaxaedorme.data.repository.ListeningStatsStore
import com.taaz.relaxaedorme.ui.screens.home.resolveAboutAppSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

private class InMemoryListeningStatsStore(
    initial: ListeningStatsSnapshot = ListeningStatsSnapshot()
) : ListeningStatsStore {
    var savedSnapshot: ListeningStatsSnapshot = initial
    var saveCount: Int = 0

    override suspend fun loadSnapshot(): ListeningStatsSnapshot = savedSnapshot

    override suspend fun saveSnapshot(snapshot: ListeningStatsSnapshot) {
        savedSnapshot = snapshot
        saveCount++
    }
}

class ListeningStatsAndAboutTest {

    @Test
    fun `initializes counters at zero when store is empty`() = runBlocking {
        val store = InMemoryListeningStatsStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val tracker = ListeningStatsTracker(
                store = store,
                scope = scope,
                monotonicNowMs = { 1_000L }
            )
            assertEquals(ListeningStatsSnapshot(0L, 0L, 0L), tracker.stats.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `accumulates music time across play pause and resume without counting paused or buffering time`() = runBlocking {
        val store = InMemoryListeningStatsStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var nowMs = 10_000L

        try {
            val tracker = ListeningStatsTracker(
                store = store,
                scope = scope,
                monotonicNowMs = { nowMs },
                persistIntervalMs = 60_000L,
                uiTickIntervalMs = 60_000L
            )

            // Carregando/parado por 20s -> não conta
            tracker.onPlaybackStateChanged(isMusicPlaying = false, isAmbientPlaying = false)
            nowMs += 20_000L
            tracker.onPlaybackStateChanged(isMusicPlaying = false, isAmbientPlaying = false)
            assertEquals(0L, tracker.stats.value.totalMs)

            // Inicia reprodução de música por 30s
            tracker.onPlaybackStateChanged(isMusicPlaying = true, isAmbientPlaying = false)
            nowMs += 30_000L
            tracker.onPlaybackStateChanged(isMusicPlaying = false, isAmbientPlaying = false)
            assertEquals(30_000L, tracker.stats.value.musicMs)
            assertEquals(30_000L, tracker.stats.value.totalMs)
            assertEquals(0L, tracker.stats.value.ambientMs)

            // Pausado por 5 minutos -> não conta
            nowMs += 300_000L

            // Retoma música por mais 15s
            tracker.onPlaybackStateChanged(isMusicPlaying = true, isAmbientPlaying = false)
            nowMs += 15_000L
            tracker.onPlaybackStateChanged(isMusicPlaying = false, isAmbientPlaying = false)

            assertEquals(45_000L, tracker.stats.value.musicMs)
            assertEquals(45_000L, tracker.stats.value.totalMs)
            assertEquals(0L, tracker.stats.value.ambientMs)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `accumulates ambient time once even with three simultaneous mixer layers`() = runBlocking {
        val store = InMemoryListeningStatsStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var nowMs = 1_000L

        try {
            val tracker = ListeningStatsTracker(
                store = store,
                scope = scope,
                monotonicNowMs = { nowMs },
                persistIntervalMs = 60_000L,
                uiTickIntervalMs = 60_000L
            )

            val threePlayingLayers = AmbientMixerState(
                activeLayers = SoundCatalog.sounds.take(3).map { sound ->
                    ActiveAmbientLayer(
                        sound = sound,
                        isPlaying = true
                    )
                }
            )

            // 1 hora com 3 camadas simultâneas no Mixer -> +1h em Sons ambientes e +1h no Total
            tracker.onPlaybackStateChanged(
                isMusicPlaying = false,
                isAmbientPlaying = threePlayingLayers.anyPlaying
            )
            nowMs += 3_600_000L
            tracker.onPlaybackStateChanged(isMusicPlaying = false, isAmbientPlaying = false)

            assertEquals(3_600_000L, tracker.stats.value.ambientMs)
            assertEquals(3_600_000L, tracker.stats.value.totalMs)
            assertEquals(0L, tracker.stats.value.musicMs)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `simultaneous music and ambient counts once in total without duplication`() = runBlocking {
        val store = InMemoryListeningStatsStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var nowMs = 5_000L

        try {
            val tracker = ListeningStatsTracker(
                store = store,
                scope = scope,
                monotonicNowMs = { nowMs },
                persistIntervalMs = 60_000L,
                uiTickIntervalMs = 60_000L
            )

            // Música + sons ambientes tocando juntos por 1 hora
            tracker.onPlaybackStateChanged(isMusicPlaying = true, isAmbientPlaying = true)
            nowMs += 3_600_000L
            tracker.onPlaybackStateChanged(isMusicPlaying = false, isAmbientPlaying = false)

            assertEquals(3_600_000L, tracker.stats.value.musicMs)
            assertEquals(3_600_000L, tracker.stats.value.ambientMs)
            assertEquals(3_600_000L, tracker.stats.value.totalMs)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `persists across app restarts and recovers after unexpected kill without counting downtime`() = runBlocking {
        val store = InMemoryListeningStatsStore()
        var nowMs = 1_000L

        // Sessão 1 (ex: reprodução em segundo plano e encerramento inesperado após flush periódico)
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val tracker1 = ListeningStatsTracker(
            store = store,
            scope = scope1,
            monotonicNowMs = { nowMs },
            persistIntervalMs = 15_000L,
            uiTickIntervalMs = 60_000L
        )
        tracker1.onPlaybackStateChanged(isMusicPlaying = true, isAmbientPlaying = false)
        nowMs += 60_000L
        tracker1.flushAndPersist()
        scope1.cancel()

        // Simula dispositivo desligado / processo morto por 2 horas
        nowMs += 7_200_000L

        // Sessão 2: reabre o app e recupera o acumulado sem somar as 2h offline/morto
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val tracker2 = ListeningStatsTracker(
                store = store,
                scope = scope2,
                monotonicNowMs = { nowMs },
                persistIntervalMs = 15_000L,
                uiTickIntervalMs = 60_000L
            )
            assertEquals(60_000L, tracker2.stats.value.musicMs)
            assertEquals(60_000L, tracker2.stats.value.totalMs)

            // Nova reprodução de som ambiente por 30s
            tracker2.onPlaybackStateChanged(isMusicPlaying = false, isAmbientPlaying = true)
            nowMs += 30_000L
            tracker2.onPlaybackStateChanged(isMusicPlaying = false, isAmbientPlaying = false)

            assertEquals(60_000L, tracker2.stats.value.musicMs)
            assertEquals(30_000L, tracker2.stats.value.ambientMs)
            assertEquals(90_000L, tracker2.stats.value.totalMs)
        } finally {
            scope2.cancel()
        }
    }

    @Test
    fun `formats listening duration and resolves dynamic about summary accurately`() {
        assertEquals("0 min", formatListeningDuration(0L))
        assertEquals("42 min", formatListeningDuration(42L * 60_000L))
        assertEquals("12h 35min", formatListeningDuration((12L * 60L + 35L) * 60_000L))
        assertEquals("128h 42min", formatListeningDuration((128L * 60L + 42L) * 60_000L))

        val summary = resolveAboutAppSummary(
            versionName = "1.0.2",
            albumCount = 2,
            trackCount = 46
        )
        assertEquals("Relaxa e Dorme", summary.appName)
        assertEquals("mTaaz", summary.creator)
        assertEquals("Versão 1.0.2", summary.versionLabel)
        assertEquals("2 álbuns", summary.albumsLabel)
        assertEquals("46 faixas", summary.tracksLabel)
    }
}
