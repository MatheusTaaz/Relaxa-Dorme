package com.taaz.relaxaedorme.ui.screens.sounds

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taaz.relaxaedorme.R
import com.taaz.relaxaedorme.audio.AmbientMixerState
import com.taaz.relaxaedorme.data.model.AmbientSound
import com.taaz.relaxaedorme.ui.components.AmbientSoundCard
import com.taaz.relaxaedorme.ui.components.MinimalScreenHeader
import com.taaz.relaxaedorme.ui.theme.AppColors
import com.taaz.relaxaedorme.ui.theme.AppIcon
import com.taaz.relaxaedorme.ui.theme.AppIcons
import com.taaz.relaxaedorme.ui.viewmodel.MainUiState

/**
 * Tela de Seleção de Sons Ambientes (Aba 3: Sons) — Layout Minimalista (Etapa 5.1).
 *
 * Projetada com foco em tranquilidade, espaço negativo e simplicidade:
 * - Filtros discretos de categoria na parte superior.
 * - Lista vertical minimalista em que cada som é uma linha limpa com ícone, nome e indicador sutil.
 * - Toque direto na linha ativa ou desativa a reprodução em loop.
 * - Controle de volume individual permanece oculto até o usuário tocar no ícone de volume do som ativo.
 */
@Composable
fun SoundsScreen(
    uiState: MainUiState,
    mixerState: AmbientMixerState,
    onBackClick: () -> Unit = {},
    onSelectSoundCategory: (String?) -> Unit,
    onToggleSound: (AmbientSound) -> Unit,
    onSoundVolumeChange: (String, Float) -> Unit,
    onDismissStatusMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleSounds = uiState.filteredAmbientSounds
    var expandedVolumeSoundId by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 1. Cabeçalho Compacto com Voltar para Home
        item {
            MinimalScreenHeader(
                title = stringResource(R.string.sounds_screen_title),
                onBackClick = onBackClick,
                trailingContent = {
                    if (mixerState.activeCount > 0) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.home_mixer_active_sounds,
                                mixerState.activeCount,
                                mixerState.activeCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.AuroraTertiary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            )
        }

        // 2. Filtros Discretos de Categoria no Topo
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    MinimalCategoryPill(
                        label = stringResource(R.string.sounds_all_categories),
                        selected = uiState.selectedSoundCategoryId == null,
                        onClick = { onSelectSoundCategory(null) }
                    )
                }

                items(
                    items = uiState.soundCategories,
                    key = { it.id }
                ) { category ->
                    MinimalCategoryPill(
                        label = category.name,
                        selected = uiState.selectedSoundCategoryId == category.id,
                        onClick = { onSelectSoundCategory(category.id) }
                    )
                }
            }
        }

        // 3. Aviso Discreto Apenas Quando o Limite de 8 Camadas For Atingido
        if (mixerState.statusMessage != null && mixerState.isAtLimit) {
            item {
                MixerStatusBanner(
                    message = mixerState.statusMessage,
                    isWarning = true,
                    onDismiss = onDismissStatusMessage,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(2.dp))
        }

        // 4. Lista Vertical Minimalista de Sons (ou estado vazio amigável quando sem assets locais)
        if (visibleSounds.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppIcon(
                        iconRes = AppIcons.Sounds,
                        contentDescription = null,
                        tint = AppColors.TextMuted,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = stringResource(R.string.sounds_empty_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary
                    )
                }
            }
        } else {
            items(
                items = visibleSounds,
                key = { it.id }
            ) { sound ->
                val activeLayer = mixerState.getLayer(sound.id)
                val isActive = activeLayer != null
                val currentVolume = activeLayer?.volume ?: sound.defaultVolume
                val isVolumeExpanded = isActive && expandedVolumeSoundId == sound.id

                AmbientSoundCard(
                    sound = sound,
                    isActiveInMixer = isActive,
                    volume = currentVolume,
                    isVolumeExpanded = isVolumeExpanded,
                    onToggleSoundClick = {
                        if (isActive && expandedVolumeSoundId == sound.id) {
                            expandedVolumeSoundId = null
                        }
                        onToggleSound(sound)
                    },
                    onToggleVolumeControlClick = {
                        expandedVolumeSoundId = if (expandedVolumeSoundId == sound.id) {
                            null
                        } else {
                            sound.id
                        }
                    },
                    onVolumeChange = { newVolume ->
                        onSoundVolumeChange(sound.id, newVolume)
                    },
                    modifier = Modifier.padding(horizontal = 22.dp)
                )
            }
        }
    }
}

@Composable
private fun MinimalCategoryPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            AppColors.MoonlightPrimaryContainer.copy(alpha = 0.9f)
        } else {
            AppColors.NightSurface.copy(alpha = 0.65f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "pillBg"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) {
            AppColors.MoonlightPrimary
        } else {
            AppColors.TextSecondary
        },
        animationSpec = tween(durationMillis = 220),
        label = "pillText"
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
internal fun MixerStatusBanner(
    message: String,
    isWarning: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bannerColor = if (isWarning) AppColors.HearthSecondary else AppColors.AuroraTertiary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bannerColor.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(30.dp)
            ) {
                AppIcon(
                    iconRes = AppIcons.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
