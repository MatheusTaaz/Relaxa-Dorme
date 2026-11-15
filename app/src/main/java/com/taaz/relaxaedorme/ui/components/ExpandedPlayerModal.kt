package com.taaz.relaxaedorme.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.taaz.relaxaedorme.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taaz.relaxaedorme.audio.MusicPlaybackState
import com.taaz.relaxaedorme.audio.MusicProgressState
import com.taaz.relaxaedorme.audio.MusicRepeatMode
import com.taaz.relaxaedorme.audio.SleepTimerState
import com.taaz.relaxaedorme.ui.theme.AppColors
import com.taaz.relaxaedorme.ui.theme.AppIcon
import com.taaz.relaxaedorme.ui.theme.AppIcons
import com.taaz.relaxaedorme.ui.theme.CategoryPalette
import com.taaz.relaxaedorme.ui.theme.MusicCategoryPalettes
import com.taaz.relaxaedorme.ui.theme.iconRes
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

/**
 * Painel expandido de detalhes e reprodução da música atual.
 *
 * A seção de progresso ([ExpandedPlayerProgressSection]) e o botão de temporizador
 * ([ExpandedPlayerSleepTimerButton]) coletam seus fluxos temporais de forma isolada
 * para que o restante do modal não recomponha a cada tick.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedPlayerModal(
    playbackState: MusicPlaybackState,
    progressStateFlow: StateFlow<MusicProgressState>,
    sleepTimerStateFlow: StateFlow<SleepTimerState>,
    onDismiss: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onDismissError: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavoriteClick: () -> Unit = {}
) {
    val track = playbackState.currentTrack ?: return
    val album = playbackState.currentAlbum
    val palette = MusicCategoryPalettes.forAlbumId(track.albumId)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.NightBackground,
        contentColor = AppColors.TextPrimary,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            palette.gradientStart.copy(alpha = 0.65f),
                            AppColors.NightBackground,
                            AppColors.NightBackground
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cabeçalho do Player Expandido
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        AppIcon(
                            iconRes = AppIcons.Close,
                            contentDescription = stringResource(R.string.player_close_cd),
                            tint = AppColors.TextSecondary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.player_playing_now),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.TextMuted
                        )
                        Text(
                            text = playbackState.queueTitle.ifBlank {
                                album?.title ?: stringResource(R.string.home_dest_music)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = palette.primaryAccent
                        )
                    }

                    IconButton(
                        onClick = onToggleFavoriteClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(
                            iconRes = if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                            contentDescription = if (isFavorite) {
                                stringResource(R.string.music_favorite_remove_cd)
                            } else {
                                stringResource(R.string.music_favorite_add_cd)
                            },
                            tint = if (isFavorite) palette.primaryAccent else AppColors.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Área de Capa do Álbum/Música
                TrackArtworkSurface(
                    palette = palette,
                    iconRes = album?.iconRes ?: AppIcons.Music,
                    coverAssetPath = track.coverUrl,
                    fallbackCoverAssetPath = album?.coverUrl,
                    preserveOriginalAspectRatio = true,
                    isPlaying = playbackState.isPlaying,
                    iconSize = 64.dp,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .fillMaxWidth(0.84f)
                        .aspectRatio(1f)
                )

                Spacer(modifier = Modifier.height(26.dp))

                // Badge do Álbum
                if (album != null) {
                    Surface(
                        color = palette.primaryAccent.copy(alpha = 0.16f),
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AppIcon(
                                iconRes = album.iconRes,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = palette.primaryAccent
                            )
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.primaryAccent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Título e Artista
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextSecondary
                )

                // Alerta de erro caso ocorra problema de reprodução
                playbackState.errorMessage?.let { errorMsg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = AppColors.ErrorSoft.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onDismissError)
                    ) {
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.ErrorSoft,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Barra de Progresso Isolada
                ExpandedPlayerProgressSection(
                    trackId = track.id,
                    effectiveDurationMs = playbackState.effectiveDurationMs,
                    progressStateFlow = progressStateFlow,
                    palette = palette,
                    onSeekFraction = onSeekFraction
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Controles Principais de Reprodução (Shuffle, Anterior, Play/Pause, Próxima, Repeat)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botão Shuffle (Reprodução Aleatória)
                    PlayerModeToggleButton(
                        iconRes = AppIcons.Shuffle,
                        contentDescription = if (playbackState.isShuffleEnabled) {
                            stringResource(R.string.player_shuffle_on)
                        } else {
                            stringResource(R.string.player_sequential)
                        },
                        isActive = playbackState.isShuffleEnabled,
                        accentColor = palette.primaryAccent,
                        onClick = onToggleShuffle
                    )

                    // Botão Música Anterior
                    Surface(
                        shape = CircleShape,
                        color = AppColors.NightSurfaceVariant,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onPreviousClick)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AppIcon(
                                iconRes = AppIcons.SkipPrevious,
                                contentDescription = stringResource(R.string.player_previous_cd),
                                tint = AppColors.TextPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Botão Play / Pause Principal
                    Surface(
                        shape = CircleShape,
                        color = palette.primaryAccent,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onPlayPauseClick)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AppIcon(
                                iconRes = if (playbackState.isPlaying) AppIcons.Pause else AppIcons.Play,
                                contentDescription = if (playbackState.isPlaying) {
                                    stringResource(R.string.action_pause)
                                } else {
                                    stringResource(R.string.action_play)
                                },
                                tint = AppColors.NightBackground,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Botão Próxima Música
                    Surface(
                        shape = CircleShape,
                        color = AppColors.NightSurfaceVariant,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onNextClick)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AppIcon(
                                iconRes = AppIcons.SkipNext,
                                contentDescription = stringResource(R.string.player_next_cd),
                                tint = AppColors.TextPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Botão Repeat (Desativado -> Repetir Música Atual -> Repetir Lista)
                    val isRepeatActive = playbackState.repeatMode != MusicRepeatMode.OFF
                    PlayerModeToggleButton(
                        iconRes = if (playbackState.repeatMode == MusicRepeatMode.REPEAT_ONE) {
                            AppIcons.RepeatOne
                        } else {
                            AppIcons.Repeat
                        },
                        contentDescription = playbackState.repeatMode.label,
                        isActive = isRepeatActive,
                        accentColor = palette.primaryAccent,
                        onClick = onCycleRepeatMode
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Botão de Temporizador de Sono (Zz) com indicação discreta da duração e tempo restante
                ExpandedPlayerSleepTimerButton(
                    sleepTimerStateFlow = sleepTimerStateFlow,
                    palette = palette,
                    onCycleSleepTimer = onCycleSleepTimer
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Controle de Volume da Música
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(
                            iconRes = if (playbackState.volume <= 0.01f) {
                                AppIcons.VolumeOff
                            } else {
                                AppIcons.VolumeUp
                            },
                            contentDescription = stringResource(R.string.player_mute_cd),
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Slider(
                        value = playbackState.volume,
                        onValueChange = onVolumeChange,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = palette.primaryAccent,
                            activeTrackColor = palette.primaryAccent,
                            inactiveTrackColor = AppColors.NightSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "${(playbackState.volume * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.width(38.dp),
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PlayerModeToggleButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isActive) accentColor.copy(alpha = 0.14f) else Color.Transparent,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIcon(
                iconRes = iconRes,
                contentDescription = contentDescription,
                tint = if (isActive) accentColor else AppColors.TextMuted,
                modifier = Modifier.size(22.dp)
            )
            if (isActive) {
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
        }
    }
}

@Composable
private fun ExpandedPlayerSleepTimerButton(
    sleepTimerStateFlow: StateFlow<SleepTimerState>,
    palette: CategoryPalette,
    onCycleSleepTimer: () -> Unit
) {
    val timerState by sleepTimerStateFlow.collectAsStateWithLifecycle()
    val isActive = timerState.isActive

    Surface(
        shape = CircleShape,
        color = if (isActive) {
            palette.primaryAccent.copy(alpha = 0.14f)
        } else {
            AppColors.NightSurfaceVariant.copy(alpha = 0.45f)
        },
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onCycleSleepTimer)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isActive) 14.dp else 12.dp,
                vertical = 7.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            AppIcon(
                iconRes = AppIcons.SleepZz,
                contentDescription = timerState.preset.label,
                tint = if (isActive) palette.primaryAccent else AppColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )

            if (isActive) {
                Text(
                    text = timerState.preset.shortBadge,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primaryAccent
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.primaryAccent.copy(alpha = 0.7f)
                )
                Text(
                    text = timerState.formattedRemainingTime,
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ExpandedPlayerProgressSection(
    trackId: String,
    effectiveDurationMs: Long,
    progressStateFlow: StateFlow<MusicProgressState>,
    palette: CategoryPalette,
    onSeekFraction: (Float) -> Unit
) {
    val progressState by progressStateFlow.collectAsStateWithLifecycle()

    var isUserDraggingSeek by remember(trackId) { mutableStateOf(false) }
    var dragSeekFraction by remember(trackId) { mutableFloatStateOf(0f) }

    val displayedFraction = if (isUserDraggingSeek) {
        dragSeekFraction
    } else {
        progressState.progressFraction
    }

    val totalDurationMs = if (progressState.durationMs > 0L) {
        progressState.durationMs
    } else {
        effectiveDurationMs
    }

    val displayedPositionText = if (isUserDraggingSeek) {
        MusicPlaybackState.formatMillis((dragSeekFraction * totalDurationMs).toLong())
    } else {
        progressState.formattedPosition
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = displayedFraction,
            onValueChange = { fraction ->
                isUserDraggingSeek = true
                dragSeekFraction = fraction
            },
            onValueChangeFinished = {
                onSeekFraction(dragSeekFraction)
                isUserDraggingSeek = false
            },
            colors = SliderDefaults.colors(
                thumbColor = palette.primaryAccent,
                activeTrackColor = palette.primaryAccent,
                inactiveTrackColor = AppColors.NightSurfaceVariant
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = displayedPositionText,
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextSecondary
            )
            Text(
                text = MusicPlaybackState.formatMillis(totalDurationMs),
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextSecondary
            )
        }
    }
}
