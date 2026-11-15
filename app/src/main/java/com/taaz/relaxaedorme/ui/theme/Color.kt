package com.taaz.relaxaedorme.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.taaz.relaxaedorme.data.model.MusicAlbum
import com.taaz.relaxaedorme.data.model.SoundCategory

/**
 * Paleta de cores noturna, aconchegante e imersiva do Relaxa e Dorme.
 * Projetada com baixa emissão de luz agressiva para uso confortável antes de dormir.
 */
object AppColors {
    // Fundos e Superfícies Noturnas (Silêncio Noturno: contraste tonal suave sem bordas pesadas)
    val NightBackground = Color(0xFF090C15)
    val NightSurface = Color(0xFF111623)
    val NightSurfaceVariant = Color(0xFF1A2133)
    val NightElevatedSurface = Color(0xFF1E263A)
    val NightCardBorder = Color(0xFF263047)

    // Destaques da Home com a Ghosty (Lavanda, azul suave e luar creme)
    val GhostyHaloOuter = Color(0xFF2B3B59)
    val GhostyHaloInner = Color(0xFF5A7399)
    val GhostyMoonCream = Color(0xFFE5D2A6)
    val GhostyContinuePill = Color(0xFF8EB0D6)
    val GhostyContinueOnPill = Color(0xFF09101D)
    val GhostyLavenderSoft = Color(0xFFBFC8F8)

    // Cores Principais (Luar suave + Âmbar de lareira + Lavanda serena)
    val MoonlightPrimary = Color(0xFFA3B8FF)
    val MoonlightOnPrimary = Color(0xFF0E1B3D)
    val MoonlightPrimaryContainer = Color(0xFF223366)
    val MoonlightOnPrimaryContainer = Color(0xFFDCE4FF)

    val HearthSecondary = Color(0xFFFFB77C)
    val HearthOnSecondary = Color(0xFF3E2004)
    val HearthSecondaryContainer = Color(0xFF59310B)
    val HearthOnSecondaryContainer = Color(0xFFFFDCC2)

    val AuroraTertiary = Color(0xFF8CE0D2)
    val AuroraOnTertiary = Color(0xFF063831)
    val AuroraTertiaryContainer = Color(0xFF145249)
    val AuroraOnTertiaryContainer = Color(0xFFB4F7EA)

    // Textos e Ícones (luminância confortável para leitura noturna)
    val TextPrimary = Color(0xFFE4EAF8)
    val TextSecondary = Color(0xFF9BA8C4)
    val TextMuted = Color(0xFF626F8C)

    // Estados e Feedback
    val ErrorSoft = Color(0xFFFF8A80)
    val ActiveSoundGlow = Color(0xFF6E8CFB)
}

/**
 * Paleta temática para variação visual de cada álbum musical ou categoria de som ambiente.
 */
@Immutable
data class CategoryPalette(
    val primaryAccent: Color,
    val containerColor: Color,
    val gradientStart: Color,
    val gradientEnd: Color
)

/**
 * Paletas centralizadas para os álbuns musicais e categorias de Sons Ambientes.
 */
object MusicCategoryPalettes {
    // Nordic Fantasy (Aurora boreal, gelo e montanhas nevadas)
    val NordicFantasy = CategoryPalette(
        primaryAccent = Color(0xFF8CD5FF),
        containerColor = Color(0xFF162A3D),
        gradientStart = Color(0xFF1B3A4B),
        gradientEnd = Color(0xFF0F1C2E)
    )

    // Elemental Dreams (Brisa elemental, água cristalina e turquesa sereno)
    val ElementalDreams = CategoryPalette(
        primaryAccent = Color(0xFF6EE7C8),
        containerColor = Color(0xFF13332E),
        gradientStart = Color(0xFF1B4942),
        gradientEnd = Color(0xFF0E2220)
    )

    // Paletas de apoio para categorias de Sons Ambientes
    val RainAtmosphere = CategoryPalette(
        primaryAccent = Color(0xFF9AB0FF),
        containerColor = Color(0xFF1B2145),
        gradientStart = Color(0xFF242D5E),
        gradientEnd = Color(0xFF0F1329)
    )

    val WaterAtmosphere = CategoryPalette(
        primaryAccent = Color(0xFF68B8FF),
        containerColor = Color(0xFF102845),
        gradientStart = Color(0xFF15375E),
        gradientEnd = Color(0xFF09182B)
    )

    val FireAtmosphere = CategoryPalette(
        primaryAccent = Color(0xFFFFC285),
        containerColor = Color(0xFF362417),
        gradientStart = Color(0xFF4A2F1D),
        gradientEnd = Color(0xFF211610)
    )

    /**
     * Retorna a paleta correspondente ao identificador do álbum musical.
     */
    fun forAlbumId(albumId: String?): CategoryPalette = when (albumId) {
        MusicAlbum.ID_NORDIC_FANTASY -> NordicFantasy
        MusicAlbum.ID_ELEMENTAL_DREAMS -> ElementalDreams
        else -> NordicFantasy
    }

    /**
     * Retorna uma paleta sutil e acolhedora para cada categoria de Som Ambiente.
     */
    fun forSoundCategoryId(categoryId: String?): CategoryPalette = when (categoryId) {
        SoundCategory.ID_RAIN -> RainAtmosphere
        SoundCategory.ID_WATER -> WaterAtmosphere
        SoundCategory.ID_FOREST -> ElementalDreams
        SoundCategory.ID_FIRE -> FireAtmosphere
        SoundCategory.ID_NOISE -> NordicFantasy
        else -> ElementalDreams
    }
}
