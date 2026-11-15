package com.taaz.relaxaedorme.audio

import com.taaz.relaxaedorme.data.model.MusicAlbum
import com.taaz.relaxaedorme.data.model.MusicTrack
import java.util.Locale
import kotlin.random.Random

/**
 * Modos de repetição suportados pelo player musical.
 *
 * Ciclo oficial (Etapa 7):
 * Repetição desativada (OFF) -> Repetir a música atual (REPEAT_ONE) -> Repetir a sequência/lista atual (REPEAT_ALL) -> OFF.
 */
enum class MusicRepeatMode(val label: String) {
    /** Reproduz a fila em sequência e encerra ao final da última música. */
    OFF("Repetição desativada"),

    /** Repete continuamente a faixa atual (tem prioridade sobre o avanço do shuffle). */
    REPEAT_ONE("Repetir faixa"),

    /** Repete toda a fila/álbum ao chegar ao final da lista. */
    REPEAT_ALL("Repetir lista");

    fun nextMode(): MusicRepeatMode = when (this) {
        OFF -> REPEAT_ONE
        REPEAT_ONE -> REPEAT_ALL
        REPEAT_ALL -> OFF
    }
}

/**
 * Estado dedicado exclusivamente ao progresso temporal da faixa em reprodução.
 *
 * Separado de [MusicPlaybackState] para que atualizações frequentes (a cada 250 ms)
 * recomponham apenas os componentes que exibem tempo/barra de progresso (Mini-Player e
 * Player Expandido), evitando recompor as telas principais inteiras.
 */
data class MusicProgressState(
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
) {
    val progressFraction: Float
        get() {
            if (durationMs <= 0L) return 0f
            return (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }

    val formattedPosition: String
        get() = MusicPlaybackState.formatMillis(currentPositionMs)

    val formattedDuration: String
        get() = MusicPlaybackState.formatMillis(durationMs)
}

/**
 * Estado estrutural do player musical observado pelas telas principais.
 * Só emite quando há mudança de faixa, estado Play/Pause, fila, shuffle, volume, repetição ou erro.
 */
data class MusicPlaybackState(
    val currentTrack: MusicTrack? = null,
    val currentAlbum: MusicAlbum? = null,
    val queue: List<MusicTrack> = emptyList(),
    val originalQueue: List<MusicTrack> = emptyList(),
    val currentQueueIndex: Int = -1,
    val queueTitle: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val volume: Float = MusicTrack.DEFAULT_MUSIC_VOLUME,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: MusicRepeatMode = MusicRepeatMode.REPEAT_ALL,
    val errorMessage: String? = null
) {
    /**
     * Duração efetiva considerando a duração reportada pelo ExoPlayer ou o metadado do catálogo.
     */
    val effectiveDurationMs: Long
        get() = when {
            durationMs > 0L -> durationMs
            currentTrack?.durationMs != null && currentTrack.durationMs > 0L -> currentTrack.durationMs
            else -> 0L
        }

    val hasPreviousInQueue: Boolean
        get() = currentQueueIndex > 0

    val hasNextInQueue: Boolean
        get() = currentQueueIndex in 0 until (queue.size - 1)

    companion object {
        /**
         * Limiar (em milissegundos) a partir do qual o botão "Anterior" reinicia a faixa atual
         * em vez de voltar para a música anterior da fila.
         */
        const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L

        fun formatMillis(millis: Long): String {
            val safeMs = millis.coerceAtLeast(0L)
            val totalSeconds = safeMs / 1000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }

        /**
         * Regra pura para o botão "Música Anterior":
         * - Se a reprodução já passou de [PREVIOUS_RESTART_THRESHOLD_MS], volta ao início da faixa.
         * - Caso contrário, se houver faixa anterior (ou `REPEAT_ALL` com mais de 1 música), retorna à anterior.
         * - Se estiver na primeira faixa e `repeatMode != REPEAT_ALL`, volta ao início da faixa.
         */
        fun shouldRestartCurrentTrackOnPrevious(
            positionMs: Long,
            currentQueueIndex: Int,
            queueSize: Int,
            repeatMode: MusicRepeatMode
        ): Boolean {
            if (queueSize <= 0) return true
            if (positionMs > PREVIOUS_RESTART_THRESHOLD_MS) return true
            if (currentQueueIndex > 0) return false
            return !(repeatMode == MusicRepeatMode.REPEAT_ALL && queueSize > 1)
        }

        /**
         * Determina se uma transição de faixa deve registrar a música no histórico recente,
         * ignorando repetições automáticas (`REPEAT_ONE` / loop da mesma faixa) e duplicações consecutivas.
         */
        fun shouldRecordRecentOnTransition(
            newTrackId: String?,
            lastRecordedTrackId: String?,
            isRepeatTransition: Boolean
        ): Boolean {
            if (newTrackId.isNullOrBlank()) return false
            if (isRepeatTransition) return false
            return newTrackId != lastRecordedTrackId
        }

        /**
         * Gera uma sequência embaralhada sem repetições prematuras (permutação completa do álbum/fila):
         * - Se [lockedFirstTrack] for informado e pertencer a [tracks], mantém essa faixa na primeira
         *   posição (índice 0) e embaralha todas as demais faixas restantes.
         * - Se [avoidFirstTrackId] for informado (ex.: última música da sequência anterior ao iniciar
         *   uma nova rodada aleatória) e houver mais de uma música distinta disponível, garante que a
         *   primeira música da nova sequência nunca seja igual a [avoidFirstTrackId].
         */
        fun generateShuffledQueue(
            tracks: List<MusicTrack>,
            lockedFirstTrack: MusicTrack? = null,
            avoidFirstTrackId: String? = null,
            random: Random = Random.Default
        ): List<MusicTrack> {
            if (tracks.size <= 1) return tracks.toList()

            if (lockedFirstTrack != null && tracks.any { it.id == lockedFirstTrack.id }) {
                val remaining = tracks.filterNot { it.id == lockedFirstTrack.id }.shuffled(random)
                return listOf(lockedFirstTrack) + remaining
            }

            val shuffled = tracks.shuffled(random).toMutableList()
            if (!avoidFirstTrackId.isNullOrBlank() && shuffled.first().id == avoidFirstTrackId) {
                val candidateIndices = shuffled.indices.filter { index ->
                    index > 0 && shuffled[index].id != avoidFirstTrackId
                }
                if (candidateIndices.isNotEmpty()) {
                    val swapIndex = candidateIndices.random(random)
                    val firstItem = shuffled[0]
                    shuffled[0] = shuffled[swapIndex]
                    shuffled[swapIndex] = firstItem
                }
            }
            return shuffled
        }
    }
}
