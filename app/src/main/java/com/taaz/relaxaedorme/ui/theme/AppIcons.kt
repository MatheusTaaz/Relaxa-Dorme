package com.taaz.relaxaedorme.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.taaz.relaxaedorme.R
import com.taaz.relaxaedorme.data.model.AmbientSound
import com.taaz.relaxaedorme.data.model.MusicAlbum
import com.taaz.relaxaedorme.data.model.SoundCategory

/**
 * Centralizador de ícones oficiais Google Material Symbols (estilo Rounded) em Vector Drawable XML.
 * Evita referências diretas a R.drawable espalhadas pelo projeto e mantém a camada de dados
 * desacoplada de recursos visuais do Android.
 */
object AppIcons {
    // Abas de Navegação Principais (4 Abas)
    @DrawableRes val Home = R.drawable.ic_home_rounded
    @DrawableRes val Music = R.drawable.ic_music_note_rounded
    @DrawableRes val Sounds = R.drawable.ic_graphic_eq_rounded
    @DrawableRes val Mixer = R.drawable.ic_tune_rounded

    // Controles do Player, Mixer, Downloads e Temporizador
    @DrawableRes val Play = R.drawable.ic_play_arrow_rounded
    @DrawableRes val Pause = R.drawable.ic_pause_rounded
    @DrawableRes val SkipNext = R.drawable.ic_skip_next_rounded
    @DrawableRes val SkipPrevious = R.drawable.ic_skip_previous_rounded
    @DrawableRes val Repeat = R.drawable.ic_repeat_rounded
    @DrawableRes val RepeatOne = R.drawable.ic_repeat_one_rounded
    @DrawableRes val Shuffle = R.drawable.ic_shuffle_rounded
    @DrawableRes val SleepZz = R.drawable.ic_zzz_rounded
    @DrawableRes val Timer = R.drawable.ic_timer_rounded
    @DrawableRes val VolumeUp = R.drawable.ic_volume_up_rounded
    @DrawableRes val VolumeOff = R.drawable.ic_volume_off_rounded
    @DrawableRes val Close = R.drawable.ic_close_rounded
    @DrawableRes val Add = R.drawable.ic_add_rounded
    @DrawableRes val Delete = R.drawable.ic_delete_rounded
    @DrawableRes val Bedtime = R.drawable.ic_bedtime_rounded
    @DrawableRes val CloudDownload = R.drawable.ic_cloud_download_rounded
    @DrawableRes val DownloadDone = R.drawable.ic_download_done_rounded
    @DrawableRes val Info = R.drawable.ic_info_rounded
    @DrawableRes val ArrowBack = R.drawable.ic_arrow_back_rounded
    @DrawableRes val Favorite = R.drawable.ic_favorite_rounded
    @DrawableRes val FavoriteBorder = R.drawable.ic_favorite_border_rounded
    @DrawableRes val ChevronRight = R.drawable.ic_chevron_right_rounded

    // Álbuns Musicais
    @DrawableRes val NordicFantasy = R.drawable.ic_landscape_rounded
    @DrawableRes val ElementalDreams = R.drawable.ic_air_rounded

    // Categorias de Sons Ambientes
    @DrawableRes val RainAndStorms = R.drawable.ic_rainy_rounded
    @DrawableRes val OceanAndRivers = R.drawable.ic_water_rounded
    @DrawableRes val ForestAndAnimals = R.drawable.ic_forest_rounded
    @DrawableRes val WindAndNature = R.drawable.ic_air_rounded
    @DrawableRes val FireplaceAndCampfire = R.drawable.ic_local_fire_department_rounded
    @DrawableRes val AmbientNoises = R.drawable.ic_blur_on_rounded
    @DrawableRes val CozyAmbiences = R.drawable.ic_cottage_rounded

    /**
     * Resolve o ícone correspondente ao ID estável de um [MusicAlbum].
     */
    @DrawableRes
    fun forMusicAlbum(albumId: String?): Int = when (albumId) {
        MusicAlbum.ID_NORDIC_FANTASY -> NordicFantasy
        MusicAlbum.ID_ELEMENTAL_DREAMS -> ElementalDreams
        else -> Music
    }

    /**
     * Resolve o ícone correspondente ao ID estável de uma [SoundCategory].
     */
    @DrawableRes
    fun forSoundCategory(categoryId: String?): Int = when (categoryId) {
        SoundCategory.ID_RAIN, "rain_and_storms" -> RainAndStorms
        SoundCategory.ID_WATER, "water_and_oceans" -> OceanAndRivers
        SoundCategory.ID_FOREST, "forests_and_animals" -> ForestAndAnimals
        SoundCategory.ID_FIRE, "campfires_and_fireplaces" -> FireplaceAndCampfire
        SoundCategory.ID_NOISE, "white_pink_brown_noises" -> AmbientNoises
        "wind_and_nature" -> WindAndNature
        "special_ambiences" -> CozyAmbiences
        else -> Sounds
    }
}

/**
 * Extensões de conveniência na camada de UI para obter o `@DrawableRes` de álbuns e sons.
 */
@get:DrawableRes
val MusicAlbum.iconRes: Int
    get() = AppIcons.forMusicAlbum(id)

@get:DrawableRes
val SoundCategory.iconRes: Int
    get() = AppIcons.forSoundCategory(id)

@get:DrawableRes
val AmbientSound.iconRes: Int
    get() = AppIcons.forSoundCategory(categoryId)

/**
 * Componente utilitário padronizado para renderizar ícones do [AppIcons]
 * usando [painterResource] e [Icon] do Jetpack Compose.
 */
@Composable
fun AppIcon(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
