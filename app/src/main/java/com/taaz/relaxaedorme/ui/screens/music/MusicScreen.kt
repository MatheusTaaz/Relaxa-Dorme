package com.taaz.relaxaedorme.ui.screens.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taaz.relaxaedorme.R
import com.taaz.relaxaedorme.audio.MusicPlaybackState
import com.taaz.relaxaedorme.data.model.MusicAlbum
import com.taaz.relaxaedorme.data.model.MusicTrack
import com.taaz.relaxaedorme.data.offline.TrackDownloadState
import com.taaz.relaxaedorme.data.offline.TrackDownloadStatus
import com.taaz.relaxaedorme.ui.components.MinimalScreenHeader
import com.taaz.relaxaedorme.ui.components.TrackArtworkSurface
import com.taaz.relaxaedorme.ui.theme.AppColors
import com.taaz.relaxaedorme.ui.theme.AppIcon
import com.taaz.relaxaedorme.ui.theme.AppIcons
import com.taaz.relaxaedorme.ui.theme.CategoryPalette
import com.taaz.relaxaedorme.ui.theme.MusicCategoryPalettes
import com.taaz.relaxaedorme.ui.theme.iconRes
import com.taaz.relaxaedorme.ui.viewmodel.MainUiState
import com.taaz.relaxaedorme.ui.viewmodel.MusicLibraryTab
import java.util.Locale

/**
 * Tela de Músicas (Aba 2: Músicas) — Biblioteca por Álbuns e Gerenciamento de Downloads Offline.
 *
 * - Na visão principal (`selectedAlbum == null`), oferece navegação secundária entre:
 *   1. `Álbuns`: grade de álbuns publicados no catálogo remoto (Cloudflare R2) com capa preservada,
 *      título, artista e descrição de até 3 linhas.
 *   2. `Downloads`: lista consolidada de todas as músicas efetivamente baixadas no armazenamento
 *      interno do dispositivo, com capa individual, álbum, tamanho real em disco, status offline e
 *      exclusão individual segura.
 * - Ao tocar em um álbum (`selectedAlbum != null`), abre sua página de detalhes com capa integral
 *   sem cortes, descrição completa, resumo de downloads do álbum (contagem, progresso, baixar e
 *   remover downloads) e lista de faixas com capas individuais carregadas do Cloudflare R2.
 */
@Composable
fun MusicScreen(
    uiState: MainUiState,
    playbackState: MusicPlaybackState,
    onSelectAlbum: (String?) -> Unit,
    onSelectMusicTab: (MusicLibraryTab) -> Unit = {},
    onPlayTrack: (MusicTrack, List<MusicTrack>, String) -> Unit,
    onPlayAllShuffled: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onToggleFavoriteTrack: (String) -> Unit = {},
    onRefreshCatalog: () -> Unit = {},
    onRefreshDownloads: () -> Unit = {},
    onDownloadTrack: (MusicTrack) -> Unit = {},
    onCancelTrackDownload: (MusicTrack) -> Unit = {},
    onDeleteTrackDownload: (MusicTrack) -> Unit = {},
    onDownloadAlbum: (String) -> Unit = {},
    onCancelAlbumDownload: (String) -> Unit = {},
    onDeleteAlbumDownloads: (String) -> Unit = {},
    languageCode: String = "pt-BR",
    modifier: Modifier = Modifier
) {
    val selectedAlbum = uiState.selectedMusicAlbum
    val downloadsQueueLabel = stringResource(R.string.music_tab_downloads_plain)
    var trackPendingDeletion by remember { mutableStateOf<MusicTrack?>(null) }
    var albumPendingDeletion by remember { mutableStateOf<MusicAlbum?>(null) }

    BackHandler(enabled = selectedAlbum != null) {
        onSelectAlbum(null)
    }

    if (selectedAlbum == null) {
        MusicLibraryMainView(
            uiState = uiState,
            playbackState = playbackState,
            languageCode = languageCode,
            onBackClick = onBackClick,
            onSelectTab = onSelectMusicTab,
            onAlbumClick = { album -> onSelectAlbum(album.id) },
            onPlayAllShuffled = onPlayAllShuffled,
            onRefreshCatalog = onRefreshCatalog,
            onRefreshDownloads = onRefreshDownloads,
            onPlayDownloadedTrack = { track ->
                onPlayTrack(track, uiState.downloadedTracks, downloadsQueueLabel)
            },
            onRequestDeleteTrackDownload = { track ->
                trackPendingDeletion = track
            },
            modifier = modifier
        )
    } else {
        val albumTracks = uiState.selectedAlbumTracks
        val isAlbumDownloading = uiState.isAlbumDownloading(selectedAlbum.id)
        val canDownloadAlbum = uiState.canDownloadAlbumFromRemote(selectedAlbum.id)
        val isAlbumOfflineReady = uiState.isAlbumFullyOffline(selectedAlbum.id)
        val downloadedCount = uiState.getAlbumDownloadedCount(selectedAlbum.id)
        val totalCount = uiState.getAlbumTotalTracksCount(selectedAlbum.id)
        val partialCount = uiState.getAlbumPartialCount(selectedAlbum.id)
        val overallProgress = uiState.getAlbumOverallProgress(selectedAlbum.id)

        MusicAlbumInterior(
            album = selectedAlbum,
            tracks = albumTracks,
            playbackState = playbackState,
            favoriteMusicIds = uiState.favoriteMusicIds,
            trackDownloadStates = uiState.trackDownloadStates,
            downloadedCount = downloadedCount,
            totalCount = totalCount,
            partialCount = partialCount,
            overallProgress = overallProgress,
            isAlbumDownloading = isAlbumDownloading,
            canDownloadAlbum = canDownloadAlbum,
            isAlbumOfflineReady = isAlbumOfflineReady,
            languageCode = languageCode,
            onBackClick = { onSelectAlbum(null) },
            onTrackClick = { track ->
                onPlayTrack(track, albumTracks, selectedAlbum.title)
            },
            onToggleFavoriteTrack = onToggleFavoriteTrack,
            onDownloadTrackClick = onDownloadTrack,
            onCancelTrackDownloadClick = onCancelTrackDownload,
            onRequestDeleteTrackDownload = { track ->
                trackPendingDeletion = track
            },
            onDownloadAlbumClick = { onDownloadAlbum(selectedAlbum.id) },
            onCancelAlbumDownloadClick = { onCancelAlbumDownload(selectedAlbum.id) },
            onRequestDeleteAlbumDownloads = {
                albumPendingDeletion = selectedAlbum
            },
            modifier = modifier
        )
    }

    trackPendingDeletion?.let { track ->
        AlertDialog(
            onDismissRequest = { trackPendingDeletion = null },
            containerColor = AppColors.NightElevatedSurface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = {
                Text(
                    text = stringResource(R.string.music_delete_track_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.music_delete_track_body_streaming, track.title),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTrackDownload(track)
                        trackPendingDeletion = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_remove),
                        color = AppColors.ErrorSoft,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { trackPendingDeletion = null }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = AppColors.TextSecondary
                    )
                }
            }
        )
    }

    albumPendingDeletion?.let { album ->
        val count = uiState.getAlbumDownloadedCount(album.id)
        AlertDialog(
            onDismissRequest = { albumPendingDeletion = null },
            containerColor = AppColors.NightElevatedSurface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = {
                Text(
                    text = stringResource(R.string.music_delete_album_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = pluralStringResource(
                        R.plurals.music_delete_album_body_plurals,
                        count,
                        count,
                        album.title
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAlbumDownloads(album.id)
                        albumPendingDeletion = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.music_delete_album_confirm),
                        color = AppColors.ErrorSoft,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { albumPendingDeletion = null }) {
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
 * Visão principal da aba Músicas com navegação secundária entre `Álbuns` e `Downloads`.
 */
@Composable
private fun MusicLibraryMainView(
    uiState: MainUiState,
    playbackState: MusicPlaybackState,
    languageCode: String,
    onBackClick: () -> Unit,
    onSelectTab: (MusicLibraryTab) -> Unit,
    onAlbumClick: (MusicAlbum) -> Unit,
    onPlayAllShuffled: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onRefreshDownloads: () -> Unit,
    onPlayDownloadedTrack: (MusicTrack) -> Unit,
    onRequestDeleteTrackDownload: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadedTracks = uiState.downloadedTracks
    val albumPairs = uiState.musicAlbums.chunked(2)
    var shuffleFeedbackMessage by remember { mutableStateOf<String?>(null) }
    val emptyCatalogShuffleMsg = stringResource(R.string.music_empty_catalog_shuffle)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                MinimalScreenHeader(
                    title = stringResource(R.string.music_screen_title),
                    onBackClick = onBackClick,
                    trailingContent = {
                        TextButton(
                            onClick = onRefreshCatalog,
                            enabled = !uiState.isLoadingCatalog
                        ) {
                            if (uiState.isLoadingCatalog) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = AppColors.TextSecondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = stringResource(
                                    if (uiState.isLoadingCatalog) R.string.action_refreshing else R.string.action_refresh
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                MusicSecondaryTabRow(
                    selectedTab = uiState.selectedMusicTab,
                    downloadedCount = downloadedTracks.size,
                    onSelectTab = { tab ->
                        shuffleFeedbackMessage = null
                        onSelectTab(tab)
                    },
                    modifier = Modifier.padding(horizontal = 22.dp)
                )

                if (uiState.selectedMusicTab == MusicLibraryTab.ALBUMS) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        onClick = {
                            if (uiState.allMusicTracks.isNotEmpty()) {
                                shuffleFeedbackMessage = null
                                onPlayAllShuffled()
                            } else {
                                shuffleFeedbackMessage = emptyCatalogShuffleMsg
                            }
                        },
                        color = AppColors.NightSurfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = AppColors.NightCardBorder.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            AppIcon(
                                iconRes = AppIcons.Shuffle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.music_play_shuffled),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = AppColors.TextPrimary
                            )
                        }
                    }

                    shuffleFeedbackMessage?.let { feedback ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = feedback,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 22.dp)
                        )
                    }
                }
            }
        }

        when (uiState.selectedMusicTab) {
            MusicLibraryTab.ALBUMS -> {
                if (uiState.musicAlbums.isEmpty()) {
                    item {
                        EmptyCatalogStateCard(
                            isLoading = uiState.isLoadingCatalog,
                            statusMessage = uiState.catalogStatusMessage,
                            onRefreshClick = onRefreshCatalog,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)
                        )
                    }
                } else {
                    items(
                        items = albumPairs,
                        key = { pair -> pair.joinToString("_") { it.id } }
                    ) { rowAlbums ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            rowAlbums.forEach { album ->
                                val isAlbumPlaying = playbackState.currentTrack?.albumId == album.id &&
                                    playbackState.isPlaying
                                val downloadedInAlbum = uiState.getAlbumDownloadedCount(album.id)
                                val totalInAlbum = uiState.getAlbumTotalTracksCount(album.id)

                                MinimalMusicAlbumItem(
                                    album = album,
                                    languageCode = languageCode,
                                    isPlaying = isAlbumPlaying,
                                    downloadedCount = downloadedInAlbum,
                                    totalCount = totalInAlbum,
                                    onClick = { onAlbumClick(album) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowAlbums.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            MusicLibraryTab.DOWNLOADS -> {
                when {
                    uiState.isLoadingDownloads -> {
                        item {
                            DownloadsStatusCard(
                                isLoading = true,
                                title = stringResource(R.string.music_downloads_checking),
                                subtitle = null,
                                actionLabel = null,
                                onActionClick = {},
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)
                            )
                        }
                    }

                    uiState.downloadsErrorMessage != null -> {
                        item {
                            DownloadsStatusCard(
                                isLoading = false,
                                title = uiState.downloadsErrorMessage,
                                subtitle = stringResource(R.string.music_downloads_error_subtitle),
                                actionLabel = stringResource(R.string.action_retry),
                                onActionClick = onRefreshDownloads,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)
                            )
                        }
                    }

                    downloadedTracks.isEmpty() -> {
                        item {
                            DownloadsStatusCard(
                                isLoading = false,
                                title = stringResource(R.string.music_downloads_empty_title),
                                subtitle = stringResource(R.string.music_downloads_empty_subtitle),
                                actionLabel = stringResource(R.string.music_downloads_explore_albums),
                                onActionClick = { onSelectTab(MusicLibraryTab.ALBUMS) },
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)
                            )
                        }
                    }

                    else -> {
                        val totalDownloadedBytes = downloadedTracks.sumOf { track ->
                            uiState.getTrackDownloadState(track).downloadedBytes.coerceAtLeast(0L)
                        }

                        item {
                            DownloadsSummaryHeader(
                                count = downloadedTracks.size,
                                totalBytes = totalDownloadedBytes,
                                modifier = Modifier.padding(horizontal = 22.dp)
                            )
                        }

                        items(
                            items = downloadedTracks,
                            key = { "downloaded_${it.id}" }
                        ) { track ->
                            val album = uiState.getAlbumForTrack(track)
                            val palette = MusicCategoryPalettes.forAlbumId(track.albumId)
                            val downloadState = uiState.getTrackDownloadState(track)
                            val isCurrentTrack = playbackState.currentTrack?.id == track.id
                            val isPlaying = isCurrentTrack && playbackState.isPlaying

                            DownloadedMusicTrackItem(
                                track = track,
                                album = album,
                                palette = palette,
                                downloadState = downloadState,
                                isCurrentTrack = isCurrentTrack,
                                isPlaying = isPlaying,
                                onClick = { onPlayDownloadedTrack(track) },
                                onDeleteClick = { onRequestDeleteTrackDownload(track) },
                                modifier = Modifier.padding(horizontal = 22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicSecondaryTabRow(
    selectedTab: MusicLibraryTab,
    downloadedCount: Int,
    onSelectTab: (MusicLibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MusicSecondaryTabPill(
            label = stringResource(R.string.music_tab_albums),
            badgeCount = null,
            selected = selectedTab == MusicLibraryTab.ALBUMS,
            onClick = { onSelectTab(MusicLibraryTab.ALBUMS) }
        )

        MusicSecondaryTabPill(
            label = stringResource(R.string.music_tab_downloads_plain),
            badgeCount = downloadedCount.takeIf { it > 0 },
            selected = selectedTab == MusicLibraryTab.DOWNLOADS,
            onClick = { onSelectTab(MusicLibraryTab.DOWNLOADS) }
        )
    }
}

@Composable
private fun MusicSecondaryTabPill(
    label: String,
    badgeCount: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            AppColors.MoonlightPrimaryContainer.copy(alpha = 0.9f)
        } else {
            AppColors.NightSurface.copy(alpha = 0.65f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "musicTabBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) AppColors.MoonlightPrimary else AppColors.TextSecondary,
        animationSpec = tween(durationMillis = 200),
        label = "musicTabText"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor
        )

        if (badgeCount != null && badgeCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    AppColors.MoonlightPrimary.copy(alpha = 0.22f)
                } else {
                    AppColors.NightSurfaceVariant
                }
            ) {
                Text(
                    text = badgeCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloadsSummaryHeader(
    count: Int,
    totalBytes: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.NightSurface.copy(alpha = 0.65f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIcon(
                iconRes = AppIcons.DownloadDone,
                contentDescription = null,
                tint = AppColors.MoonlightPrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = pluralStringResource(R.plurals.music_downloads_count_plurals, count, count),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary
            )
        }

        if (totalBytes > 0L) {
            val locale = LocalConfiguration.current.locales.get(0) ?: PT_BR_LOCALE
            Text(
                text = formatFileSize(totalBytes, locale),
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun DownloadedMusicTrackItem(
    track: MusicTrack,
    album: MusicAlbum?,
    palette: CategoryPalette,
    downloadState: TrackDownloadState,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowBackground by animateColorAsState(
        targetValue = if (isCurrentTrack) {
            palette.containerColor.copy(alpha = 0.85f)
        } else {
            AppColors.NightSurface.copy(alpha = 0.65f)
        },
        animationSpec = tween(durationMillis = 240),
        label = "downloadedRowBg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtworkSurface(
            palette = palette,
            iconRes = album?.iconRes ?: AppIcons.Music,
            coverAssetPath = track.coverUrl,
            fallbackCoverAssetPath = album?.coverUrl,
            isPlaying = isPlaying,
            iconSize = 20.dp,
            shape = RoundedCornerShape(12.dp),
            targetSizeDp = 52.dp,
            modifier = Modifier.size(52.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentTrack) FontWeight.SemiBold else FontWeight.Medium,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = album?.title ?: track.artist,
                style = MaterialTheme.typography.labelMedium,
                color = palette.primaryAccent.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            val offlineReadyLabel = stringResource(R.string.music_track_offline_ready)
            val locale = LocalConfiguration.current.locales.get(0) ?: PT_BR_LOCALE
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                AppIcon(
                    iconRes = AppIcons.DownloadDone,
                    contentDescription = offlineReadyLabel,
                    tint = palette.primaryAccent.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = buildString {
                        append(offlineReadyLabel)
                        if (downloadState.downloadedBytes > 0L) {
                            append(" • ")
                            append(formatFileSize(downloadState.downloadedBytes, locale))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isCurrentTrack) {
                AppIcon(
                    iconRes = if (isPlaying) AppIcons.Sounds else AppIcons.Pause,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.music_track_playing_cd else R.string.music_track_paused_cd
                    ),
                    tint = if (isPlaying) palette.primaryAccent else AppColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDeleteClick),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    iconRes = AppIcons.Delete,
                    contentDescription = stringResource(R.string.music_track_delete_specific_cd, track.title),
                    tint = AppColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloadsStatusCard(
    isLoading: Boolean,
    title: String,
    subtitle: String?,
    actionLabel: String?,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.NightSurface.copy(alpha = 0.65f))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = AppColors.MoonlightPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))
        } else {
            AppIcon(
                iconRes = AppIcons.CloudDownload,
                contentDescription = null,
                tint = AppColors.TextMuted,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Center
        )

        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }

        if (!actionLabel.isNullOrBlank() && !isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = CircleShape,
                color = AppColors.NightSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onActionClick)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.MoonlightPrimary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyCatalogStateCard(
    isLoading: Boolean,
    statusMessage: String?,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.NightSurface.copy(alpha = 0.65f))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = AppColors.MoonlightPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.music_catalog_loading_albums),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = statusMessage
                    ?: stringResource(R.string.music_catalog_empty_sync),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = CircleShape,
                color = AppColors.NightSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRefreshClick)
            ) {
                Text(
                    text = stringResource(R.string.music_refresh_catalog_btn),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.MoonlightPrimary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
        }
    }
}

/**
 * Item individual de álbum musical na grade:
 * - Capa em container quadrado (`1:1`) com `preserveOriginalAspectRatio = true`,
 *   evitando qualquer corte de título ou personagem tanto em capas `1:1` quanto `16:9`.
 * - Nome do álbum, artista, indicador discreto de faixas baixadas e descrição de até 3 linhas.
 */
@Composable
private fun MinimalMusicAlbumItem(
    album: MusicAlbum,
    languageCode: String,
    isPlaying: Boolean,
    downloadedCount: Int,
    totalCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = MusicCategoryPalettes.forAlbumId(album.id)
    val albumDescription = album.localizedDescription(languageCode)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        TrackArtworkSurface(
            palette = palette,
            iconRes = album.iconRes,
            coverAssetPath = album.coverUrl,
            preserveOriginalAspectRatio = false,
            isPlaying = isPlaying,
            iconSize = 28.dp,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(palette.primaryAccent)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = album.artist,
                style = MaterialTheme.typography.labelMedium,
                color = palette.primaryAccent.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (downloadedCount > 0 && totalCount > 0) {
                Text(
                    text = stringResource(R.string.music_album_offline_badge, downloadedCount, totalCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextMuted,
                    maxLines = 1
                )
            }
        }

        if (albumDescription.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            ExpandableAlbumDescription(
                albumId = album.id,
                description = albumDescription,
                textStyle = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
                collapsedMaxLines = 3,
                accentColor = palette.primaryAccent,
                textAlign = TextAlign.Start
            )
        }
    }
}

/**
 * Interior de um álbum musical selecionado:
 * - Botão simples para retornar à visão de álbuns.
 * - Capa centralizada com proporção original preservada (`preserveOriginalAspectRatio = true`) sem cortar arte ou título.
 * - Nome, artista e descrição completa do álbum sem truncamento.
 * - Painel contextual de gerenciamento de downloads do álbum (contagem de baixadas, progresso, baixar e remover downloads).
 * - Lista vertical de músicas com capas individuais carregadas do Cloudflare R2 e ações de download/exclusão.
 */
@Composable
private fun MusicAlbumInterior(
    album: MusicAlbum,
    tracks: List<MusicTrack>,
    playbackState: MusicPlaybackState,
    favoriteMusicIds: Set<String>,
    trackDownloadStates: Map<String, TrackDownloadState>,
    downloadedCount: Int,
    totalCount: Int,
    partialCount: Int,
    overallProgress: Float,
    isAlbumDownloading: Boolean,
    canDownloadAlbum: Boolean,
    isAlbumOfflineReady: Boolean,
    languageCode: String,
    onBackClick: () -> Unit,
    onTrackClick: (MusicTrack) -> Unit,
    onToggleFavoriteTrack: (String) -> Unit,
    onDownloadTrackClick: (MusicTrack) -> Unit,
    onCancelTrackDownloadClick: (MusicTrack) -> Unit,
    onRequestDeleteTrackDownload: (MusicTrack) -> Unit,
    onDownloadAlbumClick: () -> Unit,
    onCancelAlbumDownloadClick: () -> Unit,
    onRequestDeleteAlbumDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = MusicCategoryPalettes.forAlbumId(album.id)
    val albumDescription = album.localizedDescription(languageCode)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                MinimalScreenHeader(
                    title = stringResource(R.string.music_tab_albums),
                    onBackClick = onBackClick,
                    trailingContent = {
                        Text(
                            text = pluralStringResource(R.plurals.track_count, tracks.size, tracks.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.TextMuted,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                ) {
                    TrackArtworkSurface(
                        palette = palette,
                        iconRes = album.iconRes,
                        coverAssetPath = album.coverUrl,
                        preserveOriginalAspectRatio = false,
                        isPlaying = playbackState.currentTrack?.albumId == album.id && playbackState.isPlaying,
                        iconSize = 42.dp,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = AppColors.TextPrimary
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.primaryAccent
                    )

                    if (albumDescription.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ExpandableAlbumDescription(
                            albumId = album.id,
                            description = albumDescription,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            collapsedMaxLines = 3,
                            accentColor = palette.primaryAccent,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (tracks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        AlbumOfflineManagementBar(
                            downloadedCount = downloadedCount,
                            totalCount = totalCount,
                            partialCount = partialCount,
                            overallProgress = overallProgress,
                            isDownloading = isAlbumDownloading,
                            canDownload = canDownloadAlbum,
                            isOfflineReady = isAlbumOfflineReady,
                            accentColor = palette.primaryAccent,
                            onDownloadClick = onDownloadAlbumClick,
                            onCancelClick = onCancelAlbumDownloadClick,
                            onDeleteAlbumDownloadsClick = onRequestDeleteAlbumDownloads
                        )
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.music_album_empty_tracks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp)
                )
            }
        } else {
            items(
                items = tracks,
                key = { it.id }
            ) { track ->
                val isCurrentTrack = playbackState.currentTrack?.id == track.id
                val isPlaying = isCurrentTrack && playbackState.isPlaying
                val downloadState = trackDownloadStates[track.id]
                    ?: TrackDownloadState(trackId = track.id)

                MinimalMusicTrackRow(
                    track = track,
                    album = album,
                    palette = palette,
                    isCurrentTrack = isCurrentTrack,
                    isPlaying = isPlaying,
                    isFavorite = track.id in favoriteMusicIds,
                    downloadState = downloadState,
                    onClick = { onTrackClick(track) },
                    onToggleFavoriteClick = { onToggleFavoriteTrack(track.id) },
                    onDownloadClick = { onDownloadTrackClick(track) },
                    onCancelDownloadClick = { onCancelTrackDownloadClick(track) },
                    onDeleteDownloadClick = { onRequestDeleteTrackDownload(track) },
                    modifier = Modifier.padding(horizontal = 22.dp)
                )
            }
        }
    }
}

@Composable
private fun AlbumOfflineManagementBar(
    downloadedCount: Int,
    totalCount: Int,
    partialCount: Int,
    overallProgress: Float,
    isDownloading: Boolean,
    canDownload: Boolean,
    isOfflineReady: Boolean,
    accentColor: Color,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteAlbumDownloadsClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AppColors.NightSurface.copy(alpha = 0.52f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val statusTitle = when {
                        isDownloading -> stringResource(
                            R.string.music_album_downloading_progress,
                            downloadedCount,
                            totalCount
                        )
                        downloadedCount == totalCount && totalCount > 0 ->
                            stringResource(R.string.music_album_all_downloaded, totalCount)
                        downloadedCount > 0 ->
                            stringResource(
                                R.string.music_album_partial_downloaded,
                                downloadedCount,
                                totalCount
                            )
                        isOfflineReady ->
                            stringResource(R.string.music_album_offline_ready)
                        else ->
                            stringResource(R.string.music_album_available_streaming)
                    }

                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.TextPrimary
                    )

                    if (partialCount > 0 && !isDownloading) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = pluralStringResource(
                                R.plurals.music_album_partial_resume,
                                partialCount,
                                partialCount
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor.copy(alpha = 0.9f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when {
                        isDownloading -> {
                            Surface(
                                shape = CircleShape,
                                color = AppColors.NightSurfaceVariant,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = onCancelClick)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 1.6.dp,
                                        color = accentColor
                                    )
                                    Text(
                                        text = stringResource(R.string.action_pause),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = AppColors.TextPrimary
                                    )
                                }
                            }
                        }

                        canDownload -> {
                            Surface(
                                shape = CircleShape,
                                color = accentColor.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = onDownloadClick)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    AppIcon(
                                        iconRes = AppIcons.CloudDownload,
                                        contentDescription = stringResource(R.string.music_album_download),
                                        tint = accentColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = stringResource(
                                            if (downloadedCount > 0 || partialCount > 0) {
                                                R.string.music_album_download_remaining
                                            } else {
                                                R.string.music_album_download
                                            }
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accentColor
                                    )
                                }
                            }
                        }
                    }

                    if (downloadedCount > 0 && !isDownloading) {
                        Surface(
                            shape = CircleShape,
                            color = AppColors.NightSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onDeleteAlbumDownloadsClick)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AppIcon(
                                    iconRes = AppIcons.Delete,
                                    contentDescription = stringResource(R.string.music_album_remove_downloads),
                                    tint = AppColors.TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(
                                        if (canDownload) R.string.action_remove else R.string.music_album_remove_downloads
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            if (isDownloading || (partialCount > 0 && overallProgress > 0.01f)) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { overallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = accentColor,
                    trackColor = AppColors.NightSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MinimalMusicTrackRow(
    track: MusicTrack,
    album: MusicAlbum,
    palette: CategoryPalette,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    downloadState: TrackDownloadState,
    onClick: () -> Unit,
    onToggleFavoriteClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onDeleteDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowBackground by animateColorAsState(
        targetValue = if (isCurrentTrack) {
            palette.containerColor.copy(alpha = 0.72f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 240),
        label = "musicRowBg"
    )

    val textColor by animateColorAsState(
        targetValue = if (isCurrentTrack) {
            AppColors.TextPrimary
        } else {
            AppColors.TextSecondary
        },
        animationSpec = tween(durationMillis = 240),
        label = "musicRowText"
    )

    val locale = LocalConfiguration.current.locales.get(0) ?: PT_BR_LOCALE
    val statusSubtitle = when {
        downloadState.status == TrackDownloadStatus.DOWNLOADING -> {
            val pct = (downloadState.progressFraction * 100).toInt().coerceIn(0, 100)
            if (pct > 0) {
                stringResource(R.string.music_track_downloading_pct, pct)
            } else {
                stringResource(R.string.music_track_starting_download)
            }
        }
        downloadState.isDownloadedFile -> {
            if (downloadState.downloadedBytes > 0L) {
                stringResource(
                    R.string.music_track_offline_size,
                    formatFileSize(downloadState.downloadedBytes, locale)
                )
            } else {
                stringResource(R.string.music_track_offline)
            }
        }
        downloadState.status == TrackDownloadStatus.PAUSED_PARTIAL -> {
            val pct = (downloadState.progressFraction * 100).toInt().coerceIn(0, 99)
            stringResource(R.string.music_track_partial_resume, pct)
        }
        downloadState.status == TrackDownloadStatus.ERROR -> {
            downloadState.errorMessage ?: stringResource(R.string.music_track_download_failed)
        }
        else -> null
    }

    val durationLabel = remember(track.durationMs) {
        track.durationMs?.takeIf { it > 0L }?.let { duration ->
            val totalSec = duration / 1000L
            String.format(Locale.ROOT, "%d:%02d", totalSec / 60L, totalSec % 60L)
        }
    }

    val combinedSubtitle = remember(durationLabel, statusSubtitle) {
        listOfNotNull(durationLabel, statusSubtitle).joinToString(" • ").ifEmpty { null }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TrackArtworkSurface(
            palette = palette,
            iconRes = album.iconRes,
            coverAssetPath = track.coverUrl,
            fallbackCoverAssetPath = album.coverUrl,
            isPlaying = isPlaying,
            iconSize = 18.dp,
            shape = RoundedCornerShape(11.dp),
            targetSizeDp = 46.dp,
            modifier = Modifier.size(46.dp)
        )

        Spacer(modifier = Modifier.width(13.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentTrack) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (combinedSubtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = combinedSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        downloadState.status == TrackDownloadStatus.ERROR -> AppColors.ErrorSoft
                        downloadState.isDownloadedFile -> palette.primaryAccent.copy(alpha = 0.88f)
                        else -> AppColors.TextMuted
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (isCurrentTrack) {
                AppIcon(
                    iconRes = if (isPlaying) AppIcons.Sounds else AppIcons.Pause,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.music_track_playing_cd else R.string.music_track_paused_cd
                    ),
                    tint = if (isPlaying) palette.primaryAccent else AppColors.TextMuted,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(16.dp)
                )
            }

            IconButton(
                onClick = onToggleFavoriteClick,
                modifier = Modifier.size(40.dp)
            ) {
                AppIcon(
                    iconRes = if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.music_favorite_remove_cd else R.string.music_favorite_add_cd
                    ),
                    tint = if (isFavorite) palette.primaryAccent else AppColors.TextMuted.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            TrackOfflineStatusIndicator(
                downloadState = downloadState,
                accentColor = palette.primaryAccent,
                onDownloadClick = onDownloadClick,
                onCancelClick = onCancelDownloadClick,
                onDeleteClick = onDeleteDownloadClick
            )
        }
    }
}

@Composable
private fun TrackOfflineStatusIndicator(
    downloadState: TrackDownloadState,
    accentColor: Color,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    when {
        downloadState.status == TrackDownloadStatus.DOWNLOADING -> {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCancelClick),
                contentAlignment = Alignment.Center
            ) {
                if (downloadState.progressFraction > 0.02f) {
                    CircularProgressIndicator(
                        progress = { downloadState.progressFraction },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 1.8.dp,
                        color = accentColor,
                        trackColor = AppColors.TextMuted.copy(alpha = 0.25f)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 1.8.dp,
                        color = accentColor
                    )
                }
                AppIcon(
                    iconRes = AppIcons.Close,
                    contentDescription = stringResource(R.string.music_track_cancel_download_cd),
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(9.dp)
                )
            }
        }

        downloadState.isDownloadedFile -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppIcon(
                    iconRes = AppIcons.DownloadDone,
                    contentDescription = stringResource(R.string.music_track_downloaded_device_cd),
                    tint = accentColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDeleteClick),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        iconRes = AppIcons.Delete,
                        contentDescription = stringResource(R.string.music_track_delete_download_cd),
                        tint = AppColors.TextMuted.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        downloadState.canDownloadFromRemote -> {
            val iconTint = when (downloadState.status) {
                TrackDownloadStatus.ERROR -> AppColors.ErrorSoft
                TrackDownloadStatus.PAUSED_PARTIAL -> accentColor.copy(alpha = 0.9f)
                else -> AppColors.TextMuted.copy(alpha = 0.8f)
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDownloadClick),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    iconRes = AppIcons.CloudDownload,
                    contentDescription = stringResource(R.string.music_track_download_offline_cd),
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        downloadState.isBundledAsset -> {
            AppIcon(
                iconRes = AppIcons.DownloadDone,
                contentDescription = stringResource(R.string.music_track_offline_ready),
                tint = AppColors.TextMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

private val PT_BR_LOCALE: Locale = Locale.forLanguageTag("pt-BR")

internal fun formatFileSize(bytes: Long, locale: Locale = PT_BR_LOCALE): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return String.format(locale, "%.0f KB", kb)
    }
    val mb = kb / 1024.0
    return String.format(locale, "%.1f MB", mb)
}

@Composable
private fun ExpandableAlbumDescription(
    albumId: String,
    description: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    lineHeight: TextUnit,
    collapsedMaxLines: Int,
    accentColor: Color,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable(albumId) { mutableStateOf(false) }
    var hasOverflow by remember(albumId, description) { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (textAlign == TextAlign.Center) {
            Alignment.CenterHorizontally
        } else {
            Alignment.Start
        }
    ) {
        Text(
            text = description,
            style = textStyle.copy(lineHeight = lineHeight),
            color = AppColors.TextSecondary,
            textAlign = textAlign,
            maxLines = if (isExpanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!isExpanded) {
                    hasOverflow = result.hasVisualOverflow
                }
            }
        )

        if (shouldShowExpandToggle(hasOverflow = hasOverflow, isExpanded = isExpanded)) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(if (isExpanded) R.string.action_see_less else R.string.action_see_more),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = accentColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

internal fun shouldShowExpandToggle(hasOverflow: Boolean, isExpanded: Boolean): Boolean {
    return hasOverflow || isExpanded
}
