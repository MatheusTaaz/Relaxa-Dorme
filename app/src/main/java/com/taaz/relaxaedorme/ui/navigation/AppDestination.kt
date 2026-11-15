package com.taaz.relaxaedorme.ui.navigation

import androidx.annotation.DrawableRes
import com.taaz.relaxaedorme.ui.theme.AppIcons

/**
 * As quatro abas principais de navegação inferior do aplicativo Relaxa e Dorme.
 */
enum class AppDestination(
    val route: String,
    val label: String,
    @param:DrawableRes val iconRes: Int
) {
    HOME(
        route = "home",
        label = "Início",
        iconRes = AppIcons.Home
    ),
    MUSIC(
        route = "music",
        label = "Músicas",
        iconRes = AppIcons.Music
    ),
    SOUNDS(
        route = "sounds",
        label = "Sons",
        iconRes = AppIcons.Sounds
    ),
    MIXER(
        route = "mixer",
        label = "Mixer",
        iconRes = AppIcons.Mixer
    )
}
