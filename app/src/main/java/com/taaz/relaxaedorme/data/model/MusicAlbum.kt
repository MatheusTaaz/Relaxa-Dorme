package com.taaz.relaxaedorme.data.model

import kotlinx.serialization.Serializable

/**
 * Representa um álbum musical publicado no catálogo remoto do Cloudflare R2.
 *
 * O catálogo remoto (`catalog.json`) é a única fonte de verdade para os álbuns exibidos
 * no aplicativo, incluindo [id], [title], [artist], [description], [coverUrl] e a lista
 * ordenada de faixas ([tracks]).
 */
@Serializable
data class MusicAlbum(
    val id: String,
    val title: String,
    val artist: String = DEFAULT_ARTIST,
    val description: String = "",
    val descriptions: Map<String, String> = emptyMap(),
    val coverUrl: String? = null,
    val paletteKey: String = id,
    val tracks: List<MusicTrack> = emptyList()
) {
    /**
     * Retorna a descrição do álbum no idioma solicitado ([languageCode]), fazendo fallback seguro
     * para a descrição original em português caso não haja tradução cadastrada.
     */
    fun localizedDescription(languageCode: String): String {
        val normalized = languageCode.trim().lowercase()
        if (normalized == "pt" || normalized.startsWith("pt-")) {
            return description
        }
        val baseLang = normalized.substringBefore("-")
        val translated = descriptions.entries.firstOrNull { (key, value) ->
            value.isNotBlank() && (
                key.trim().lowercase() == normalized ||
                    key.trim().lowercase().substringBefore("-") == baseLang
                )
        }?.value
        return translated?.takeIf { it.isNotBlank() } ?: description
    }

    companion object {
        const val DEFAULT_ARTIST = "Taaz"
        const val ID_NORDIC_FANTASY = "nordic_fantasy"
        const val ID_ELEMENTAL_DREAMS = "elemental_dreams"
    }
}
