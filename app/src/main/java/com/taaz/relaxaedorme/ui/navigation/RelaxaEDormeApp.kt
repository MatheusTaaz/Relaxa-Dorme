package com.taaz.relaxaedorme.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.taaz.relaxaedorme.ui.components.ExpandedPlayerModal
import com.taaz.relaxaedorme.ui.components.MiniPlayerBar
import com.taaz.relaxaedorme.ui.i18n.createLocalizedContext
import com.taaz.relaxaedorme.ui.screens.favorites.FavoritesScreen
import com.taaz.relaxaedorme.ui.screens.home.HomeScreen
import com.taaz.relaxaedorme.ui.screens.mixer.MixerScreen
import com.taaz.relaxaedorme.ui.screens.music.MusicScreen
import com.taaz.relaxaedorme.ui.screens.sounds.SoundsScreen
import com.taaz.relaxaedorme.ui.theme.MusicCategoryPalettes
import com.taaz.relaxaedorme.ui.theme.RelaxaEDormeTheme
import com.taaz.relaxaedorme.ui.viewmodel.MainViewModel

private const val ROUTE_FAVORITES = "favorites"

/**
 * Container raiz da interface do Relaxa e Dorme.
 *
 * Coordena:
 * - Tema escuro dinâmico conforme a categoria musical ativa
 * - Navegação entre as 4 abas inferiores com preservação de estado
 * - Mini-player persistente acima da barra de navegação
 * - Painel expandido de reprodução musical
 * - Integração independente com o Mixer de Sons Ambientes
 */
@Composable
fun RelaxaEDormeApp(
    mainViewModel: MainViewModel = viewModel()
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by mainViewModel.playbackState.collectAsStateWithLifecycle()
    val mixerState by mainViewModel.mixerState.collectAsStateWithLifecycle()
    val sleepTimerState by mainViewModel.sleepTimerState.collectAsStateWithLifecycle()
    val hasSleepSessionEnded by mainViewModel.hasSleepSessionEnded.collectAsStateWithLifecycle()
    val pendingWakeUpFromSleep by mainViewModel.pendingWakeUpFromSleep.collectAsStateWithLifecycle()
    val isExpandedPlayerOpen by mainViewModel.isExpandedPlayerOpen.collectAsStateWithLifecycle()
    val appLanguage by mainViewModel.appLanguage.collectAsStateWithLifecycle()

    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, appLanguage) {
        Locale.setDefault(appLanguage.locale)
        createLocalizedContext(baseContext, appLanguage)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var wasStopped by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> wasStopped = true
                Lifecycle.Event.ON_START -> {
                    if (wasStopped) {
                        wasStopped = false
                        mainViewModel.onAppForegroundedAfterBackground()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppDestination.HOME.route

    val activePalette = MusicCategoryPalettes.forAlbumId(
        playbackState.currentTrack?.albumId ?: uiState.selectedMusicAlbumId
    )

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalResources provides localizedContext.resources
    ) {
        RelaxaEDormeTheme(activeCategoryPalette = activePalette) {
            Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                AnimatedVisibility(
                    visible = playbackState.currentTrack != null && !isExpandedPlayerOpen,
                    enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            progressStateFlow = mainViewModel.progressState,
                            onExpandClick = mainViewModel::openExpandedPlayer,
                            onPlayPauseClick = mainViewModel::togglePlayPause,
                            onNextClick = mainViewModel::skipToNext
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppDestination.HOME.route,
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(180)) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable(route = AppDestination.HOME.route) {
                    HomeScreen(
                        uiState = uiState,
                        playbackState = playbackState,
                        mixerState = mixerState,
                        sleepTimerStateFlow = mainViewModel.sleepTimerState,
                        listeningStatsFlow = mainViewModel.listeningStats,
                        currentLanguage = appLanguage,
                        onSelectLanguage = mainViewModel::selectAppLanguage,
                        hasSleepSessionEnded = hasSleepSessionEnded,
                        pendingWakeUpFromSleep = pendingWakeUpFromSleep,
                        onAcknowledgeWakeUp = mainViewModel::acknowledgeWakeUp,
                        onPlayTrack = { track, queue, title ->
                            mainViewModel.playTrackFromContext(track, queue, title)
                        },
                        onToggleMusicPlayPause = mainViewModel::togglePlayPause,
                        onResumeMixer = mainViewModel::resumeAllMixerSounds,
                        onOpenExpandedPlayer = mainViewModel::openExpandedPlayer,
                        onNavigateToMixer = {
                            navigateToDestination(navController, AppDestination.MIXER.route)
                        },
                        onNavigateToMusic = {
                            navigateToDestination(navController, AppDestination.MUSIC.route)
                        },
                        onNavigateToSounds = {
                            navigateToDestination(navController, AppDestination.SOUNDS.route)
                        },
                        onNavigateToFavorites = {
                            navigateToDestination(navController, ROUTE_FAVORITES)
                        },
                        isAnimationsActive = currentRoute == AppDestination.HOME.route && !isExpandedPlayerOpen
                    )
                }

                composable(route = AppDestination.MUSIC.route) {
                    MusicScreen(
                        uiState = uiState,
                        playbackState = playbackState,
                        languageCode = appLanguage.code,
                        onSelectAlbum = mainViewModel::selectMusicAlbum,
                        onSelectMusicTab = mainViewModel::selectMusicTab,
                        onPlayTrack = { track, queue, title ->
                            mainViewModel.playTrackFromContext(track, queue, title)
                        },
                        onPlayAllShuffled = mainViewModel::playAllMusicShuffled,
                        onBackClick = { navController.popBackStack() },
                        onToggleFavoriteTrack = mainViewModel::toggleFavoriteMusic,
                        onRefreshCatalog = mainViewModel::refreshMusicCatalog,
                        onRefreshDownloads = mainViewModel::refreshDownloads,
                        onDownloadTrack = mainViewModel::downloadTrack,
                        onCancelTrackDownload = mainViewModel::cancelTrackDownload,
                        onDeleteTrackDownload = mainViewModel::deleteDownloadedTrack,
                        onDownloadAlbum = mainViewModel::downloadAlbum,
                        onCancelAlbumDownload = mainViewModel::cancelAlbumDownload,
                        onDeleteAlbumDownloads = mainViewModel::deleteAlbumDownloads
                    )
                }

                composable(route = AppDestination.SOUNDS.route) {
                    SoundsScreen(
                        uiState = uiState,
                        mixerState = mixerState,
                        onBackClick = { navController.popBackStack() },
                        onSelectSoundCategory = mainViewModel::selectSoundCategory,
                        onToggleSound = mainViewModel::toggleSoundInMixer,
                        onSoundVolumeChange = mainViewModel::setMixerLayerVolume,
                        onDismissStatusMessage = mainViewModel::dismissMixerStatusMessage
                    )
                }

                composable(route = AppDestination.MIXER.route) {
                    MixerScreen(
                        mixerState = mixerState,
                        savedScenes = uiState.savedScenes,
                        onBackClick = { navController.popBackStack() },
                        onMasterVolumeChange = mainViewModel::setMixerMasterVolume,
                        onLayerVolumeChange = mainViewModel::setMixerLayerVolume,
                        onLayerIntervalConfigChange = mainViewModel::setMixerLayerIntervalConfig,
                        onToggleLayerPlayPause = mainViewModel::toggleMixerLayerPlayPause,
                        onRemoveLayer = mainViewModel::removeSoundFromMixer,
                        onPauseAll = mainViewModel::pauseAllMixerSounds,
                        onResumeAll = mainViewModel::resumeAllMixerSounds,
                        onClearAll = mainViewModel::clearAllMixerSounds,
                        onSaveScene = mainViewModel::saveCurrentMixerScene,
                        onLoadScene = mainViewModel::loadMixerScene,
                        onDeleteScene = mainViewModel::deleteSavedScene,
                        onDismissStatusMessage = mainViewModel::dismissMixerStatusMessage,
                        onExploreSoundsClick = {
                            navigateToDestination(navController, AppDestination.SOUNDS.route)
                        }
                    )
                }

                composable(route = ROUTE_FAVORITES) {
                    FavoritesScreen(
                        uiState = uiState,
                        playbackState = playbackState,
                        onBackClick = { navController.popBackStack() },
                        onPlayTrack = { track, queue, title ->
                            mainViewModel.playTrackFromContext(track, queue, title)
                        },
                        onToggleFavoriteTrack = mainViewModel::toggleFavoriteMusic,
                        onExploreMusicClick = {
                            navigateToDestination(navController, AppDestination.MUSIC.route)
                        }
                    )
                }
            }

            if (isExpandedPlayerOpen && playbackState.currentTrack != null) {
                val currentTrack = playbackState.currentTrack
                ExpandedPlayerModal(
                    playbackState = playbackState,
                    progressStateFlow = mainViewModel.progressState,
                    sleepTimerStateFlow = mainViewModel.sleepTimerState,
                    onDismiss = mainViewModel::closeExpandedPlayer,
                    onPlayPauseClick = mainViewModel::togglePlayPause,
                    onPreviousClick = mainViewModel::skipToPrevious,
                    onNextClick = mainViewModel::skipToNext,
                    onSeekFraction = mainViewModel::seekToFraction,
                    onVolumeChange = mainViewModel::setMusicVolume,
                    onToggleMute = mainViewModel::toggleMuteVolume,
                    onToggleShuffle = mainViewModel::toggleShuffle,
                    onCycleRepeatMode = mainViewModel::cycleRepeatMode,
                    onCycleSleepTimer = mainViewModel::cycleSleepTimerPreset,
                    onDismissError = mainViewModel::dismissPlaybackError,
                    isFavorite = currentTrack != null && uiState.isTrackFavorite(currentTrack.id),
                    onToggleFavoriteClick = {
                        currentTrack?.let { mainViewModel.toggleFavoriteMusic(it.id) }
                    }
                )
            }
        }
        }
    }
}

private fun navigateToDestination(
    navController: NavHostController,
    route: String
) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
