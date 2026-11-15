package com.taaz.relaxaedorme.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RelaxaDarkColorScheme = darkColorScheme(
    primary = AppColors.MoonlightPrimary,
    onPrimary = AppColors.MoonlightOnPrimary,
    primaryContainer = AppColors.MoonlightPrimaryContainer,
    onPrimaryContainer = AppColors.MoonlightOnPrimaryContainer,
    secondary = AppColors.HearthSecondary,
    onSecondary = AppColors.HearthOnSecondary,
    secondaryContainer = AppColors.HearthSecondaryContainer,
    onSecondaryContainer = AppColors.HearthOnSecondaryContainer,
    tertiary = AppColors.AuroraTertiary,
    onTertiary = AppColors.AuroraOnTertiary,
    tertiaryContainer = AppColors.AuroraTertiaryContainer,
    onTertiaryContainer = AppColors.AuroraOnTertiaryContainer,
    background = AppColors.NightBackground,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.NightSurface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.NightSurfaceVariant,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.NightCardBorder,
    error = AppColors.ErrorSoft
)

/**
 * Permite fornecer uma paleta de categoria ativa para sub-árvores da UI (ex: Player ou Card de Música),
 * mantendo Fantasia Nórdica como padrão inicial.
 */
val LocalCategoryPalette = staticCompositionLocalOf {
    MusicCategoryPalettes.NordicFantasy
}

@Composable
fun RelaxaEDormeTheme(
    activeCategoryPalette: CategoryPalette = MusicCategoryPalettes.NordicFantasy,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(view) {
            val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            // Garante ícones claros na barra de status e navegação sob o fundo escuro noturno
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(LocalCategoryPalette provides activeCategoryPalette) {
        MaterialTheme(
            colorScheme = RelaxaDarkColorScheme,
            typography = Typography,
            content = content
        )
    }
}
