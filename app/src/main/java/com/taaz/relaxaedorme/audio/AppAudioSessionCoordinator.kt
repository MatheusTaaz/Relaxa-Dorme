package com.taaz.relaxaedorme.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.taaz.relaxaedorme.R
import com.taaz.relaxaedorme.data.offline.MusicDownloadManager
import com.taaz.relaxaedorme.data.offline.MusicOfflineStorageManager
import com.taaz.relaxaedorme.data.offline.TrackDownloadStatus
import com.taaz.relaxaedorme.data.repository.CatalogRepository
import com.taaz.relaxaedorme.data.repository.FavoritesRepository
import com.taaz.relaxaedorme.data.repository.ListeningStatsRepository
import com.taaz.relaxaedorme.data.repository.ListeningStatsSnapshot
import com.taaz.relaxaedorme.ui.i18n.AppLanguage
import com.taaz.relaxaedorme.ui.i18n.createLocalizedContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Descreve o estado de apresentação da sessão de mídia unificada para a notificação do Android
 * e controles da tela de bloqueio.
 */
data class MediaSessionPresentation(
    val isActive: Boolean,
    val isMixerPrimary: Boolean,
    val title: String,
    val artist: String,
    val albumTitle: String,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val canSkipNextOrPrevious: Boolean,
    val isSeekable: Boolean,
    val durationMs: Long,
    val coverUrl: String?
)

/**
 * Coordenador central de sessão de áudio em segundo plano do aplicativo Relaxa e Dorme.
 *
 * Responsável exclusivamente por:
 * 1. Manter instâncias únicas de [MusicPlayerController] e [AmbientSoundMixerController] no escopo
 *    do aplicativo, evitando recriação ou perda de timers quando a Activity é minimizada/recriada.
 * 2. Gerenciar o foco de áudio do Android ([AudioFocusRequest]) de forma unificada para Música e Mixer.
 * 3. Manter [PowerManager.WakeLock] parcial ativo enquanto houver reprodução ou temporizadores em andamento.
 * 4. Expor o [Player] unificado ([sessionPlayer]) para o [MediaSession] do [RelaxaPlaybackService].
 *
 * Nota de arquitetura: O carregamento, download e cache de imagens de capa não faz parte deste
 * coordenador, mantendo o fluxo de áudio isolado de qualquer falha visual.
 */
class AppAudioSessionCoordinator private constructor(
    context: Context
) {
    private val appContext: Context = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val catalogRepository = CatalogRepository(appContext)
    val preferencesRepository = FavoritesRepository(appContext)
    val listeningStatsRepository = ListeningStatsRepository(appContext)
    val listeningStatsTracker = ListeningStatsTracker(
        store = listeningStatsRepository,
        scope = appScope
    )
    val listeningStats: StateFlow<ListeningStatsSnapshot> = listeningStatsTracker.stats
    val offlineStorageManager = MusicOfflineStorageManager(appContext)
    val downloadManager = MusicDownloadManager(
        storageManager = offlineStorageManager,
        scope = appScope
    )

    val playerController = MusicPlayerController(
        context = appContext,
        catalogRepository = catalogRepository,
        scope = appScope,
        offlineStorageManager = offlineStorageManager,
        onTrackStarted = { trackId ->
            appScope.launch {
                preferencesRepository.recordRecentMusic(trackId)
            }
        },
        localizedContextProvider = { localizedContext }
    )

    val mixerController = AmbientSoundMixerController(
        context = appContext,
        catalogRepository = catalogRepository,
        scope = appScope
    )

    val sleepTimerController = SleepTimerController(
        scope = appScope,
        ownsScope = false,
        onFadeMultiplierChanged = { multiplier ->
            sleepFadeMultiplier = multiplier.coerceIn(0f, 1f)
            applyCombinedOutputAttenuation()
        },
        onTimerFinished = {
            resumeMusicOnFocusGain = false
            resumeMixerOnFocusGain = false
            pausedMusicBySystemControl = false
            pausedMixerBySystemControl = false
            playerController.pause()
            mixerController.pauseAll()
            _hasSleepSessionEnded.value = true
            appScope.launch {
                preferencesRepository.markSleepEndedByTimer()
            }
        }
    )

    private val _hasSleepSessionEnded = MutableStateFlow(false)
    val hasSleepSessionEnded: StateFlow<Boolean> = _hasSleepSessionEnded.asStateFlow()

    fun clearSleepSessionEnded(clearWakeUpPersistence: Boolean = false) {
        _hasSleepSessionEnded.value = false
        if (clearWakeUpPersistence) {
            appScope.launch {
                preferencesRepository.clearPendingWakeUpFromSleep()
            }
        }
    }

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock: PowerManager.WakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "RelaxaEDorme:AudioSessionWakeLock"
    ).apply {
        setReferenceCounted(false)
    }

    private var hasAudioFocus = false
    private var resumeMusicOnFocusGain = false
    private var resumeMixerOnFocusGain = false

    private var pausedMusicBySystemControl = false
    private var pausedMixerBySystemControl = false

    private var duckAttenuationFactor: Float = 1.0f
    private var sleepFadeMultiplier: Float = 1.0f

    private var isNoisyReceiverRegistered = false
    private var isServiceRunning = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        appScope.launch {
            onAudioFocusChange(focusChange)
        }
    }

    private val audioFocusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            PlatformAudioAttributes.Builder()
                .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
                .setContentType(PlatformAudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAcceptsDelayedFocusGain(true)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(audioFocusChangeListener)
        .build()

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                handleSystemPlayWhenReady(playWhenReady = false)
            }
        }
    }

    @Volatile
    private var localizedContext: Context = createLocalizedContext(appContext, AppLanguage.DEFAULT)

    @OptIn(UnstableApi::class)
    val sessionPlayer: Player = HybridMediaSessionPlayer()

    val isAnyAudioPlaying: Boolean
        get() = playerController.playbackState.value.isPlaying ||
            mixerController.mixerState.value.anyPlaying

    val hasAnyActiveSession: Boolean
        get() = playerController.playbackState.value.currentTrack != null ||
            mixerController.mixerState.value.hasActiveSounds

    init {
        downloadManager.refreshTracksState(catalogRepository.allMusicTracks)

        appScope.launch {
            catalogRepository.allMusicTracksFlow.collect { tracks ->
                downloadManager.refreshTracksState(tracks)
            }
        }

        appScope.launch {
            catalogRepository.refreshRemoteCatalog()
        }

        appScope.launch(Dispatchers.Main.immediate) {
            preferencesRepository.appLanguageCode.collect { code ->
                val language = AppLanguage.fromCode(code)
                localizedContext = createLocalizedContext(appContext, language)
                (sessionPlayer as? HybridMediaSessionPlayer)?.notifyStateChanged()
            }
        }

        appScope.launch {
            var previousDownloadedIds = emptySet<String>()
            downloadManager.downloadStates.collect { statesMap ->
                val currentDownloadedIds = statesMap.values
                    .filter { it.status == TrackDownloadStatus.DOWNLOADED }
                    .map { it.trackId }
                    .toSet()
                val newlyDownloadedIds = currentDownloadedIds - previousDownloadedIds
                previousDownloadedIds = currentDownloadedIds
                if (newlyDownloadedIds.isNotEmpty()) {
                    for (trackId in newlyDownloadedIds) {
                        val track = catalogRepository.getMusicTrackById(trackId) ?: continue
                        playerController.refreshQueuedMediaItemUriIfDownloaded(track)
                    }
                }
            }
        }

        appScope.launch {
            combine(
                playerController.playbackState,
                mixerController.mixerState,
                sleepTimerController.sleepTimerState
            ) { musicState, mixerState, timerState ->
                Triple(musicState, mixerState, timerState)
            }.collect { (musicState, mixerState, timerState) ->
                val isMusicActuallyPlaying = musicState.isPlaying && !musicState.isBuffering
                val anyPlaying = isMusicActuallyPlaying || mixerState.anyPlaying
                val anySession = musicState.currentTrack != null || mixerState.hasActiveSounds
                val keepAwakeForTimer = timerState.isActive && anySession

                listeningStatsTracker.onPlaybackStateChanged(
                    isMusicPlaying = isMusicActuallyPlaying,
                    isAmbientPlaying = mixerState.anyPlaying
                )

                if (musicState.isPlaying) {
                    pausedMusicBySystemControl = false
                }
                if (mixerState.anyPlaying) {
                    pausedMixerBySystemControl = false
                }

                notifySessionPlayerChanged()
                updateWakeLockAndAudioFocus(
                    anyPlaying = anyPlaying,
                    anySession = anySession,
                    keepAwakeForTimer = keepAwakeForTimer
                )
                updatePlaybackServiceState(anyPlaying = anyPlaying, anySession = anySession)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun notifySessionPlayerChanged() {
        (sessionPlayer as? HybridMediaSessionPlayer)?.notifyStateChanged()
    }

    private fun applyCombinedOutputAttenuation() {
        val combined = (duckAttenuationFactor * sleepFadeMultiplier).coerceIn(0f, 1f)
        playerController.setOutputAttenuation(combined)
        mixerController.setOutputAttenuation(combined)
    }

    private fun updateWakeLockAndAudioFocus(
        anyPlaying: Boolean,
        anySession: Boolean,
        keepAwakeForTimer: Boolean
    ) {
        if (anyPlaying || keepAwakeForTimer) {
            acquireWakeLockIfNeeded()
        } else {
            releaseWakeLockIfHeld()
        }

        if (anyPlaying) {
            requestAudioFocusIfNeeded()
            registerBecomingNoisyReceiverIfNeeded()
        } else {
            unregisterBecomingNoisyReceiverIfNeeded()
            if (!resumeMusicOnFocusGain && !resumeMixerOnFocusGain && !anySession) {
                abandonAudioFocusIfHeld()
            }
        }
    }

    private fun updatePlaybackServiceState(anyPlaying: Boolean, anySession: Boolean) {
        if ((anyPlaying || anySession) && !isServiceRunning) {
            try {
                val intent = Intent(appContext, RelaxaPlaybackService::class.java)
                appContext.startService(intent)
                isServiceRunning = true
            } catch (_: Exception) {
            }
        } else if (!anyPlaying && !anySession && isServiceRunning) {
            try {
                val intent = Intent(appContext, RelaxaPlaybackService::class.java)
                appContext.stopService(intent)
            } catch (_: Exception) {
            } finally {
                isServiceRunning = false
            }
        }
    }

    internal fun onServiceCreated() {
        isServiceRunning = true
    }

    internal fun onServiceDestroyed() {
        isServiceRunning = false
    }

    private fun acquireWakeLockIfNeeded() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(MAX_WAKE_LOCK_TIMEOUT_MS)
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLockIfHeld() {
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        } catch (_: Exception) {
        }
    }

    private fun requestAudioFocusIfNeeded() {
        if (hasAudioFocus) return
        val result = audioManager.requestAudioFocus(audioFocusRequest)
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocusIfHeld() {
        if (!hasAudioFocus) return
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        hasAudioFocus = false
    }

    private fun registerBecomingNoisyReceiverIfNeeded() {
        if (isNoisyReceiverRegistered) return
        try {
            ContextCompat.registerReceiver(
                appContext,
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isNoisyReceiverRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun unregisterBecomingNoisyReceiverIfNeeded() {
        if (!isNoisyReceiverRegistered) return
        try {
            appContext.unregisterReceiver(becomingNoisyReceiver)
        } catch (_: Exception) {
        } finally {
            isNoisyReceiverRegistered = false
        }
    }

    internal fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val musicPlaying = playerController.playbackState.value.isPlaying
                val mixerPlaying = mixerController.mixerState.value.anyPlaying
                if (musicPlaying || mixerPlaying) {
                    resumeMusicOnFocusGain = musicPlaying
                    resumeMixerOnFocusGain = mixerPlaying
                    if (musicPlaying) playerController.pause()
                    if (mixerPlaying) mixerController.pauseAll()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                duckAttenuationFactor = DUCK_ATTENUATION_FACTOR
                applyCombinedOutputAttenuation()
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                duckAttenuationFactor = 1.0f
                applyCombinedOutputAttenuation()

                val shouldResumeMusic = resumeMusicOnFocusGain
                val shouldResumeMixer = resumeMixerOnFocusGain
                resumeMusicOnFocusGain = false
                resumeMixerOnFocusGain = false

                if (shouldResumeMusic) {
                    playerController.play()
                }
                if (shouldResumeMixer) {
                    mixerController.resumeAll()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                resumeMusicOnFocusGain = false
                resumeMixerOnFocusGain = false
                duckAttenuationFactor = 1.0f
                applyCombinedOutputAttenuation()
                handleSystemPlayWhenReady(playWhenReady = false)
                abandonAudioFocusIfHeld()
            }
        }
    }

    internal fun handleSystemPlayWhenReady(playWhenReady: Boolean) {
        val musicState = playerController.playbackState.value
        val mixerState = mixerController.mixerState.value

        if (!playWhenReady) {
            val musicPlaying = musicState.isPlaying
            val mixerPlaying = mixerState.anyPlaying
            if (musicPlaying || mixerPlaying) {
                pausedMusicBySystemControl = musicPlaying
                pausedMixerBySystemControl = mixerPlaying
            }
            if (musicPlaying) {
                playerController.pause()
            }
            if (mixerPlaying) {
                mixerController.pauseAll()
            }
        } else {
            val resumeMusic = pausedMusicBySystemControl ||
                (!pausedMixerBySystemControl && musicState.currentTrack != null && !mixerState.hasActiveSounds)
            val resumeMixer = pausedMixerBySystemControl ||
                (!pausedMusicBySystemControl && mixerState.hasActiveSounds && musicState.currentTrack == null)

            pausedMusicBySystemControl = false
            pausedMixerBySystemControl = false

            if (resumeMusic && musicState.currentTrack != null) {
                playerController.play()
            }
            if (resumeMixer && mixerState.hasActiveSounds) {
                mixerController.resumeAll()
            }
            if (!resumeMusic && !resumeMixer) {
                if (musicState.currentTrack != null) {
                    playerController.play()
                } else if (mixerState.hasActiveSounds) {
                    mixerController.resumeAll()
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private inner class HybridMediaSessionPlayer : SimpleBasePlayer(Looper.getMainLooper()) {

        fun notifyStateChanged() {
            invalidateState()
        }

        override fun getState(): State {
            val musicState = playerController.playbackState.value
            val progressState = playerController.progressState.value
            val mixerState = mixerController.mixerState.value
            val resContext = localizedContext
            val presentation = buildSessionPresentation(
                musicState = musicState,
                mixerState = mixerState,
                mixerNotificationTitle = resContext.getString(R.string.notification_mixer_title),
                ambientAlbumTitle = resContext.getString(R.string.sounds_screen_title),
                musicFallbackAlbumTitle = resContext.getString(R.string.music_screen_title),
                plusAmbientFormat = resContext.getString(R.string.notification_plus_ambient)
            )

            if (!presentation.isActive) {
                val idleCommands = Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_STOP,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_METADATA,
                        Player.COMMAND_GET_TIMELINE
                    )
                    .build()

                return State.Builder()
                    .setAvailableCommands(idleCommands)
                    .setPlayWhenReady(false, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    .setPlaybackState(Player.STATE_IDLE)
                    .setPlaylist(emptyList())
                    .build()
            }

            if (presentation.isMixerPrimary) {
                val mixerMetadata = MediaMetadata.Builder()
                    .setTitle(presentation.title)
                    .setArtist(presentation.artist)
                    .setDisplayTitle(presentation.title)
                    .setSubtitle(presentation.artist)
                    .setAlbumTitle(presentation.albumTitle)
                    .build()

                val mixerItemData = MediaItemData.Builder("relaxa_mixer_ambient_session")
                    .setMediaMetadata(mixerMetadata)
                    .setDurationUs(C.TIME_UNSET)
                    .setIsSeekable(false)
                    .build()

                val mixerCommands = Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_STOP,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_METADATA,
                        Player.COMMAND_GET_TIMELINE,
                        Player.COMMAND_GET_VOLUME
                    )
                    .build()

                return State.Builder()
                    .setAvailableCommands(mixerCommands)
                    .setPlayWhenReady(
                        presentation.isPlaying,
                        PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                    )
                    .setPlaybackState(Player.STATE_READY)
                    .setPlaylist(listOf(mixerItemData))
                    .setCurrentMediaItemIndex(0)
                    .setContentPositionMs(PositionSupplier.getConstant(0L))
                    .setVolume(mixerState.masterVolume)
                    .build()
            }

            val musicMetadata = MediaMetadata.Builder()
                .setTitle(presentation.title)
                .setArtist(presentation.artist)
                .setDisplayTitle(presentation.title)
                .setSubtitle(presentation.artist)
                .setAlbumTitle(presentation.albumTitle)
                .build()

            val durationUs = if (presentation.durationMs > 0L) {
                presentation.durationMs * 1000L
            } else {
                C.TIME_UNSET
            }

            val trackId = musicState.currentTrack?.id ?: "relaxa_music_session"
            val musicItemData = MediaItemData.Builder(trackId)
                .setMediaMetadata(musicMetadata)
                .setDurationUs(durationUs)
                .setIsSeekable(presentation.isSeekable)
                .build()

            val musicCommandsBuilder = Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_METADATA,
                    Player.COMMAND_GET_TIMELINE,
                    Player.COMMAND_SET_REPEAT_MODE,
                    Player.COMMAND_SET_SHUFFLE_MODE,
                    Player.COMMAND_GET_VOLUME
                )

            if (presentation.canSkipNextOrPrevious) {
                musicCommandsBuilder.addAll(
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                )
            }

            val positionSupplier = if (musicState.isPlaying) {
                PositionSupplier.getExtrapolating(
                    progressState.currentPositionMs,
                    1.0f
                )
            } else {
                PositionSupplier.getConstant(progressState.currentPositionMs)
            }

            val mappedRepeatMode = when (musicState.repeatMode) {
                MusicRepeatMode.OFF -> Player.REPEAT_MODE_OFF
                MusicRepeatMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
                MusicRepeatMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            }

            return State.Builder()
                .setAvailableCommands(musicCommandsBuilder.build())
                .setPlayWhenReady(
                    presentation.isPlaying,
                    PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                )
                .setPlaybackState(
                    if (presentation.isBuffering) Player.STATE_BUFFERING else Player.STATE_READY
                )
                .setPlaylist(listOf(musicItemData))
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(positionSupplier)
                .setRepeatMode(mappedRepeatMode)
                .setShuffleModeEnabled(musicState.isShuffleEnabled)
                .setVolume(musicState.volume)
                .build()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            handleSystemPlayWhenReady(playWhenReady)
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        override fun handleStop(): ListenableFuture<*> {
            handleSystemPlayWhenReady(playWhenReady = false)
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(
            mediaItemIndex: Int,
            positionMs: Long,
            seekCommand: Int
        ): ListenableFuture<*> {
            when (seekCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    playerController.skipToNext()
                }

                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    playerController.skipToPrevious()
                }

                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM -> {
                    if (positionMs != C.TIME_UNSET && positionMs >= 0L) {
                        playerController.seekTo(positionMs)
                    }
                }

                Player.COMMAND_SEEK_TO_DEFAULT_POSITION -> {
                    playerController.seekTo(0L)
                }
            }
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
            val mapped = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> MusicRepeatMode.REPEAT_ONE
                Player.REPEAT_MODE_ALL -> MusicRepeatMode.REPEAT_ALL
                else -> MusicRepeatMode.OFF
            }
            playerController.setRepeatMode(mapped)
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
            playerController.setShuffleEnabled(shuffleModeEnabled)
            invalidateState()
            return Futures.immediateVoidFuture()
        }
    }

    companion object {
        const val MIXER_NOTIFICATION_TITLE = "Relaxa e Dorme — Sons ambientes"
        private const val DUCK_ATTENUATION_FACTOR = 0.25f
        private const val MAX_WAKE_LOCK_TIMEOUT_MS = 12L * 60L * 60L * 1000L // 12 horas

        @Volatile
        private var INSTANCE: AppAudioSessionCoordinator? = null

        fun getInstance(context: Context): AppAudioSessionCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppAudioSessionCoordinator(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        fun buildSessionPresentation(
            musicState: MusicPlaybackState,
            mixerState: AmbientMixerState,
            mixerNotificationTitle: String = MIXER_NOTIFICATION_TITLE,
            ambientAlbumTitle: String = "Sons ambientes",
            musicFallbackAlbumTitle: String = "Músicas",
            plusAmbientFormat: String = "%1\$s • + Sons ambientes"
        ): MediaSessionPresentation {
            val track = musicState.currentTrack
            val hasMixerSounds = mixerState.hasActiveSounds
            val isMusicPlaying = musicState.isPlaying
            val isMixerPlaying = mixerState.anyPlaying

            if (track == null && !hasMixerSounds) {
                return MediaSessionPresentation(
                    isActive = false,
                    isMixerPrimary = false,
                    title = "",
                    artist = "",
                    albumTitle = "",
                    isPlaying = false,
                    isBuffering = false,
                    canSkipNextOrPrevious = false,
                    isSeekable = false,
                    durationMs = 0L,
                    coverUrl = null
                )
            }

            val isMixerPrimary = track == null || (!isMusicPlaying && isMixerPlaying)

            if (isMixerPrimary) {
                val soundSummary = mixerState.activeLayers.joinToString(", ") { it.sound.name }
                return MediaSessionPresentation(
                    isActive = true,
                    isMixerPrimary = true,
                    title = mixerNotificationTitle,
                    artist = soundSummary,
                    albumTitle = ambientAlbumTitle,
                    isPlaying = isMixerPlaying,
                    isBuffering = mixerState.activeLayers.any { it.isBuffering },
                    canSkipNextOrPrevious = false,
                    isSeekable = false,
                    durationMs = 0L,
                    coverUrl = null
                )
            }

            val album = musicState.currentAlbum
            val artistSubtitle = if (isMixerPlaying) {
                String.format(plusAmbientFormat, track!!.artist)
            } else {
                track!!.artist
            }
            val effectiveDuration = musicState.effectiveDurationMs

            return MediaSessionPresentation(
                isActive = true,
                isMixerPrimary = false,
                title = track.title,
                artist = artistSubtitle,
                albumTitle = album?.title ?: musicFallbackAlbumTitle,
                isPlaying = isMusicPlaying || isMixerPlaying,
                isBuffering = musicState.isBuffering,
                canSkipNextOrPrevious = musicState.queue.isNotEmpty(),
                isSeekable = effectiveDuration > 0L,
                durationMs = effectiveDuration,
                coverUrl = track.coverUrl ?: album?.coverUrl
            )
        }
    }
}
