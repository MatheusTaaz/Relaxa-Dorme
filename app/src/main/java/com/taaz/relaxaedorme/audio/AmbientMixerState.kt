package com.taaz.relaxaedorme.audio

import com.taaz.relaxaedorme.data.model.AmbientSound
import com.taaz.relaxaedorme.data.model.SavedScene
import com.taaz.relaxaedorme.data.model.SavedSoundSelection
import com.taaz.relaxaedorme.data.model.SoundIntervalConfig
import com.taaz.relaxaedorme.data.model.SoundIntervalMode
import kotlin.math.roundToInt

/**
 * Representa uma camada individual de som ambiente ativa no Mixer.
 *
 * Cada camada é independente, possui seu próprio volume individual ([volume] de `0f` a `1f`)
 * e, quando compatível ([AmbientSound.supportsIntervalFrequency]), sua própria configuração
 * de frequência de intervalo ([intervalConfig]).
 */
data class ActiveAmbientLayer(
    val sound: AmbientSound,
    val categoryName: String = "",
    val volume: Float = sound.defaultVolume.coerceIn(0f, 1f),
    val isPlaying: Boolean = true,
    val isBuffering: Boolean = false,
    val isInfiniteLoopEnabled: Boolean = true,
    val intervalConfig: SoundIntervalConfig = SoundIntervalConfig(),
    val isWaitingForNextInterval: Boolean = false,
    val errorMessage: String? = null
) {
    /**
     * Percentual inteiro (`0..100`) do volume individual da camada para exibição na UI.
     */
    val volumePercentage: Int
        get() = (volume.coerceIn(0f, 1f) * 100f).roundToInt()

    /**
     * Indica se a camada está utilizando intervalos espaçados em vez de loop contínuo.
     */
    val isIntervalModeActive: Boolean
        get() = sound.supportsIntervalFrequency && intervalConfig.mode != SoundIntervalMode.CONTINUOUS

    /**
     * Calcula o volume efetivo enviado ao player desta camada considerando o [masterVolume].
     */
    fun effectiveVolume(masterVolume: Float): Float =
        AmbientMixerState.calculateEffectiveVolume(
            layerVolume = volume,
            masterVolume = masterVolume
        )
}

/**
 * Estado imutável do Mixer de Sons Ambientes.
 *
 * Totalmente independente de [MusicPlaybackState]: alterações nas camadas ambientes
 * ou no volume mestre do Mixer nunca interferem na reprodução ou no volume da música.
 */
data class AmbientMixerState(
    val activeLayers: List<ActiveAmbientLayer> = emptyList(),
    val masterVolume: Float = DEFAULT_MASTER_VOLUME,
    val maxLayers: Int = MAX_ACTIVE_SOUNDS,
    val statusMessage: String? = null
) {
    val activeCount: Int
        get() = activeLayers.size

    val hasActiveSounds: Boolean
        get() = activeLayers.isNotEmpty()

    val isAtLimit: Boolean
        get() = activeLayers.size >= maxLayers

    val anyPlaying: Boolean
        get() = activeLayers.any { it.isPlaying }

    val anyPaused: Boolean
        get() = activeLayers.any { !it.isPlaying }

    val masterVolumePercentage: Int
        get() = (masterVolume.coerceIn(0f, 1f) * 100f).roundToInt()

    fun isSoundActive(soundId: String): Boolean =
        activeLayers.any { it.sound.id == soundId }

    fun isSoundPlaying(soundId: String): Boolean =
        activeLayers.firstOrNull { it.sound.id == soundId }?.isPlaying == true

    fun getLayer(soundId: String): ActiveAmbientLayer? =
        activeLayers.firstOrNull { it.sound.id == soundId }

    /**
     * Constrói uma [SavedScene] a partir do estado atual do Mixer, preservando volumes e intervalos.
     */
    fun toSavedScene(
        id: String,
        name: String,
        timestampMillis: Long = System.currentTimeMillis()
    ): SavedScene {
        val trimmedName = name.trim().ifBlank { "Cena Personalizada" }
        return SavedScene(
            id = id,
            name = trimmedName,
            ambientSounds = activeLayers.map { layer ->
                SavedSoundSelection(
                    soundId = layer.sound.id,
                    volume = layer.volume.coerceIn(0f, 1f),
                    intervalConfig = layer.intervalConfig
                )
            },
            masterVolume = masterVolume.coerceIn(0f, 1f),
            createdAtEpochMillis = timestampMillis,
            updatedAtEpochMillis = timestampMillis
        )
    }

    companion object {
        const val MAX_ACTIVE_SOUNDS = 8
        const val DEFAULT_MASTER_VOLUME = 1.0f

        /**
         * Calcula o volume efetivo real (`0f` a `1f`) aplicado ao player de áudio de uma camada,
         * preservando a proporção entre o volume individual ([layerVolume]) e o volume mestre ([masterVolume]).
         */
        fun calculateEffectiveVolume(layerVolume: Float, masterVolume: Float): Float {
            val clampedLayer = layerVolume.coerceIn(0f, 1f)
            val clampedMaster = masterVolume.coerceIn(0f, 1f)
            return (clampedLayer * clampedMaster).coerceIn(0f, 1f)
        }
    }
}
