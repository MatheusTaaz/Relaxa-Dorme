package com.taaz.relaxaedorme.audio

import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Opções de duração do temporizador de sono (botão Zz).
 *
 * Cada toque avança ciclicamente na sequência:
 * Desativado -> 15 min -> 30 min -> 45 min -> 1h -> 1h 30m -> 2h -> Desativado.
 */
enum class SleepTimerPreset(
    val durationMinutes: Int,
    val shortBadge: String,
    val label: String
) {
    OFF(
        durationMinutes = 0,
        shortBadge = "",
        label = "Temporizador desativado"
    ),
    MIN_15(
        durationMinutes = 15,
        shortBadge = "15m",
        label = "15 minutos"
    ),
    MIN_30(
        durationMinutes = 30,
        shortBadge = "30m",
        label = "30 minutos"
    ),
    MIN_45(
        durationMinutes = 45,
        shortBadge = "45m",
        label = "45 minutos"
    ),
    HOUR_1(
        durationMinutes = 60,
        shortBadge = "1h",
        label = "1 hora"
    ),
    HOUR_1_30(
        durationMinutes = 90,
        shortBadge = "1h30",
        label = "1 hora e 30 minutos"
    ),
    HOUR_2(
        durationMinutes = 120,
        shortBadge = "2h",
        label = "2 horas"
    );

    val durationMillis: Long
        get() = durationMinutes * 60_000L

    fun nextPreset(): SleepTimerPreset = when (this) {
        OFF -> MIN_15
        MIN_15 -> MIN_30
        MIN_30 -> MIN_45
        MIN_45 -> HOUR_1
        HOUR_1 -> HOUR_1_30
        HOUR_1_30 -> HOUR_2
        HOUR_2 -> OFF
    }
}

/**
 * Estado imutável do temporizador de sono e do processo de fade-out.
 */
data class SleepTimerState(
    val preset: SleepTimerPreset = SleepTimerPreset.OFF,
    val remainingMillis: Long = 0L,
    val isFadingOut: Boolean = false,
    val fadeMultiplier: Float = 1.0f
) {
    val isActive: Boolean
        get() = preset != SleepTimerPreset.OFF && (remainingMillis > 0L || isFadingOut)

    val formattedRemainingTime: String
        get() = formatCountdownMillis(remainingMillis)

    companion object {
        const val DEFAULT_FADE_OUT_DURATION_MS = 30_000L

        fun formatCountdownMillis(millis: Long): String {
            val safeMs = millis.coerceAtLeast(0L)
            val totalSeconds = (safeMs + 999L) / 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
            }
        }
    }
}

/**
 * Controlador centralizado do Temporizador de Sono (Zz) e do Fade-Out de 30 segundos.
 *
 * Opera no escopo de aplicação ([AppAudioSessionCoordinator]) para continuar funcionando
 * normalmente quando o aplicativo é minimizado, com a tela bloqueada ou em segundo plano.
 * Nunca cria temporizadores duplicados e preserva os volumes originais da Música e do Mixer
 * através de um multiplicador temporário de saída ([onFadeMultiplierChanged]).
 */
class SleepTimerController(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    private val ownsScope: Boolean = true,
    private val tickIntervalMs: Long = 1_000L,
    private val fadeOutDurationMs: Long = SleepTimerState.DEFAULT_FADE_OUT_DURATION_MS,
    private val fadeOutSteps: Int = 60,
    private val restoreSteps: Int = 10,
    private val restoreStepDelayMs: Long = 35L,
    private val presetDurationProvider: (SleepTimerPreset) -> Long = { it.durationMillis },
    private val onFadeMultiplierChanged: (Float) -> Unit = {},
    private val onTimerFinished: () -> Unit = {}
) {
    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    private var timerJob: Job? = null
    private var restoreVolumeJob: Job? = null

    /**
     * Avança para a próxima duração do temporizador na sequência oficial.
     * Caso o temporizador já esteja executando o fade-out final, tocar no botão Zz
     * cancela imediatamente o encerramento e restaura suavemente o volume anterior.
     */
    fun cyclePreset() {
        val current = _sleepTimerState.value
        if (current.isFadingOut) {
            cancel(restoreVolumeSmoothly = true)
            return
        }
        setPreset(current.preset.nextPreset())
    }

    /**
     * Define uma opção específica do temporizador, substituindo qualquer contagem anterior
     * sem nunca permitir temporizadores duplicados.
     */
    fun setPreset(preset: SleepTimerPreset) {
        timerJob?.cancel()
        timerJob = null
        restoreVolumeJob?.cancel()
        restoreVolumeJob = null

        val previousMultiplier = _sleepTimerState.value.fadeMultiplier

        if (preset == SleepTimerPreset.OFF) {
            if (previousMultiplier < 0.99f) {
                startSmoothVolumeRestore(fromMultiplier = previousMultiplier)
            } else {
                onFadeMultiplierChanged(1.0f)
                _sleepTimerState.value = SleepTimerState()
            }
            return
        }

        if (previousMultiplier < 0.99f) {
            onFadeMultiplierChanged(1.0f)
        }

        val durationMs = presetDurationProvider(preset).coerceAtLeast(0L)
        _sleepTimerState.value = SleepTimerState(
            preset = preset,
            remainingMillis = durationMs,
            isFadingOut = false,
            fadeMultiplier = 1.0f
        )

        timerJob = scope.launch {
            var remaining = durationMs
            val stepMs = tickIntervalMs.coerceAtLeast(1L)

            while (isActive && remaining > 0L) {
                val waitMs = remaining.coerceAtMost(stepMs)
                delay(waitMs)
                if (!isActive) return@launch
                remaining = (remaining - waitMs).coerceAtLeast(0L)
                _sleepTimerState.value = _sleepTimerState.value.copy(
                    remainingMillis = remaining,
                    isFadingOut = false,
                    fadeMultiplier = 1.0f
                )
            }

            if (!isActive) return@launch

            // Inicia o fade-out gradual (padrão: 30 segundos) até zero
            val totalFadeMs = fadeOutDurationMs.coerceAtLeast(0L)
            val steps = fadeOutSteps.coerceAtLeast(1)
            val fadeStepDelayMs = (totalFadeMs / steps).coerceAtLeast(1L)

            _sleepTimerState.value = _sleepTimerState.value.copy(
                remainingMillis = totalFadeMs,
                isFadingOut = true,
                fadeMultiplier = 1.0f
            )

            for (step in 1..steps) {
                delay(fadeStepDelayMs)
                if (!isActive) return@launch

                val progress = step.toFloat() / steps.toFloat()
                val multiplier = (1.0f - progress).coerceIn(0f, 1f)
                val fadeRemaining = ((steps - step) * fadeStepDelayMs).coerceAtLeast(0L)

                onFadeMultiplierChanged(multiplier)
                _sleepTimerState.value = _sleepTimerState.value.copy(
                    remainingMillis = fadeRemaining,
                    isFadingOut = true,
                    fadeMultiplier = multiplier
                )
            }

            if (!isActive) return@launch

            // Encerra a reprodução dos áudios ativos e restaura o multiplicador interno para 1.0f
            // (com os players já pausados) para preservar intactos os volumes originais do usuário.
            onTimerFinished()
            onFadeMultiplierChanged(1.0f)
            _sleepTimerState.value = SleepTimerState()
        }
    }

    /**
     * Cancela o temporizador ativo. Se estiver durante o fade-out, interrompe o encerramento
     * e restaura suavemente o volume anterior sem alterar os volumes originais configurados.
     */
    fun cancel(restoreVolumeSmoothly: Boolean = true) {
        timerJob?.cancel()
        timerJob = null
        restoreVolumeJob?.cancel()
        restoreVolumeJob = null

        val currentMultiplier = _sleepTimerState.value.fadeMultiplier
        if (currentMultiplier < 0.99f && restoreVolumeSmoothly) {
            startSmoothVolumeRestore(fromMultiplier = currentMultiplier)
        } else {
            onFadeMultiplierChanged(1.0f)
            _sleepTimerState.value = SleepTimerState()
        }
    }

    private fun startSmoothVolumeRestore(fromMultiplier: Float) {
        val clampedStart = fromMultiplier.coerceIn(0f, 1f)
        _sleepTimerState.value = SleepTimerState(
            preset = SleepTimerPreset.OFF,
            remainingMillis = 0L,
            isFadingOut = false,
            fadeMultiplier = clampedStart
        )

        val steps = restoreSteps.coerceAtLeast(1)
        val stepDelay = restoreStepDelayMs.coerceAtLeast(1L)

        restoreVolumeJob = scope.launch {
            val delta = (1.0f - clampedStart) / steps.toFloat()
            for (step in 1..steps) {
                delay(stepDelay)
                if (!isActive) return@launch
                val nextMultiplier = if (step == steps) {
                    1.0f
                } else {
                    (clampedStart + delta * step).coerceIn(0f, 1f)
                }
                onFadeMultiplierChanged(nextMultiplier)
                _sleepTimerState.value = _sleepTimerState.value.copy(
                    fadeMultiplier = nextMultiplier
                )
            }
            onFadeMultiplierChanged(1.0f)
            _sleepTimerState.value = SleepTimerState()
        }
    }

    internal val hasActiveTimerJobForTesting: Boolean
        get() = timerJob?.isActive == true

    fun release() {
        timerJob?.cancel()
        timerJob = null
        restoreVolumeJob?.cancel()
        restoreVolumeJob = null
        if (ownsScope) {
            scope.cancel()
        }
    }
}
