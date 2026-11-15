package com.taaz.relaxaedorme.ui.screens.favorites

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.taaz.relaxaedorme.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.taaz.relaxaedorme.audio.MusicPlaybackState
import com.taaz.relaxaedorme.data.model.MusicTrack
import com.taaz.relaxaedorme.ui.components.MinimalScreenHeader
import com.taaz.relaxaedorme.ui.components.TrackArtworkSurface
import com.taaz.relaxaedorme.ui.theme.AppColors
import com.taaz.relaxaedorme.ui.theme.AppIcon
import com.taaz.relaxaedorme.ui.theme.AppIcons
import com.taaz.relaxaedorme.ui.theme.MusicCategoryPalettes
import com.taaz.relaxaedorme.ui.theme.iconRes
import com.taaz.relaxaedorme.ui.viewmodel.MainUiState
import java.util.Locale

/**
 * Tela de Favoritos minimalista (Etapa 9.3).
 *
 * Reúne exclusivamente as faixas favoritadas pelo usuário no repositório existente,
 * permitindo reprodução direta e remoção rápida dos favoritos.
 */
@Composable
fun FavoritesScreen(
    uiState: MainUiState,
    playbackState: MusicPlaybackState,
    onBackClick: () -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>, String) -> Unit,
    onToggleFavoriteTrack: (String) -> Unit,
    onExploreMusicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val favoriteTracks = remember(uiState.allMusicTracks, uiState.favoriteMusicIds) {
        uiState.allMusicTracks.filter { it.id in uiState.favoriteMusicIds }
    }
    val favoritesQueueTitle = stringResource(R.string.favorites_screen_title)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.NightBackground)
    ) {
        MinimalScreenHeader(
            title = stringResource(R.string.favorites_screen_title),
            onBackClick = onBackClick
        )

        if (favoriteTracks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppIcon(
                    iconRes = AppIcons.FavoriteBorder,
                    contentDescription = null,
                    tint = AppColors.TextMuted,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.favorites_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.favorites_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = AppColors.MoonlightPrimaryContainer,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = onExploreMusicClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.favorites_explore_music),
                            style = MaterialTheme.typography.labelLarge,
                            color = AppColors.MoonlightPrimary
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp)
            ) {
                items(
                    items = favoriteTracks,
                    key = { "fav_${it.id}" }
                ) { track ->
                    val album = uiState.getAlbumForTrack(track)
                    val palette = MusicCategoryPalettes.forAlbumId(track.albumId)
                    val isCurrent = playbackState.currentTrack?.id == track.id
                    val isPlaying = isCurrent && playbackState.isPlaying

                    val rowBackground by animateColorAsState(
                        targetValue = if (isCurrent) {
                            palette.containerColor.copy(alpha = 0.55f)
                        } else {
                            Color.Transparent
                        },
                        animationSpec = tween(durationMillis = 220),
                        label = "favoriteRowBg"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBackground)
                            .clickable { onPlayTrack(track, favoriteTracks, favoritesQueueTitle) }
                            .padding(start = 22.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtworkSurface(
                            palette = palette,
                            iconRes = album?.iconRes ?: AppIcons.Music,
                            coverAssetPath = track.coverUrl,
                            fallbackCoverAssetPath = album?.coverUrl,
                            isPlaying = isPlaying,
                            iconSize = 18.dp,
                            shape = RoundedCornerShape(10.dp),
                            targetSizeDp = 46.dp,
                            modifier = Modifier.size(46.dp)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isCurrent) palette.primaryAccent else AppColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            val meta = buildString {
                                if (album != null) append(album.title)
                                track.durationMs?.takeIf { it > 0L }?.let { duration ->
                                    if (isNotEmpty()) append(" • ")
                                    append(formatDurationCompact(duration))
                                }
                            }
                            if (meta.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = { onToggleFavoriteTrack(track.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            AppIcon(
                                iconRes = AppIcons.Favorite,
                                contentDescription = stringResource(R.string.music_favorite_remove_cd),
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        color = AppColors.NightCardBorder.copy(alpha = 0.25f),
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                }
            }
        }
    }
}

private fun formatDurationCompact(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}
