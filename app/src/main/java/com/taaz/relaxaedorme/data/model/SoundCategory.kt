package com.taaz.relaxaedorme.data.model

import kotlinx.serialization.Serializable

/**
 * Representa uma categoria da biblioteca de Sons Ambientes.
 *
 * Agrupa sons contínuos por tema (chuva, água, floresta, fogo e ruído ambiente)
 * com identificadores estáveis para navegação, filtragem e expansão futura.
 */
@Serializable
data class SoundCategory(
    val id: String,
    val name: String,
    val description: String,
    val assetDirectory: String,
    val imageAssetPath: String? = null
) {
    companion object {
        const val ID_RAIN = "rain"
        const val ID_WATER = "water"
        const val ID_FOREST = "forest"
        const val ID_FIRE = "fire"
        const val ID_NOISE = "noise"
    }
}
