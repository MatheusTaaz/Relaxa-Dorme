package com.taaz.relaxaedorme.data.model

import java.util.Locale
import kotlin.random.Random
import kotlinx.serialization.Serializable

/**
 * Modos de frequência de intervalo para efeitos sonoros pontuais no Mixer.
 */
@Serializable
enum class SoundIntervalMode(val label: String) {
    CONTINUOUS("Contínuo"),
    FREQUENT("Frequente"),
    MODERATE("Moderado"),
    RARE("Raro"),
    CUSTOM("Personalizado")
}

/**
 * Configuração de intervalo individual de uma camada sonora no Mixer.
 *
 * - [CONTINUOUS]: reprodução contínua sem pausas entre repetições.
 * - [FREQUENT]: intervalo aleatório curto (15s a 45s).
 * - [MODERATE]: intervalo aleatório intermediário (1 a 3 minutos).
 * - [RARE]: intervalo aleatório longo (3 a 7 minutos).
 * - [CUSTOM]: intervalo aleatório definido pelo usuário em minutos ([customMinMinutes] a [customMaxMinutes]).
 */
@Serializable
data class SoundIntervalConfig(
    val mode: SoundIntervalMode = SoundIntervalMode.CONTINUOUS,
    val customMinMinutes: Int = DEFAULT_CUSTOM_MIN_MINUTES,
    val customMaxMinutes: Int = DEFAULT_CUSTOM_MAX_MINUTES
) {
    val sanitizedMinMinutes: Int
        get() = customMinMinutes.coerceIn(MIN_ALLOWED_MINUTES, MAX_ALLOWED_MINUTES)

    val sanitizedMaxMinutes: Int
        get() = customMaxMinutes.coerceIn(sanitizedMinMinutes, MAX_ALLOWED_MINUTES)

    /**
     * Retorna o intervalo `[minMillis, maxMillis]` correspondente ao modo configurado.
     */
    fun delayRangeMillis(): LongRange = when (mode) {
        SoundIntervalMode.CONTINUOUS -> 0L..0L
        SoundIntervalMode.FREQUENT -> 15_000L..45_000L
        SoundIntervalMode.MODERATE -> 60_000L..180_000L
        SoundIntervalMode.RARE -> 180_000L..420_000L
        SoundIntervalMode.CUSTOM -> {
            val minMs = sanitizedMinMinutes * 60_000L
            val maxMs = sanitizedMaxMinutes * 60_000L
            minMs..maxMs
        }
    }

    /**
     * Sorteia aleatoriamente o próximo intervalo (em milissegundos) dentro da faixa configurada,
     * evitando que efeitos sonoros pareçam mecânicos ou repetitivos.
     */
    fun nextRandomDelayMillis(random: Random = Random.Default): Long {
        val range = delayRangeMillis()
        if (range.last <= 0L) return 0L
        if (range.first >= range.last) return range.first
        return random.nextLong(range.first, range.last + 1L)
    }

    companion object {
        const val DEFAULT_CUSTOM_MIN_MINUTES = 1
        const val DEFAULT_CUSTOM_MAX_MINUTES = 3
        const val MIN_ALLOWED_MINUTES = 1
        const val MAX_ALLOWED_MINUTES = 30
    }
}

/**
 * Representa um som da biblioteca de Sons Ambientes.
 *
 * - Sons contínuos de base (como chuva, água e ruídos constantes) possuem [supportsIntervalFrequency] = `false`.
 * - Sons pontuais ou atmosféricos (como trovões, vento e animais) possuem [supportsIntervalFrequency] = `true`,
 *   permitindo configurar intervalos aleatórios entre reproduções no Mixer.
 */
@Serializable
data class AmbientSound(
    val id: String,
    val name: String,
    val description: String? = null,
    val categoryId: String,
    val audioAssetPath: String,
    val durationMs: Long? = null,
    val imageAssetPath: String? = null,
    val defaultVolume: Float = DEFAULT_SOUND_VOLUME,
    val isLoopable: Boolean = true,
    val supportsIntervalFrequency: Boolean = false
) {
    /**
     * URI formatada para leitura direta de arquivos empacotados em `app/src/main/assets/`
     * pelo motor de áudio.
     */
    val assetUri: String
        get() = "file:///android_asset/$audioAssetPath"

    /**
     * Duração formatada no padrão `MM:SS` quando [durationMs] está disponível.
     */
    val formattedDuration: String?
        get() = durationMs?.let { ms ->
            val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }

    companion object {
        const val DEFAULT_SOUND_VOLUME = 0.55f
    }
}
