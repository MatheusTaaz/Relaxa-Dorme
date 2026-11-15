package com.taaz.relaxaedorme.audio

import android.os.SystemClock
import com.taaz.relaxaedorme.data.repository.ListeningStatsSnapshot
import com.taaz.relaxaedorme.data.repository.ListeningStatsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Rastreador centralizado e monotônico de tempo real de audição no dispositivo.
 *
 * Regras garantidas:
 * - Contabiliza exclusivamente quando há áudio efetivamente reproduzindo (sem contar pausa, parada ou buffering).
 * - Múltiplas camadas simultâneas no Mixer contam apenas 1x para `ambientMs`.
 * - Reprodução simultânea de música e sons ambientes conta 1x para `totalMs` (sem duplicidade).
 * - Utiliza [monotonicNowMs] (padrão [SystemClock.elapsedRealtime]) para evitar distorções de relógio.
 * - Persiste periodicamente e imediatamente nas transições de pausa/parada.
 */
class ListeningStatsTracker(
    private val store: ListeningStatsStore,
    private val scope: CoroutineScope,
    private val monotonicNowMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val persistIntervalMs: Long = DEFAULT_PERSIST_INTERVAL_MS,
    private val uiTickIntervalMs: Long = DEFAULT_UI_TICK_INTERVAL_MS
) {
    private val lock = Any()

    private val _stats = MutableStateFlow(ListeningStatsSnapshot())
    val stats: StateFlow<ListeningStatsSnapshot> = _stats.asStateFlow()

    private var isInitialized = false
    private var accumulatedTotalMs = 0L
    private var accumulatedMusicMs = 0L
    private var accumulatedAmbientMs = 0L

    private var wasMusicPlaying = false
    private var wasAmbientPlaying = false
    private var lastTickElapsedMs = 0L
    private var lastPersistElapsedMs = 0L

    private var tickerJob: Job? = null

    init {
        scope.launch {
            val loaded = runCatching { store.loadSnapshot() }.getOrDefault(ListeningStatsSnapshot())
            synchronized(lock) {
                accumulatedTotalMs += loaded.totalMs.coerceAtLeast(0L)
                accumulatedMusicMs += loaded.musicMs.coerceAtLeast(0L)
                accumulatedAmbientMs += loaded.ambientMs.coerceAtLeast(0L)
                isInitialized = true
                _stats.value = currentSnapshotLocked()
            }
        }
    }

    /**
     * Deve ser chamado sempre que o estado real de reprodução de música ou sons ambientes mudar.
     */
    fun onPlaybackStateChanged(
        isMusicPlaying: Boolean,
        isAmbientPlaying: Boolean
    ) {
        var shouldStartTicker = false
        var shouldStopTicker = false
        var snapshotToPersist: ListeningStatsSnapshot? = null

        synchronized(lock) {
            val now = monotonicNowMs()
            commitElapsedLocked(now)

            val wasAnyPlaying = wasMusicPlaying || wasAmbientPlaying
            val isAnyPlaying = isMusicPlaying || isAmbientPlaying

            wasMusicPlaying = isMusicPlaying
            wasAmbientPlaying = isAmbientPlaying

            if (isAnyPlaying) {
                if (!wasAnyPlaying) {
                    lastTickElapsedMs = now
                    if (lastPersistElapsedMs == 0L) {
                        lastPersistElapsedMs = now
                    }
                }
                if (tickerJob == null || tickerJob?.isActive != true) {
                    shouldStartTicker = true
                }
            } else {
                if (wasAnyPlaying) {
                    shouldStopTicker = true
                    lastPersistElapsedMs = now
                    snapshotToPersist = currentSnapshotLocked()
                }
                lastTickElapsedMs = 0L
            }

            if (snapshotToPersist == null &&
                isAnyPlaying &&
                lastPersistElapsedMs > 0L &&
                (now - lastPersistElapsedMs) >= persistIntervalMs
            ) {
                lastPersistElapsedMs = now
                snapshotToPersist = currentSnapshotLocked()
            }

            _stats.value = currentSnapshotLocked()
        }

        if (shouldStopTicker) {
            tickerJob?.cancel()
            tickerJob = null
        }

        if (snapshotToPersist != null && isInitialized) {
            val toSave = snapshotToPersist!!
            scope.launch {
                runCatching { store.saveSnapshot(toSave) }
            }
        }

        if (shouldStartTicker) {
            startTicker()
        }
    }

    /**
     * Força a consolidação do tempo decorrido até o momento e persiste em disco imediatamente.
     */
    suspend fun flushAndPersist() {
        val snapshotToSave = synchronized(lock) {
            val now = monotonicNowMs()
            commitElapsedLocked(now)
            lastPersistElapsedMs = now
            val snapshot = currentSnapshotLocked()
            _stats.value = snapshot
            snapshot
        }
        runCatching { store.saveSnapshot(snapshotToSave) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(uiTickIntervalMs)
                var snapshotToPersist: ListeningStatsSnapshot? = null
                val stillPlaying: Boolean

                synchronized(lock) {
                    val now = monotonicNowMs()
                    commitElapsedLocked(now)
                    stillPlaying = wasMusicPlaying || wasAmbientPlaying

                    if (stillPlaying && (now - lastPersistElapsedMs) >= persistIntervalMs) {
                        lastPersistElapsedMs = now
                        snapshotToPersist = currentSnapshotLocked()
                    }
                    _stats.value = currentSnapshotLocked()
                }

                if (snapshotToPersist != null && isInitialized) {
                    val toSave = snapshotToPersist!!
                    runCatching { store.saveSnapshot(toSave) }
                }

                if (!stillPlaying) {
                    break
                }
            }
        }
    }

    private fun commitElapsedLocked(nowElapsedMs: Long) {
        val wasAnyPlaying = wasMusicPlaying || wasAmbientPlaying
        if (!wasAnyPlaying) {
            lastTickElapsedMs = 0L
            return
        }
        if (lastTickElapsedMs <= 0L) {
            lastTickElapsedMs = nowElapsedMs
            return
        }
        val delta = (nowElapsedMs - lastTickElapsedMs).coerceAtLeast(0L)
        if (delta > 0L) {
            accumulatedTotalMs += delta
            if (wasMusicPlaying) {
                accumulatedMusicMs += delta
            }
            if (wasAmbientPlaying) {
                accumulatedAmbientMs += delta
            }
        }
        lastTickElapsedMs = nowElapsedMs
    }

    private fun currentSnapshotLocked(): ListeningStatsSnapshot {
        return ListeningStatsSnapshot(
            totalMs = accumulatedTotalMs,
            musicMs = accumulatedMusicMs,
            ambientMs = accumulatedAmbientMs
        )
    }

    companion object {
        const val DEFAULT_PERSIST_INTERVAL_MS = 15_000L
        const val DEFAULT_UI_TICK_INTERVAL_MS = 1_000L
    }
}

/**
 * Formata uma duração em milissegundos para exibição humana na tela SOBRE:
 * - Menos de 1 hora: `42 min` (ou `0 min`)
 * - A partir de 1 hora: `12h 35min` (continua exibindo horas totais acima de 24h, sem converter para dias)
 */
fun formatListeningDuration(millis: Long): String {
    val safeMs = millis.coerceAtLeast(0L)
    val totalMinutes = safeMs / 60_000L
    if (totalMinutes < 60L) {
        return "$totalMinutes min"
    }
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return "${hours}h ${minutes}min"
}
