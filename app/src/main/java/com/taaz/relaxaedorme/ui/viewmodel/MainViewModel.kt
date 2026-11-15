package com.taaz.relaxaedorme.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.taaz.relaxaedorme.audio.AmbientMixerState
import com.taaz.relaxaedorme.audio.AmbientSoundMixerController
import com.taaz.relaxaedorme.audio.AppAudioSessionCoordinator
import com.taaz.relaxaedorme.audio.MusicPlaybackState
import com.taaz.relaxaedorme.audio.MusicPlayerController
import com.taaz.relaxaedorme.audio.MusicProgressState
import com.taaz.relaxaedorme.audio.SleepTimerState
import com.taaz.relaxaedorme.data.model.AmbientSound
import com.taaz.relaxaedorme.data.model.MusicAlbum
import com.taaz.relaxaedorme.data.model.MusicTrack
import com.taaz.relaxaedorme.data.model.SavedScene
import com.taaz.relaxaedorme.data.model.SoundCategory
import com.taaz.relaxaedorme.data.model.SoundIntervalConfig
import com.taaz.relaxaedorme.data.offline.TrackDownloadState
import com.taaz.relaxaedorme.data.offline.TrackDownloadStatus
import com.taaz.relaxaedorme.ui.i18n.AppLanguage
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Abas secundárias de navegação dentro da seção principal "Músicas".
 */
enum class MusicLibraryTab {
    ALBUMS,
    DOWNLOADS
}

/**
 * Estado consolidado da interface principal para as 4 abas.
 */
data class MainUiState(
    val musicAlbums: List<MusicAlbum> = emptyList(),
    val allMusicTracks: List<MusicTrack> = emptyList(),
    val selectedMusicAlbumId: String? = null,
    val selectedMusicTab: MusicLibraryTab = MusicLibraryTab.ALBUMS,
    val isLoadingCatalog: Boolean = false,
    val catalogStatusMessage: String? = null,
    val isLoadingDownloads: Boolean = false,
    val downloadsErrorMessage: String? = null,
    val soundCategories: List<SoundCategory> = emptyList(),
    val allAmbientSounds: List<AmbientSound> = emptyList(),
    val selectedSoundCategoryId: String? = null,
    val favoriteMusicIds: Set<String> = emptySet(),
    val savedScenes: List<SavedScene> = emptyList(),
    val recentTracks: List<MusicTrack> = emptyList(),
    val trackDownloadStates: Map<String, TrackDownloadState> = emptyMap()
) {
    val selectedMusicAlbum: MusicAlbum?
        get() = musicAlbums.firstOrNull { it.id == selectedMusicAlbumId }

    val selectedAlbumTracks: List<MusicTrack>
        get() = selectedMusicAlbum?.tracks
            ?: if (selectedMusicAlbumId == null) {
                allMusicTracks
            } else {
                allMusicTracks.filter { it.albumId == selectedMusicAlbumId }
            }

    /**
     * Lista exclusiva de faixas efetivamente baixadas no armazenamento interno do dispositivo.
     * Nunca inclui músicas apenas em streaming nem faixas demo embarcadas que não foram baixadas.
     */
    val downloadedTracks: List<MusicTrack>
        get() = allMusicTracks.filter { track ->
            getTrackDownloadState(track).isDownloadedFile
        }

    val filteredAmbientSounds: List<AmbientSound>
        get() = if (selectedSoundCategoryId == null) {
            allAmbientSounds
        } else {
            allAmbientSounds.filter { it.categoryId == selectedSoundCategoryId }
        }

    val selectedSoundCategory: SoundCategory?
        get() = soundCategories.firstOrNull { it.id == selectedSoundCategoryId }

    fun getAlbumForTrack(track: MusicTrack): MusicAlbum? =
        musicAlbums.firstOrNull { it.id == track.albumId }

    fun getCategoryForSound(sound: AmbientSound): SoundCategory? =
        soundCategories.firstOrNull { it.id == sound.categoryId }

    fun isTrackFavorite(trackId: String): Boolean =
        trackId in favoriteMusicIds

    fun getTrackDownloadState(track: MusicTrack): TrackDownloadState =
        trackDownloadStates[track.id] ?: TrackDownloadState(trackId = track.id)

    fun getAlbumTracks(albumId: String): List<MusicTrack> =
        musicAlbums.firstOrNull { it.id == albumId }?.tracks
            ?: allMusicTracks.filter { it.albumId == albumId }

    fun getAlbumTotalTracksCount(albumId: String): Int =
        getAlbumTracks(albumId).size

    fun getAlbumDownloadedCount(albumId: String): Int =
        getAlbumTracks(albumId).count { getTrackDownloadState(it).isDownloadedFile }

    fun getAlbumOfflineAvailableCount(albumId: String): Int =
        getAlbumTracks(albumId).count { getTrackDownloadState(it).isAvailableOffline }

    fun getAlbumPartialCount(albumId: String): Int =
        getAlbumTracks(albumId).count {
            val status = getTrackDownloadState(it).status
            status == TrackDownloadStatus.PAUSED_PARTIAL || status == TrackDownloadStatus.DOWNLOADING
        }

    fun getAlbumOverallProgress(albumId: String): Float {
        val tracks = getAlbumTracks(albumId)
        if (tracks.isEmpty()) return 0f
        val sum = tracks.sumOf { track ->
            val dl = getTrackDownloadState(track)
            when {
                dl.isDownloadedFile -> 1.0
                dl.status == TrackDownloadStatus.DOWNLOADING || dl.status == TrackDownloadStatus.PAUSED_PARTIAL ->
                    dl.progressFraction.coerceIn(0f, 1f).toDouble()
                else -> 0.0
            }
        }
        return (sum / tracks.size).toFloat().coerceIn(0f, 1f)
    }

    fun hasAlbumAnyDownloadedTracks(albumId: String): Boolean =
        getAlbumDownloadedCount(albumId) > 0

    fun isAlbumDownloading(albumId: String): Boolean {
        val tracks = getAlbumTracks(albumId)
        return tracks.any { getTrackDownloadState(it).status == TrackDownloadStatus.DOWNLOADING }
    }

    fun isAlbumFullyOffline(albumId: String): Boolean {
        val tracks = getAlbumTracks(albumId)
        return tracks.isNotEmpty() && tracks.all { getTrackDownloadState(it).isAvailableOffline }
    }

    fun isAlbumFullyDownloaded(albumId: String): Boolean {
        val tracks = getAlbumTracks(albumId)
        return tracks.isNotEmpty() && tracks.all { getTrackDownloadState(it).isDownloadedFile }
    }

    fun canDownloadAlbumFromRemote(albumId: String): Boolean {
        val tracks = getAlbumTracks(albumId)
        return tracks.any { track ->
            val dl = getTrackDownloadState(track)
            dl.canDownloadFromRemote && !dl.isDownloadedFile
        }
    }
}

/**
 * ViewModel central compartilhado entre as 4 abas, o Mini-Player, o Painel Expandido e o Mixer.
 *
 * Mantém instâncias únicas e independentes de [MusicPlayerController] e [AmbientSoundMixerController]
 * durante toda a sessão do aplicativo.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val audioCoordinator = AppAudioSessionCoordinator.getInstance(application.applicationContext)
    private val catalogRepository = audioCoordinator.catalogRepository
    private val preferencesRepository = audioCoordinator.preferencesRepository
    private val playerController = audioCoordinator.playerController
    private val mixerController = audioCoordinator.mixerController
    private val sleepTimerController = audioCoordinator.sleepTimerController
    private val downloadManager = audioCoordinator.downloadManager

    val playbackState: StateFlow<MusicPlaybackState> = playerController.playbackState
    val progressState: StateFlow<MusicProgressState> = playerController.progressState
    val mixerState: StateFlow<AmbientMixerState> = mixerController.mixerState
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimerController.sleepTimerState
    val listeningStats: StateFlow<com.taaz.relaxaedorme.data.repository.ListeningStatsSnapshot> =
        audioCoordinator.listeningStats
    val hasSleepSessionEnded: StateFlow<Boolean> = audioCoordinator.hasSleepSessionEnded
    val pendingWakeUpFromSleep: StateFlow<Boolean> = preferencesRepository.pendingWakeUpFromSleep
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false
        )

    val appLanguage: StateFlow<AppLanguage> = preferencesRepository.appLanguageCode
        .map { code -> AppLanguage.fromCode(code) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppLanguage.DEFAULT
        )

    private val _selectedMusicAlbumId = MutableStateFlow<String?>(null)
    private val _selectedMusicTab = MutableStateFlow(MusicLibraryTab.ALBUMS)
    private val _selectedSoundCategoryId = MutableStateFlow<String?>(null)
    private val _isExpandedPlayerOpen = MutableStateFlow(false)
    val isExpandedPlayerOpen: StateFlow<Boolean> = _isExpandedPlayerOpen.asStateFlow()

    private val catalogSnapshotFlow = combine(
        catalogRepository.musicAlbumsFlow,
        catalogRepository.allMusicTracksFlow,
        catalogRepository.isLoadingCatalogFlow,
        catalogRepository.catalogStatusMessageFlow
    ) { albums, tracks, isLoading, statusMessage ->
        CatalogSnapshot(
            musicAlbums = albums,
            allMusicTracks = tracks,
            isLoadingCatalog = isLoading,
            catalogStatusMessage = statusMessage
        )
    }

    private val selectionAndPrefsFlow = combine(
        _selectedMusicAlbumId,
        _selectedSoundCategoryId,
        preferencesRepository.favoriteMusicIds,
        preferencesRepository.savedScenes,
        preferencesRepository.recentMusicIds
    ) { selectedAlbumId, selectedSoundCatId, favoriteIds, scenes, recentIds ->
        SelectionAndPrefsSnapshot(
            selectedMusicAlbumId = selectedAlbumId,
            selectedSoundCategoryId = selectedSoundCatId,
            favoriteMusicIds = favoriteIds,
            savedScenes = scenes,
            recentMusicIds = recentIds
        )
    }

    private val downloadSnapshotFlow = combine(
        downloadManager.downloadStates,
        downloadManager.isLoadingDownloads,
        downloadManager.downloadsErrorMessage,
        _selectedMusicTab
    ) { states, isLoading, errorMessage, selectedTab ->
        DownloadAndTabSnapshot(
            downloadStates = states,
            isLoadingDownloads = isLoading,
            downloadsErrorMessage = errorMessage,
            selectedMusicTab = selectedTab
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        catalogSnapshotFlow,
        selectionAndPrefsFlow,
        downloadSnapshotFlow
    ) { catalogSnapshot, prefsSnapshot, downloadSnapshot ->
        val allTracks = catalogSnapshot.allMusicTracks
        val tracksById = allTracks.associateBy { it.id }
        // IDs antigos no histórico que não estejam no catálogo atual são ignorados com segurança sem apagar a persistência
        val resolvedRecentTracks = prefsSnapshot.recentMusicIds.mapNotNull { tracksById[it] }

        MainUiState(
            musicAlbums = catalogSnapshot.musicAlbums,
            allMusicTracks = allTracks,
            selectedMusicAlbumId = prefsSnapshot.selectedMusicAlbumId,
            selectedMusicTab = downloadSnapshot.selectedMusicTab,
            isLoadingCatalog = catalogSnapshot.isLoadingCatalog,
            catalogStatusMessage = catalogSnapshot.catalogStatusMessage,
            isLoadingDownloads = downloadSnapshot.isLoadingDownloads,
            downloadsErrorMessage = downloadSnapshot.downloadsErrorMessage,
            soundCategories = catalogRepository.soundCategories,
            allAmbientSounds = catalogRepository.allAmbientSounds,
            selectedSoundCategoryId = prefsSnapshot.selectedSoundCategoryId,
            favoriteMusicIds = prefsSnapshot.favoriteMusicIds,
            savedScenes = prefsSnapshot.savedScenes,
            recentTracks = resolvedRecentTracks,
            trackDownloadStates = downloadSnapshot.downloadStates
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState(
            musicAlbums = catalogRepository.musicAlbums,
            allMusicTracks = catalogRepository.allMusicTracks,
            isLoadingCatalog = catalogRepository.isLoadingCatalogFlow.value,
            catalogStatusMessage = catalogRepository.catalogStatusMessageFlow.value,
            isLoadingDownloads = downloadManager.isLoadingDownloads.value,
            downloadsErrorMessage = downloadManager.downloadsErrorMessage.value,
            soundCategories = catalogRepository.soundCategories,
            allAmbientSounds = catalogRepository.allAmbientSounds,
            selectedSoundCategoryId = null,
            trackDownloadStates = downloadManager.downloadStates.value
        )
    )

    private data class CatalogSnapshot(
        val musicAlbums: List<MusicAlbum>,
        val allMusicTracks: List<MusicTrack>,
        val isLoadingCatalog: Boolean,
        val catalogStatusMessage: String?
    )

    private data class SelectionAndPrefsSnapshot(
        val selectedMusicAlbumId: String?,
        val selectedSoundCategoryId: String?,
        val favoriteMusicIds: Set<String>,
        val savedScenes: List<SavedScene>,
        val recentMusicIds: List<String>
    )

    private data class DownloadAndTabSnapshot(
        val downloadStates: Map<String, TrackDownloadState>,
        val isLoadingDownloads: Boolean,
        val downloadsErrorMessage: String?,
        val selectedMusicTab: MusicLibraryTab
    )

    // --- Navegação e Catálogo ---

    fun selectMusicAlbum(albumId: String?) {
        _selectedMusicAlbumId.value = albumId
    }

    fun selectMusicTab(tab: MusicLibraryTab) {
        _selectedMusicTab.value = tab
        if (tab == MusicLibraryTab.DOWNLOADS) {
            refreshDownloads()
        }
    }

    fun selectSoundCategory(categoryId: String?) {
        _selectedSoundCategoryId.value = categoryId
    }

    fun selectAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferencesRepository.setAppLanguageCode(language.code)
        }
    }

    fun refreshMusicCatalog(force: Boolean = true) {
        viewModelScope.launch {
            catalogRepository.refreshRemoteCatalog(force = force)
        }
    }

    fun refreshMusicCatalogIfNeeded() {
        viewModelScope.launch {
            catalogRepository.refreshRemoteCatalog(force = false)
        }
    }

    fun refreshDownloads() {
        downloadManager.refreshTracksState(catalogRepository.allMusicTracks)
    }

    fun toggleFavoriteMusic(trackId: String) {
        viewModelScope.launch {
            preferencesRepository.toggleFavoriteMusic(trackId)
        }
    }

    // --- Downloads Offline (Cloudflare R2) ---

    fun downloadTrack(track: MusicTrack) {
        downloadManager.downloadTrack(track)
    }

    fun cancelTrackDownload(track: MusicTrack) {
        downloadManager.cancelDownload(track)
    }

    fun deleteDownloadedTrack(track: MusicTrack) {
        playerController.prepareForTrackOfflineDeletion(track)
        downloadManager.deleteTrackDownload(track)
    }

    fun downloadAlbum(albumId: String) {
        val tracks = catalogRepository.getMusicTracksByAlbum(albumId)
        downloadManager.downloadCategoryTracks(tracks)
    }

    fun cancelAlbumDownload(albumId: String) {
        val tracks = catalogRepository.getMusicTracksByAlbum(albumId)
        downloadManager.cancelCategoryDownloads(tracks)
    }

    fun deleteAlbumDownloads(albumId: String) {
        val tracks = catalogRepository.getMusicTracksByAlbum(albumId)
        tracks.forEach { track ->
            if (downloadManager.getTrackState(track).isDownloadedFile) {
                playerController.prepareForTrackOfflineDeletion(track)
            }
        }
        downloadManager.deleteAlbumDownloads(tracks)
    }

    // --- Controles do Mixer de Sons Ambientes ---

    fun toggleSoundInMixer(sound: AmbientSound) {
        if (mixerState.value.isSoundActive(sound.id)) {
            mixerController.removeSound(sound.id)
        } else {
            mixerController.addSound(sound)
        }
    }

    fun addOrActivateSoundInMixer(sound: AmbientSound) {
        audioCoordinator.clearSleepSessionEnded(clearWakeUpPersistence = true)
        mixerController.addSound(sound)
    }

    fun removeSoundFromMixer(soundId: String) {
        mixerController.removeSound(soundId)
    }

    fun toggleMixerLayerPlayPause(soundId: String) {
        mixerController.toggleLayerPlayPause(soundId)
    }

    fun setMixerLayerVolume(soundId: String, volume: Float) {
        mixerController.setLayerVolume(soundId, volume)
    }

    fun setMixerLayerIntervalConfig(soundId: String, config: SoundIntervalConfig) {
        mixerController.setLayerIntervalConfig(soundId, config)
    }

    fun setMixerMasterVolume(masterVolume: Float) {
        mixerController.setMasterVolume(masterVolume)
    }

    fun pauseAllMixerSounds() {
        mixerController.pauseAll()
    }

    fun resumeAllMixerSounds() {
        audioCoordinator.clearSleepSessionEnded(clearWakeUpPersistence = true)
        mixerController.resumeAll()
    }

    fun clearAllMixerSounds() {
        mixerController.clearAll()
    }

    fun saveCurrentMixerScene(sceneName: String) {
        val currentMixer = mixerState.value
        if (!currentMixer.hasActiveSounds) return
        val scene = currentMixer.toSavedScene(
            id = "scene_${UUID.randomUUID()}",
            name = sceneName
        )
        viewModelScope.launch {
            preferencesRepository.saveScene(scene)
        }
    }

    fun loadMixerScene(scene: SavedScene) {
        mixerController.loadScene(scene)
    }

    fun deleteSavedScene(sceneId: String) {
        viewModelScope.launch {
            preferencesRepository.deleteScene(sceneId)
        }
    }

    fun dismissMixerStatusMessage() {
        mixerController.clearStatusMessage()
    }

    // --- Controles do Player Musical ---

    fun playTrackFromContext(
        track: MusicTrack,
        queue: List<MusicTrack>,
        queueTitle: String
    ) {
        audioCoordinator.clearSleepSessionEnded(clearWakeUpPersistence = true)
        playerController.playTrack(
            track = track,
            queue = queue,
            queueTitle = queueTitle
        )
    }

    fun playAllMusicShuffled() {
        val allTracks = uiState.value.allMusicTracks
        if (allTracks.isEmpty()) return
        audioCoordinator.clearSleepSessionEnded(clearWakeUpPersistence = true)
        playerController.playShuffledQueue(
            queue = allTracks,
            queueTitle = "Biblioteca · Aleatório"
        )
    }

    fun togglePlayPause() {
        audioCoordinator.clearSleepSessionEnded(clearWakeUpPersistence = true)
        playerController.togglePlayPause()
    }

    fun skipToNext() {
        playerController.skipToNext()
    }

    fun skipToPrevious() {
        playerController.skipToPrevious()
    }

    fun seekToFraction(fraction: Float) {
        playerController.seekToFraction(fraction)
    }

    fun setMusicVolume(volume: Float) {
        playerController.setVolume(volume, smoothTransition = false)
    }

    fun toggleMuteVolume() {
        playerController.toggleMute()
    }

    fun cycleRepeatMode() {
        playerController.cycleRepeatMode()
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun cycleSleepTimerPreset() {
        audioCoordinator.clearSleepSessionEnded(clearWakeUpPersistence = true)
        sleepTimerController.cyclePreset()
    }

    fun onAppForegroundedAfterBackground() {
        audioCoordinator.clearSleepSessionEnded(clearWakeUpPersistence = false)
        refreshMusicCatalogIfNeeded()
    }

    fun acknowledgeWakeUp() {
        audioCoordinator.clearSleepSessionEnded(clearWakeUpPersistence = true)
    }

    fun cancelSleepTimer() {
        sleepTimerController.cancel()
    }

    fun openExpandedPlayer() {
        if (playbackState.value.currentTrack != null) {
            _isExpandedPlayerOpen.value = true
        }
    }

    fun closeExpandedPlayer() {
        _isExpandedPlayerOpen.value = false
    }

    fun dismissPlaybackError() {
        playerController.clearError()
    }
}
