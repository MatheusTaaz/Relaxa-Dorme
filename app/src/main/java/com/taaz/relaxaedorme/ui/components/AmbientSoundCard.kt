package com.taaz.relaxaedorme.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.taaz.relaxaedorme.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.taaz.relaxaedorme.data.model.AmbientSound
import com.taaz.relaxaedorme.ui.theme.AppColors
import com.taaz.relaxaedorme.ui.theme.AppIcon
import com.taaz.relaxaedorme.ui.theme.AppIcons
import com.taaz.relaxaedorme.ui.theme.MusicCategoryPalettes
import com.taaz.relaxaedorme.ui.theme.iconRes

/**
 * Linha minimalista de som ambiente.
 *
 * - Tocar na linha ou no nome ativa ou desativa imediatamente a reprodução do som.
 * - Exibe apenas um pequeno ícone, o nome do som e um indicador visual discreto quando ativo.
 * - O controle individual de volume permanece oculto até que o usuário toque no ícone de volume/ajuste.
 */
@Composable
fun AmbientSoundCard(
    sound: AmbientSound,
    isActiveInMixer: Boolean,
    volume: Float,
    isVolumeExpanded: Boolean,
    onToggleSoundClick: () -> Unit,
    onToggleVolumeControlClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = MusicCategoryPalettes.forSoundCategoryId(sound.categoryId)

    val rowBackgroundColor by animateColorAsState(
        targetValue = if (isActiveInMixer) {
            palette.containerColor.copy(alpha = 0.65f)
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = tween(durationMillis = 260),
        label = "soundRowBackground"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isActiveInMixer) {
            palette.primaryAccent
        } else {
            AppColors.TextMuted
        },
        animationSpec = tween(durationMillis = 260),
        label = "soundRowIconTint"
    )

    val titleColor by animateColorAsState(
        targetValue = if (isActiveInMixer) {
            AppColors.TextPrimary
        } else {
            AppColors.TextSecondary
        },
        animationSpec = tween(durationMillis = 260),
        label = "soundRowTitleColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rowBackgroundColor)
            .clickable(onClick = onToggleSoundClick)
            .padding(horizontal = 18.dp, vertical = 15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pequeno ícone simples e consistente
            AppIcon(
                iconRes = sound.iconRes,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(21.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Nome do som
            Text(
                text = sound.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActiveInMixer) FontWeight.Medium else FontWeight.Normal,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Indicador discreto de reprodução + botão de volume individual quando ativo
            AnimatedVisibility(
                visible = isActiveInMixer,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Indicador visual discreto de som ativo
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(palette.primaryAccent)
                    )

                    // Ícone discreto para exibir/ocultar o controle de volume daquele som
                    IconButton(
                        onClick = onToggleVolumeControlClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        AppIcon(
                            iconRes = if (volume > 0.01f) AppIcons.VolumeUp else AppIcons.VolumeOff,
                            contentDescription = stringResource(R.string.sounds_adjust_volume_cd, sound.name),
                            tint = if (isVolumeExpanded) {
                                palette.primaryAccent
                            } else {
                                AppColors.TextSecondary
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Controle de volume individual (oculto até que o usuário toque no ícone de volume)
        AnimatedVisibility(
            visible = isActiveInMixer && isVolumeExpanded,
            enter = fadeIn(tween(200)) + expandVertically(tween(220)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(180))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 37.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = palette.primaryAccent,
                        inactiveTrackColor = AppColors.NightBackground.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
