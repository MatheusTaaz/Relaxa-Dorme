package com.taaz.relaxaedorme.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taaz.relaxaedorme.R
import com.taaz.relaxaedorme.audio.MusicPlaybackState
import com.taaz.relaxaedorme.audio.MusicProgressState
import com.taaz.relaxaedorme.ui.theme.AppColors
import com.taaz.relaxaedorme.ui.theme.AppIcon
import com.taaz.relaxaedorme.ui.theme.AppIcons
import com.taaz.relaxaedorme.ui.theme.MusicCategoryPalettes
import com.taaz.relaxaedorme.ui.theme.iconRes
import kotlinx.coroutines.flow.StateFlow

/**
 * Mini-player persistente exibido acima da barra de navegação inferior
 * sempre que houver uma música selecionada.
 *
 * Coleta [progressStateFlow] internamente para isolar as recomposições de progresso (250 ms)
 * apenas à legenda de tempo e ao indicador de progresso, sem recompor a tela principal.
 */
@Composable
fun MiniPlayerBar(
    playbackState: MusicPlaybackState,
    progressStateFlow: StateFlow<MusicProgressState>,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    val album = playbackState.currentAlbum
    val palette = MusicCategoryPalettes.forAlbumId(track.albumId)
    val progressState by progressStateFlow.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onExpandClick),
        shape = RoundedCornerShape(18.dp),
        color = AppColors.NightElevatedSurface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtworkSurface(
                    palette = palette,
                    iconRes = album?.iconRes ?: AppIcons.Music,
                    coverAssetPath = track.coverUrl,
                    fallbackCoverAssetPath = album?.coverUrl,
                    isPlaying = playbackState.isPlaying,
                    iconSize = 18.dp,
                    shape = RoundedCornerShape(12.dp),
                    targetSizeDp = 44.dp,
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppIcon(
                            iconRes = if (playbackState.isPlaying) AppIcons.Sounds else AppIcons.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = palette.primaryAccent
                        )
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = buildString {
                            append(track.artist)
                            if (album != null) {
                                append(" • ")
                                append(album.title)
                            }
                            append(" • ")
                            append(progressState.formattedPosition)
                            append(" / ")
                            append(progressState.formattedDuration)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = palette.primaryAccent,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPlayPauseClick)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AppIcon(
                            iconRes = if (playbackState.isPlaying) AppIcons.Pause else AppIcons.Play,
                            contentDescription = if (playbackState.isPlaying) {
                            stringResource(R.string.action_pause)
                        } else {
                            stringResource(R.string.action_play)
                        },
                            tint = AppColors.NightBackground,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    AppIcon(
                        iconRes = AppIcons.SkipNext,
                        contentDescription = stringResource(R.string.player_next_cd),
                        tint = AppColors.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progressState.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = palette.primaryAccent,
                trackColor = AppColors.NightSurfaceVariant
            )
        }
    }
}
