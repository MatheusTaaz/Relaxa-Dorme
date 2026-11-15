package com.taaz.relaxaedorme.data.model

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Representa uma faixa musical pertencente a um [MusicAlbum] do catálogo do Cloudflare R2.
 *
 * - [remoteAudioPath] define o caminho ou URL do arquivo `.ogg` no Cloudflare R2, sendo
 *   independente de qualquer asset local do APK.
 * - [localAssetPath] é preenchido exclusivamente para as 4 músicas oficiais de demonstração
 *   empacotadas em `app/src/main/assets/demo_music/`, mapeadas explicitamente por [id].
 */
@Serializable
data class MusicTrack(
    val id: String,
    val albumId: String,
    val title: String,
    val artist: String = DEFAULT_ARTIST,
    val description: String? = null,
    val remoteAudioPath: String,
    val localAssetPath: String? = null,
    val fileSizeBytes: Long? = null,
    val coverUrl: String? = null,
    val durationMs: Long? = null,
    val trackOrder: Int = 0,
    val defaultVolume: Float = DEFAULT_MUSIC_VOLUME,
    val isLoopable: Boolean = true
) {
    /**
     * URI formatada para leitura direta do asset local de demonstração em `app/src/main/assets/`
     * pelo Media3 / ExoPlayer, ou `null` quando a faixa não possui asset local embarcado.
     */
    val localAssetUri: String?
        get() = localAssetPath?.takeIf { it.isNotBlank() }?.let { "file:///android_asset/$it" }

    /**
     * Duração formatada no padrão `MM:SS` quando [durationMs] estiver disponível.
     */
    val formattedDuration: String?
        get() = durationMs?.takeIf { it > 0L }?.let { ms ->
            val totalSeconds = ms / 1000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }

    companion object {
        const val DEFAULT_ARTIST = "Taaz"
        const val DEFAULT_MUSIC_VOLUME = 0.8f
    }
}
