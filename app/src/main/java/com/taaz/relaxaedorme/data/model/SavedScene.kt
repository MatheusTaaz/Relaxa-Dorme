package com.taaz.relaxaedorme.data.model

import kotlinx.serialization.Serializable

/**
 * Representa um som ambiente ativo dentro de uma cena salva ([SavedScene]),
 * preservando seu identificador estável, volume individual e configuração de intervalo.
 */
@Serializable
data class SavedSoundSelection(
    val soundId: String,
    val volume: Float = AmbientSound.DEFAULT_SOUND_VOLUME,
    val intervalConfig: SoundIntervalConfig = SoundIntervalConfig()
)

/**
 * Representa uma cena ou combinação personalizada salva pelo usuário no Mixer.
 *
 * Armazena todas as informações necessárias para restaurar o estado do Mixer:
 * - Nome da cena ([name]).
 * - Uma lista de sons ambientes simultâneos ([ambientSounds]), cada qual com seu volume individual
 *   e configuração de frequência de intervalo.
 * - O volume mestre do Mixer ([masterVolume]).
 * - Campos opcionais de música e metadados de criação/atualização.
 */
@Serializable
data class SavedScene(
    val id: String,
    val name: String,
    val description: String? = null,
    val selectedMusicId: String? = null,
    val musicVolume: Float = MusicTrack.DEFAULT_MUSIC_VOLUME,
    val ambientSounds: List<SavedSoundSelection> = emptyList(),
    val masterVolume: Float = DEFAULT_MASTER_VOLUME,
    val isMusicLoopEnabled: Boolean = true,
    val defaultSleepTimerMinutes: Int? = null,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L
) {
    /**
     * Indica se a cena possui pelo menos uma música ou um som ambiente configurado.
     */
    val isNotEmpty: Boolean
        get() = selectedMusicId != null || ambientSounds.isNotEmpty()

    companion object {
        const val DEFAULT_MASTER_VOLUME = 1.0f
    }
}
