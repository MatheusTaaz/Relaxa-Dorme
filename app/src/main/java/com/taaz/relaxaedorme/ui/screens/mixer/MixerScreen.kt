package com.taaz.relaxaedorme.ui.screens.mixer

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.taaz.relaxaedorme.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.taaz.relaxaedorme.audio.ActiveAmbientLayer
import com.taaz.relaxaedorme.audio.AmbientMixerState
import com.taaz.relaxaedorme.data.model.SavedScene
import com.taaz.relaxaedorme.data.model.SoundIntervalConfig
import com.taaz.relaxaedorme.data.model.SoundIntervalMode
import com.taaz.relaxaedorme.ui.components.MinimalScreenHeader
import com.taaz.relaxaedorme.ui.theme.AppColors
import com.taaz.relaxaedorme.ui.theme.AppIcon
import com.taaz.relaxaedorme.ui.theme.AppIcons
import com.taaz.relaxaedorme.ui.theme.MusicCategoryPalettes

/**
 * Tela Mixer (Aba 4: Mixer) — Design Minimalista e Frequência de Intervalo (Etapa 5.1).
 *
 * Prioriza simplicidade visual, espaço negativo e conforto noturno:
 * - Cada som ativo aparece em uma linha compacta contendo apenas indicador discreto de atividade,
 *   nome do som, slider compacto de volume individual e ícone de configurações.
 * - O painel de configurações da camada abre sob demanda na própria lista com controle de volume,
 *   tipo de reprodução e frequência de intervalo (Contínuo, Raro, Moderado, Frequente, Personalizado)
 *   para sons compatíveis.
 */
@Composable
fun MixerScreen(
    mixerState: AmbientMixerState,
    savedScenes: List<SavedScene>,
    onBackClick: () -> Unit = {},
    onMasterVolumeChange: (Float) -> Unit,
    onLayerVolumeChange: (String, Float) -> Unit,
    onLayerIntervalConfigChange: (String, SoundIntervalConfig) -> Unit,
    onToggleLayerPlayPause: (String) -> Unit,
    onRemoveLayer: (String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearAll: () -> Unit,
    onSaveScene: (String) -> Unit,
    onLoadScene: (SavedScene) -> Unit,
    onDeleteScene: (String) -> Unit,
    onDismissStatusMessage: () -> Unit,
    onExploreSoundsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedConfigSoundId by rememberSaveable { mutableStateOf<String?>(null) }
    var isMasterVolumeVisible by rememberSaveable { mutableStateOf(false) }
    var isSaveSceneDialogOpen by rememberSaveable { mutableStateOf(false) }
    var sceneNameInput by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Cabeçalho Compacto + Ações Globais Discretas
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                MinimalScreenHeader(
                    title = stringResource(R.string.mixer_screen_title),
                    onBackClick = onBackClick,
                    trailingContent = {
                        if (mixerState.hasActiveSounds) {
                            val isAnyPlaying = mixerState.anyPlaying
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(AppColors.NightSurface.copy(alpha = 0.85f))
                                    .clickable {
                                        if (isAnyPlaying) onPauseAll() else onResumeAll()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AppIcon(
                                    iconRes = if (isAnyPlaying) AppIcons.Pause else AppIcons.Play,
                                    contentDescription = if (isAnyPlaying) {
                                        stringResource(R.string.mixer_pause_all)
                                    } else {
                                        stringResource(R.string.mixer_resume_all)
                                    },
                                    tint = AppColors.AuroraTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isAnyPlaying) {
                                        stringResource(R.string.action_pause)
                                    } else {
                                        stringResource(R.string.action_resume)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppColors.TextPrimary
                                )
                            }

                            IconButton(
                                onClick = { isMasterVolumeVisible = !isMasterVolumeVisible },
                                modifier = Modifier.size(44.dp)
                            ) {
                                AppIcon(
                                    iconRes = if (mixerState.masterVolume > 0.01f) {
                                        AppIcons.VolumeUp
                                    } else {
                                        AppIcons.VolumeOff
                                    },
                                    contentDescription = stringResource(R.string.mixer_master_volume),
                                    tint = if (isMasterVolumeVisible) {
                                        AppColors.AuroraTertiary
                                    } else {
                                        AppColors.TextSecondary
                                    },
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // Botão discreto para Salvar Cena
                            IconButton(
                                onClick = {
                                    sceneNameInput = ""
                                    isSaveSceneDialogOpen = true
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                AppIcon(
                                    iconRes = AppIcons.Add,
                                    contentDescription = stringResource(R.string.mixer_save_scene),
                                    tint = AppColors.TextSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // Botão discreto para Limpar
                            IconButton(
                                onClick = {
                                    expandedConfigSoundId = null
                                    onClearAll()
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                AppIcon(
                                    iconRes = AppIcons.Close,
                                    contentDescription = stringResource(R.string.mixer_clear_all),
                                    tint = AppColors.TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )

                // Controle de Volume Mestre expansível sob demanda
                AnimatedVisibility(
                    visible = isMasterVolumeVisible && mixerState.hasActiveSounds,
                    enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(160)) + shrinkVertically(tween(180))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.NightSurface.copy(alpha = 0.65f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.mixer_master_volume),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.TextSecondary
                        )
                        Slider(
                            value = mixerState.masterVolume,
                            onValueChange = onMasterVolumeChange,
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = AppColors.AuroraTertiary,
                                inactiveTrackColor = AppColors.NightBackground.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${mixerState.masterVolumePercentage}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.TextMuted
                        )
                    }
                }
            }
        }

        // 2. Pílulas Discretas de Cenas Salvas (quando existirem)
        if (savedScenes.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = savedScenes,
                        key = { it.id }
                    ) { scene ->
                        MinimalSavedScenePill(
                            scene = scene,
                            onLoadClick = {
                                onDismissStatusMessage()
                                onLoadScene(scene)
                            },
                            onDeleteClick = { onDeleteScene(scene.id) }
                        )
                    }
                }
            }
        }

        // 3. Lista Vertical Compacta dos Sons Ativos ou Estado Vazio Minimalista
        if (!mixerState.hasActiveSounds) {
            item {
                Spacer(modifier = Modifier.height(36.dp))
                MinimalMixerEmptyState(
                    onExploreSoundsClick = onExploreSoundsClick,
                    modifier = Modifier.padding(horizontal = 22.dp)
                )
            }
        } else {
            items(
                items = mixerState.activeLayers,
                key = { it.sound.id }
            ) { layer ->
                val isConfigOpen = expandedConfigSoundId == layer.sound.id

                MinimalMixerLayerRow(
                    layer = layer,
                    isConfigOpen = isConfigOpen,
                    onTogglePlayPause = { onToggleLayerPlayPause(layer.sound.id) },
                    onVolumeChange = { newVolume ->
                        onLayerVolumeChange(layer.sound.id, newVolume)
                    },
                    onToggleConfig = {
                        expandedConfigSoundId = if (isConfigOpen) null else layer.sound.id
                    },
                    onIntervalConfigChange = { newConfig ->
                        onLayerIntervalConfigChange(layer.sound.id, newConfig)
                    },
                    onRemove = {
                        if (expandedConfigSoundId == layer.sound.id) {
                            expandedConfigSoundId = null
                        }
                        onRemoveLayer(layer.sound.id)
                    },
                    modifier = Modifier.padding(horizontal = 22.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "+ Adicionar som",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColors.TextMuted,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onExploreSoundsClick)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (isSaveSceneDialogOpen) {
        AlertDialog(
            onDismissRequest = { isSaveSceneDialogOpen = false },
            containerColor = AppColors.NightSurface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = {
                Text(
                    text = stringResource(R.string.mixer_save_combination),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
            },
            text = {
                OutlinedTextField(
                    value = sceneNameInput,
                    onValueChange = { sceneNameInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.mixer_scene_name_label)) },
                    placeholder = { Text(stringResource(R.string.mixer_scene_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                val defaultSceneName = stringResource(R.string.mixer_scene_default_name, mixerState.activeCount)
                TextButton(
                    onClick = {
                        val finalName = sceneNameInput.trim().ifBlank { defaultSceneName }
                        onSaveScene(finalName)
                        isSaveSceneDialogOpen = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_save),
                        color = AppColors.AuroraTertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { isSaveSceneDialogOpen = false }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = AppColors.TextSecondary
                    )
                }
            }
        )
    }
}

/**
 * Linha compacta e minimalista de um som ativo no Mixer.
 *
 * Exibe na linha principal apenas:
 * 1. Indicador discreto de atividade (ponto luminoso) + Nome do som.
 * 2. Slider compacto de volume individual.
 * 3. Ícone de configurações para abrir o painel minimalista de opções.
 */
@Composable
private fun MinimalMixerLayerRow(
    layer: ActiveAmbientLayer,
    isConfigOpen: Boolean,
    onTogglePlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleConfig: () -> Unit,
    onIntervalConfigChange: (SoundIntervalConfig) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = MusicCategoryPalettes.forSoundCategoryId(layer.sound.categoryId)

    val rowBackground by animateColorAsState(
        targetValue = if (isConfigOpen) {
            AppColors.NightSurfaceVariant.copy(alpha = 0.85f)
        } else {
            AppColors.NightSurface.copy(alpha = 0.65f)
        },
        animationSpec = tween(durationMillis = 240),
        label = "mixerRowBg"
    )

    val indicatorColor by animateColorAsState(
        targetValue = if (layer.isPlaying) {
            palette.primaryAccent
        } else {
            AppColors.TextMuted.copy(alpha = 0.45f)
        },
        animationSpec = tween(durationMillis = 240),
        label = "mixerIndicator"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rowBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Linha principal compacta: Indicador discreto + Nome + Slider compacto + Ícone de Configurações
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador discreto de som ativo + Nome (tocar alterna ativo/pausado)
            Row(
                modifier = Modifier
                    .weight(0.44f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onTogglePlayPause)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = layer.sound.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (layer.isPlaying) FontWeight.Medium else FontWeight.Normal,
                    color = if (layer.isPlaying) AppColors.TextPrimary else AppColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Slider compacto de volume individual
            Slider(
                value = layer.volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = if (layer.isPlaying) {
                        palette.primaryAccent
                    } else {
                        AppColors.TextMuted
                    },
                    inactiveTrackColor = AppColors.NightBackground.copy(alpha = 0.55f)
                ),
                modifier = Modifier.weight(0.46f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Ícone de configurações para acessar opções adicionais
            IconButton(
                onClick = onToggleConfig,
                modifier = Modifier.size(30.dp)
            ) {
                AppIcon(
                    iconRes = AppIcons.Mixer,
                    contentDescription = stringResource(R.string.mixer_layer_settings_cd, layer.sound.name),
                    tint = if (isConfigOpen) palette.primaryAccent else AppColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Pequeno painel minimalista de configurações (oculto até o usuário tocar no ícone)
        AnimatedVisibility(
            visible = isConfigOpen,
            enter = fadeIn(tween(200)) + expandVertically(tween(220)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(180))
        ) {
            MinimalSoundSettingsPanel(
                layer = layer,
                accentColor = palette.primaryAccent,
                onVolumeChange = onVolumeChange,
                onIntervalConfigChange = onIntervalConfigChange,
                onTogglePlayPause = onTogglePlayPause,
                onRemove = onRemove
            )
        }
    }
}

/**
 * Painel minimalista embutido exibido ao tocar no ícone de configurações de um som:
 * - Controle de volume.
 * - Tipo de reprodução.
 * - Frequência de intervalo (quando compatível: Contínuo, Raro, Moderado, Frequente, Personalizado).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MinimalSoundSettingsPanel(
    layer: ActiveAmbientLayer,
    accentColor: Color,
    onVolumeChange: (Float) -> Unit,
    onIntervalConfigChange: (SoundIntervalConfig) -> Unit,
    onTogglePlayPause: () -> Unit,
    onRemove: () -> Unit
) {
    val currentConfig = layer.intervalConfig

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Controle de Volume com porcentagem discreta
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.sounds_volume_label),
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextMuted,
                modifier = Modifier.width(64.dp)
            )
            Slider(
                value = layer.volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = AppColors.NightBackground.copy(alpha = 0.55f)
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${layer.volumePercentage}%",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextSecondary,
                modifier = Modifier.width(38.dp),
                textAlign = TextAlign.End
            )
        }

        // 2. Tipo de Reprodução e Frequência de Intervalo (quando compatível)
        if (!layer.sound.supportsIntervalFrequency) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.mixer_playback_type),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.TextMuted
                )
                Text(
                    text = stringResource(R.string.mixer_continuous_ambient),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.TextSecondary
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.mixer_interval_frequency),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextMuted
                    )
                    Text(
                        text = if (currentConfig.mode == SoundIntervalMode.CONTINUOUS) {
                            stringResource(R.string.mixer_continuous_playback)
                        } else {
                            stringResource(R.string.mixer_spaced_effect)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val orderedModes = listOf(
                        SoundIntervalMode.CONTINUOUS,
                        SoundIntervalMode.RARE,
                        SoundIntervalMode.MODERATE,
                        SoundIntervalMode.FREQUENT,
                        SoundIntervalMode.CUSTOM
                    )

                    orderedModes.forEach { mode ->
                        val selected = currentConfig.mode == mode
                        IntervalModeOptionPill(
                            label = mode.label,
                            selected = selected,
                            accentColor = accentColor,
                            onClick = {
                                onIntervalConfigChange(currentConfig.copy(mode = mode))
                            }
                        )
                    }
                }

                // Modo Personalizado: intervalo mínimo e máximo em minutos
                AnimatedVisibility(
                    visible = currentConfig.mode == SoundIntervalMode.CUSTOM,
                    enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(160))
                ) {
                    CustomIntervalRangeSelector(
                        minMinutes = currentConfig.sanitizedMinMinutes,
                        maxMinutes = currentConfig.sanitizedMaxMinutes,
                        accentColor = accentColor,
                        onRangeChange = { newMin, newMax ->
                            onIntervalConfigChange(
                                currentConfig.copy(
                                    mode = SoundIntervalMode.CUSTOM,
                                    customMinMinutes = newMin,
                                    customMaxMinutes = newMax
                                )
                            )
                        }
                    )
                }
            }
        }

        // 3. Ações discretas da camada (Pausar/Retomar e Remover)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (layer.isPlaying) {
                stringResource(R.string.mixer_pause_sound)
            } else {
                stringResource(R.string.mixer_resume_sound)
            },
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onTogglePlayPause)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Text(
                text = stringResource(R.string.mixer_remove_from_mixer),
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun IntervalModeOptionPill(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            accentColor.copy(alpha = 0.22f)
        } else {
            AppColors.NightBackground.copy(alpha = 0.45f)
        },
        animationSpec = tween(200),
        label = "intervalPillBg"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) {
            accentColor
        } else {
            AppColors.TextSecondary
        },
        animationSpec = tween(200),
        label = "intervalPillText"
    )

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor
        )
    }
}

/**
 * Seletor minimalista de intervalo mínimo e máximo em minutos para o modo Personalizado.
 */
@Composable
private fun CustomIntervalRangeSelector(
    minMinutes: Int,
    maxMinutes: Int,
    accentColor: Color,
    onRangeChange: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.NightBackground.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MinuteStepperRow(
            label = stringResource(R.string.mixer_min_interval),
            minutes = minMinutes,
            accentColor = accentColor,
            onDecrement = {
                val updatedMin = (minMinutes - 1).coerceAtLeast(SoundIntervalConfig.MIN_ALLOWED_MINUTES)
                onRangeChange(updatedMin, maxMinutes.coerceAtLeast(updatedMin))
            },
            onIncrement = {
                val updatedMin = (minMinutes + 1).coerceAtMost(SoundIntervalConfig.MAX_ALLOWED_MINUTES)
                val updatedMax = maxMinutes.coerceAtLeast(updatedMin)
                onRangeChange(updatedMin, updatedMax)
            }
        )

        MinuteStepperRow(
            label = stringResource(R.string.mixer_max_interval),
            minutes = maxMinutes,
            accentColor = accentColor,
            onDecrement = {
                val updatedMax = (maxMinutes - 1).coerceAtLeast(minMinutes)
                onRangeChange(minMinutes, updatedMax)
            },
            onIncrement = {
                val updatedMax = (maxMinutes + 1).coerceAtMost(SoundIntervalConfig.MAX_ALLOWED_MINUTES)
                onRangeChange(minMinutes, updatedMax)
            }
        )
    }
}

@Composable
private fun MinuteStepperRow(
    label: String,
    minutes: Int,
    accentColor: Color,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.TextSecondary
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(AppColors.NightSurface)
                    .clickable(onClick = onDecrement),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "−",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary
                )
            }

            Text(
                text = "$minutes min",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = accentColor,
                modifier = Modifier.width(46.dp),
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(AppColors.NightSurface)
                    .clickable(onClick = onIncrement),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun MinimalSavedScenePill(
    scene: SavedScene,
    onLoadClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(AppColors.NightSurface.copy(alpha = 0.75f))
            .clickable(onClick = onLoadClick)
            .padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = scene.name,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .clickable(onClick = onDeleteClick),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                iconRes = AppIcons.Close,
                contentDescription = stringResource(R.string.mixer_scene_delete_cd, scene.name),
                tint = AppColors.TextMuted,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun MinimalMixerEmptyState(
    onExploreSoundsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(
            iconRes = AppIcons.Sounds,
            contentDescription = null,
            tint = AppColors.TextMuted,
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.mixer_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.mixer_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(AppColors.NightSurface)
                .clickable(onClick = onExploreSoundsClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.mixer_open_sounds),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary
            )
        }
    }
}
