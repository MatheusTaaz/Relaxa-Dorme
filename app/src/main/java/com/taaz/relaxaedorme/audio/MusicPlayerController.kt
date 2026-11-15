package com.taaz.relaxaedorme.audio

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.taaz.relaxaedorme.R
import com.taaz.relaxaedorme.data.model.MusicTrack
import com.taaz.relaxaedorme.data.offline.MusicOfflineStorageManager
import com.taaz.relaxaedorme.data.remote.CloudflareR2Config
import com.taaz.relaxaedorme.data.repository.CatalogRepository
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Controlador centralizado do player de Músicas baseado no Media3 [ExoPlayer].
 *
 * Mantém uma única instância compartilhada do [ExoPlayer] durante o ciclo de vida da sessão,
 * expondo:
 * - [playbackState]: estado estrutural (faixa, álbum, fila, play/pause, shuffle, volume, modo de repetição, erro)
 * - [progressState]: estado de progresso temporal (posição e duração), isolado para evitar
 *   recomposição das telas principais a cada tick de 250 ms.
 */
class MusicPlayerController(
    context: Context,
    private val catalogRepository: CatalogRepository,
    private val scope: CoroutineScope,
    private val offlineStorageManager: MusicOfflineStorageManager = MusicOfflineStorageManager(context.applicationContext),
    private val random: Random = Random.Default,
    private val onTrackStarted: (String) -> Unit = {},
    private val localizedContextProvider: () -> Context = { context.applicationContext }
) {
    private val appContext = context.applicationContext

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ false
        )
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    private val _playbackState = MutableStateFlow(MusicPlaybackState())
    val playbackState: StateFlow<MusicPlaybackState> = _playbackState.asStateFlow()

    private val _progressState = MutableStateFlow(MusicProgressState())
    val progressState: StateFlow<MusicProgressState> = _progressState.asStateFlow()

    private var positionTickerJob: Job? = null
    private var volumeRampJob: Job? = null
    private var lastNonZeroVolume: Float = MusicTrack.DEFAULT_MUSIC_VOLUME
    private var outputAttenuation: Float = 1.0f
    private var lastRecordedHistoryTrackId: String? = null
    private var isReshufflingSequence: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (isReshufflingSequence) return
            val trackId = mediaItem?.mediaId ?: return
            val currentQueue = _playbackState.value.queue
            val matchedTrack = currentQueue.firstOrNull { it.id == trackId }
                ?: catalogRepository.getMusicTrackById(trackId)
                ?: return
            val matchedAlbum = catalogRepository.getMusicAlbumById(matchedTrack.albumId)
            val newIndex = exoPlayer.currentMediaItemIndex.takeIf { it >= 0 }
                ?: currentQueue.indexOfFirst { it.id == trackId }

            val reportedDuration = exoPlayer.duration.takeIf { it > 0L && it != C.TIME_UNSET }
                ?: matchedTrack.durationMs
                ?: 0L
            val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)

            _playbackState.update { state ->
                state.copy(
                    currentTrack = matchedTrack,
                    currentAlbum = matchedAlbum,
                    currentQueueIndex = newIndex,
                    durationMs = reportedDuration,
                    errorMessage = null
                )
            }
            _progressState.value = MusicProgressState(
                currentPositionMs = currentPos,
                durationMs = reportedDuration
            )

            val isRepeat = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            recordTrackStartedIfNeeded(matchedTrack.id, isRepeatTransition = isRepeat)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (isReshufflingSequence) return

            val stateSnapshot = _playbackState.value
            if (
                playbackState == Player.STATE_ENDED &&
                stateSnapshot.isShuffleEnabled &&
                stateSnapshot.repeatMode == MusicRepeatMode.REPEAT_ALL &&
                stateSnapshot.originalQueue.isNotEmpty()
            ) {
                startNextShuffledSequence(autoPlay = true)
                return
            }

            val isBuffering = playbackState == Player.STATE_BUFFERING
            val reportedDuration = exoPlayer.duration.takeIf { it > 0L && it != C.TIME_UNSET }
            val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val resolvedDuration = reportedDuration
                ?: stateSnapshot.currentTrack?.durationMs
                ?: stateSnapshot.durationMs

            _playbackState.update { state ->
                state.copy(
                    isBuffering = isBuffering,
                    durationMs = resolvedDuration,
                    isPlaying = exoPlayer.isPlaying
                )
            }
            _progressState.value = MusicProgressState(
                currentPositionMs = if (playbackState == Player.STATE_ENDED) 0L else currentPos,
                durationMs = resolvedDuration
            )
            updatePositionTicker()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isReshufflingSequence) return
            _playbackState.update { state ->
                state.copy(isPlaying = isPlaying)
            }
            _progressState.update { progress ->
                progress.copy(currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L))
            }
            updatePositionTicker()
        }

        override fun onPlayerError(error: PlaybackException) {
            val activeTrack = _playbackState.value.currentTrack
            val currentTitle = activeTrack?.title ?: ""
            val isOfflineAvailable = activeTrack?.let { offlineStorageManager.isTrackAvailableOffline(it) } ?: true
            val resContext = localizedContextProvider()
            val friendlyMessage = if (!isOfflineAvailable) {
                resContext.getString(R.string.player_error_no_connection, currentTitle)
            } else {
                resContext.getString(R.string.player_error_generic, currentTitle)
            }
            _playbackState.update { state ->
                state.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = friendlyMessage
                )
            }
            updatePositionTicker()
        }
    }

    init {
        exoPlayer.addListener(playerListener)
        exoPlayer.volume = MusicTrack.DEFAULT_MUSIC_VOLUME
        syncExoPlayerRepeatMode(
            repeatMode = MusicRepeatMode.REPEAT_ALL,
            isShuffleEnabled = false
        )
    }

    private fun recordTrackStartedIfNeeded(trackId: String, isRepeatTransition: Boolean) {
        if (
            MusicPlaybackState.shouldRecordRecentOnTransition(
                newTrackId = trackId,
                lastRecordedTrackId = lastRecordedHistoryTrackId,
                isRepeatTransition = isRepeatTransition
            )
        ) {
            lastRecordedHistoryTrackId = trackId
            onTrackStarted(trackId)
        }
    }

    private fun buildMediaItem(item: MusicTrack): MediaItem {
        val resolvedUriString = offlineStorageManager.resolvePlaybackSource(item)?.uriString
            ?: item.localAssetUri
            ?: CloudflareR2Config.buildPublicUrl(item.remoteAudioPath)
            ?: ""
        return MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(resolvedUriString.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artist)
                    .build()
            )
            .build()
    }

    private fun buildMediaItemWithoutDownloadedFile(item: MusicTrack): MediaItem {
        val fallbackUriString = offlineStorageManager.resolvePlaybackSourceWithoutDownloadedFile(item)?.uriString
            ?: item.localAssetUri
            ?: CloudflareR2Config.buildPublicUrl(item.remoteAudioPath)
            ?: ""
        return MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(fallbackUriString.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artist)
                    .build()
            )
            .build()
    }

    /**
     * Atualiza o [MediaItem] de uma faixa na fila caso ela tenha acabado de ser baixada
     * para o armazenamento offline enquanto não era a faixa ativamente em execução.
     */
    fun refreshQueuedMediaItemUriIfDownloaded(track: MusicTrack) {
        val queue = _playbackState.value.queue
        if (queue.isEmpty() || exoPlayer.mediaItemCount == 0) return
        val indexInQueue = queue.indexOfFirst { it.id == track.id }
        if (indexInQueue < 0 || indexInQueue >= exoPlayer.mediaItemCount) return
        if (indexInQueue == exoPlayer.currentMediaItemIndex && exoPlayer.isPlaying) return

        exoPlayer.replaceMediaItem(indexInQueue, buildMediaItem(track))
    }

    /**
     * Prepara o [ExoPlayer] antes da exclusão do arquivo offline de uma faixa,
     * garantindo que nenhum descritor de arquivo local permaneça em uso e fazendo a
     * transição segura para asset embarcado ou streaming remoto na posição atual.
     */
    fun prepareForTrackOfflineDeletion(track: MusicTrack) {
        val queue = _playbackState.value.queue
        if (queue.isEmpty() || exoPlayer.mediaItemCount == 0) return
        val indexInQueue = queue.indexOfFirst { it.id == track.id }
        if (indexInQueue < 0 || indexInQueue >= exoPlayer.mediaItemCount) return

        val fallbackItem = buildMediaItemWithoutDownloadedFile(track)
        if (indexInQueue == exoPlayer.currentMediaItemIndex) {
            val savedPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val wasPlaying = exoPlayer.isPlaying
            val hasFallbackSource = offlineStorageManager.resolvePlaybackSourceWithoutDownloadedFile(track) != null
            if (hasFallbackSource) {
                isReshufflingSequence = true
                try {
                    exoPlayer.replaceMediaItem(indexInQueue, fallbackItem)
                    if (exoPlayer.currentPosition < savedPositionMs && savedPositionMs > 0L) {
                        exoPlayer.seekTo(indexInQueue, savedPositionMs)
                    }
                    if (wasPlaying) {
                        exoPlayer.play()
                    }
                } finally {
                    isReshufflingSequence = false
                }
            } else {
                exoPlayer.pause()
                exoPlayer.stop()
            }
        } else {
            exoPlayer.replaceMediaItem(indexInQueue, fallbackItem)
        }
    }

    private fun syncExoPlayerRepeatMode(
        repeatMode: MusicRepeatMode,
        isShuffleEnabled: Boolean
    ) {
        exoPlayer.repeatMode = when (repeatMode) {
            MusicRepeatMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            MusicRepeatMode.REPEAT_ALL -> {
                if (isShuffleEnabled) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
            }
            MusicRepeatMode.OFF -> Player.REPEAT_MODE_OFF
        }
    }

    fun playTrack(
        track: MusicTrack,
        queue: List<MusicTrack> = listOf(track),
        queueTitle: String = "",
        forceRestart: Boolean = false
    ) {
        val currentState = _playbackState.value
        val baseQueue = if (queue.isNotEmpty() && queue.any { it.id == track.id }) {
            queue
        } else {
            listOf(track)
        }

        val isSameActiveTrack = currentState.currentTrack?.id == track.id &&
            exoPlayer.mediaItemCount > 0
        val isSameQueueContext = currentState.originalQueue.map { it.id } == baseQueue.map { it.id }

        if (isSameActiveTrack && isSameQueueContext && !forceRestart) {
            if (currentState.errorMessage != null || exoPlayer.playbackState == Player.STATE_IDLE) {
                play()
                return
            }

            togglePlayPause()
            return
        }

        val effectiveQueue = if (currentState.isShuffleEnabled) {
            MusicPlaybackState.generateShuffledQueue(
                tracks = baseQueue,
                lockedFirstTrack = track,
                random = random
            )
        } else {
            baseQueue
        }

        val startIndex = effectiveQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        loadAndPlaySequence(
            trackToPlay = track,
            effectiveQueue = effectiveQueue,
            originalQueue = baseQueue,
            startIndex = startIndex,
            queueTitle = queueTitle,
            autoPlay = true
        )
    }

    fun playQueue(queue: List<MusicTrack>, queueTitle: String) {
        if (queue.isEmpty()) return
        val currentState = _playbackState.value
        val effectiveQueue = if (currentState.isShuffleEnabled) {
            MusicPlaybackState.generateShuffledQueue(
                tracks = queue,
                avoidFirstTrackId = currentState.currentTrack?.id,
                random = random
            )
        } else {
            queue
        }
        val firstTrack = effectiveQueue.firstOrNull() ?: return
        loadAndPlaySequence(
            trackToPlay = firstTrack,
            effectiveQueue = effectiveQueue,
            originalQueue = queue,
            startIndex = 0,
            queueTitle = queueTitle,
            autoPlay = true
        )
    }

    fun playShuffledQueue(queue: List<MusicTrack>, queueTitle: String) {
        if (queue.isEmpty()) return
        val currentState = _playbackState.value
        val effectiveQueue = MusicPlaybackState.generateShuffledQueue(
            tracks = queue,
            avoidFirstTrackId = currentState.currentTrack?.id,
            random = random
        )
        val firstTrack = effectiveQueue.firstOrNull() ?: return
        _playbackState.update { it.copy(isShuffleEnabled = true) }
        loadAndPlaySequence(
            trackToPlay = firstTrack,
            effectiveQueue = effectiveQueue,
            originalQueue = queue,
            startIndex = 0,
            queueTitle = queueTitle,
            autoPlay = true
        )
    }

    private fun loadAndPlaySequence(
        trackToPlay: MusicTrack,
        effectiveQueue: List<MusicTrack>,
        originalQueue: List<MusicTrack>,
        startIndex: Int,
        queueTitle: String,
        autoPlay: Boolean
    ) {
        val resolvedPrimarySource = offlineStorageManager.resolvePlaybackSource(trackToPlay)
        val mediaItems = effectiveQueue.map(::buildMediaItem)
        val album = catalogRepository.getMusicAlbumById(trackToPlay.albumId)
        val initialDuration = trackToPlay.durationMs ?: 0L

        if (resolvedPrimarySource == null) {
            _playbackState.update { state ->
                state.copy(
                    currentTrack = trackToPlay,
                    currentAlbum = album,
                    queue = effectiveQueue,
                    originalQueue = originalQueue,
                    currentQueueIndex = startIndex,
                    queueTitle = queueTitle.ifBlank { album?.title ?: state.queueTitle.ifBlank { "Biblioteca" } },
                    durationMs = initialDuration,
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = localizedContextProvider().getString(
                        R.string.player_error_offline,
                        trackToPlay.title
                    )
                )
            }
            return
        }

        isReshufflingSequence = true
        try {
            _playbackState.update { state ->
                state.copy(
                    currentTrack = trackToPlay,
                    currentAlbum = album,
                    queue = effectiveQueue,
                    originalQueue = originalQueue,
                    currentQueueIndex = startIndex,
                    queueTitle = queueTitle.ifBlank { album?.title ?: state.queueTitle.ifBlank { "Biblioteca" } },
                    durationMs = initialDuration,
                    errorMessage = null
                )
            }
            _progressState.value = MusicProgressState(
                currentPositionMs = 0L,
                durationMs = initialDuration
            )

            syncExoPlayerRepeatMode(
                repeatMode = _playbackState.value.repeatMode,
                isShuffleEnabled = _playbackState.value.isShuffleEnabled
            )
            exoPlayer.setMediaItems(mediaItems, startIndex, 0L)
            exoPlayer.prepare()
            if (autoPlay) {
                exoPlayer.play()
                recordTrackStartedIfNeeded(trackToPlay.id, isRepeatTransition = false)
            } else {
                exoPlayer.pause()
            }
        } catch (_: Exception) {
            _playbackState.update { state ->
                state.copy(
                    isPlaying = false,
                    errorMessage = localizedContextProvider().getString(
                        R.string.player_error_loading_audio,
                        trackToPlay.title
                    )
                )
            }
        } finally {
            isReshufflingSequence = false
        }
    }

    private fun startNextShuffledSequence(autoPlay: Boolean) {
        val currentState = _playbackState.value
        val baseQueue = currentState.originalQueue.ifEmpty { currentState.queue }
        if (baseQueue.isEmpty()) return

        val lastTrackId = currentState.currentTrack?.id ?: currentState.queue.lastOrNull()?.id
        val nextShuffledQueue = MusicPlaybackState.generateShuffledQueue(
            tracks = baseQueue,
            avoidFirstTrackId = lastTrackId,
            random = random
        )
        val firstTrack = nextShuffledQueue.firstOrNull() ?: return

        loadAndPlaySequence(
            trackToPlay = firstTrack,
            effectiveQueue = nextShuffledQueue,
            originalQueue = baseQueue,
            startIndex = 0,
            queueTitle = currentState.queueTitle,
            autoPlay = autoPlay
        )
    }

    fun play() {
        val currentState = _playbackState.value
        if (exoPlayer.mediaItemCount == 0) {
            val fallbackTrack = currentState.currentTrack
                ?: catalogRepository.allMusicTracks.firstOrNull()
                ?: return
            val fallbackQueue = currentState.originalQueue
                .ifEmpty { currentState.queue }
                .ifEmpty { catalogRepository.allMusicTracks }
            playTrack(
                track = fallbackTrack,
                queue = fallbackQueue,
                queueTitle = currentState.queueTitle.ifBlank { "Todas as Músicas" },
                forceRestart = true
            )
            return
        }

        _playbackState.update { it.copy(errorMessage = null) }

        when (exoPlayer.playbackState) {
            Player.STATE_IDLE -> {
                exoPlayer.prepare()
            }
            Player.STATE_ENDED -> {
                if (currentState.isShuffleEnabled && currentState.originalQueue.size > 1) {
                    startNextShuffledSequence(autoPlay = true)
                    return
                }
                exoPlayer.seekTo(0, 0L)
                _progressState.update { it.copy(currentPositionMs = 0L) }
            }
            Player.STATE_BUFFERING,
            Player.STATE_READY -> Unit
        }
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun toggleShuffle() {
        setShuffleEnabled(!_playbackState.value.isShuffleEnabled)
    }

    fun setShuffleEnabled(enabled: Boolean) {
        val currentState = _playbackState.value
        if (currentState.isShuffleEnabled == enabled) return

        val baseQueue = currentState.originalQueue.ifEmpty { currentState.queue }
        val activeTrack = currentState.currentTrack

        if (baseQueue.isEmpty() || activeTrack == null || exoPlayer.mediaItemCount == 0) {
            _playbackState.update { it.copy(isShuffleEnabled = enabled) }
            syncExoPlayerRepeatMode(
                repeatMode = currentState.repeatMode,
                isShuffleEnabled = enabled
            )
            return
        }

        val updatedQueue = if (enabled) {
            MusicPlaybackState.generateShuffledQueue(
                tracks = baseQueue,
                lockedFirstTrack = activeTrack,
                random = random
            )
        } else {
            baseQueue
        }

        val targetIndexInUpdatedQueue = updatedQueue
            .indexOfFirst { it.id == activeTrack.id }
            .coerceAtLeast(0)

        isReshufflingSequence = true
        try {
            val currentExoIndex = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
            val currentExoCount = exoPlayer.mediaItemCount

            if (currentExoIndex + 1 < currentExoCount) {
                exoPlayer.removeMediaItems(currentExoIndex + 1, currentExoCount)
            }
            if (currentExoIndex > 0) {
                exoPlayer.removeMediaItems(0, currentExoIndex)
            }

            if (targetIndexInUpdatedQueue > 0) {
                val itemsBefore = updatedQueue.subList(0, targetIndexInUpdatedQueue).map(::buildMediaItem)
                exoPlayer.addMediaItems(0, itemsBefore)
            }
            if (targetIndexInUpdatedQueue + 1 < updatedQueue.size) {
                val itemsAfter = updatedQueue.subList(targetIndexInUpdatedQueue + 1, updatedQueue.size).map(::buildMediaItem)
                exoPlayer.addMediaItems(targetIndexInUpdatedQueue + 1, itemsAfter)
            }

            syncExoPlayerRepeatMode(
                repeatMode = currentState.repeatMode,
                isShuffleEnabled = enabled
            )

            _playbackState.update { state ->
                state.copy(
                    queue = updatedQueue,
                    originalQueue = baseQueue,
                    currentQueueIndex = targetIndexInUpdatedQueue,
                    isShuffleEnabled = enabled
                )
            }
        } finally {
            isReshufflingSequence = false
        }
    }

    fun skipToNext() {
        if (exoPlayer.mediaItemCount == 0) return
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        }
        val state = _playbackState.value

        if (state.repeatMode == MusicRepeatMode.REPEAT_ONE) {
            exoPlayer.seekTo(0L)
            _progressState.update { it.copy(currentPositionMs = 0L) }
            exoPlayer.play()
            return
        }

        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.play()
        } else if (state.isShuffleEnabled && state.originalQueue.size > 1) {
            startNextShuffledSequence(autoPlay = true)
        } else if (exoPlayer.mediaItemCount > 1) {
            exoPlayer.seekTo(0, 0L)
            exoPlayer.play()
        } else {
            exoPlayer.seekTo(0L)
            _progressState.update { it.copy(currentPositionMs = 0L) }
            exoPlayer.play()
        }
    }

    fun skipToPrevious() {
        if (exoPlayer.mediaItemCount == 0) return
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        }
        val state = _playbackState.value
        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)

        val shouldRestart = MusicPlaybackState.shouldRestartCurrentTrackOnPrevious(
            positionMs = currentPos,
            currentQueueIndex = state.currentQueueIndex,
            queueSize = state.queue.size,
            repeatMode = state.repeatMode
        )

        if (shouldRestart) {
            exoPlayer.seekTo(0L)
            _progressState.update { it.copy(currentPositionMs = 0L) }
        } else if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
            exoPlayer.play()
        } else if (state.queue.size > 1 && state.repeatMode == MusicRepeatMode.REPEAT_ALL) {
            exoPlayer.seekTo(state.queue.lastIndex, 0L)
            exoPlayer.play()
        } else {
            exoPlayer.seekTo(0L)
            _progressState.update { it.copy(currentPositionMs = 0L) }
        }
    }

    fun seekTo(positionMs: Long) {
        if (exoPlayer.mediaItemCount == 0) return
        val maxDuration = _playbackState.value.effectiveDurationMs
        val targetMs = if (maxDuration > 0L) {
            positionMs.coerceIn(0L, maxDuration)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        exoPlayer.seekTo(targetMs)
        _progressState.update {
            it.copy(
                currentPositionMs = targetMs,
                durationMs = maxDuration.takeIf { d -> d > 0L } ?: it.durationMs
            )
        }
    }

    fun seekToFraction(fraction: Float) {
        val total = _playbackState.value.effectiveDurationMs
        if (total <= 0L) return
        val targetMs = (fraction.coerceIn(0f, 1f) * total).toLong()
        seekTo(targetMs)
    }

    fun setVolume(targetVolume: Float, smoothTransition: Boolean = false) {
        val clamped = targetVolume.coerceIn(0f, 1f)
        if (clamped > 0.01f) {
            lastNonZeroVolume = clamped
        }
        volumeRampJob?.cancel()

        _playbackState.update { it.copy(volume = clamped) }

        val effectiveTarget = (clamped * outputAttenuation).coerceIn(0f, 1f)
        val currentVolume = exoPlayer.volume
        if (!smoothTransition || kotlin.math.abs(effectiveTarget - currentVolume) < 0.15f) {
            exoPlayer.volume = effectiveTarget
            return
        }

        volumeRampJob = scope.launch {
            val steps = 6
            val delta = (effectiveTarget - currentVolume) / steps
            for (step in 1..steps) {
                exoPlayer.volume = (currentVolume + delta * step).coerceIn(0f, 1f)
                delay(20L)
            }
            exoPlayer.volume = effectiveTarget
        }
    }

    fun setOutputAttenuation(factor: Float) {
        outputAttenuation = factor.coerceIn(0f, 1f)
        volumeRampJob?.cancel()
        exoPlayer.volume = (_playbackState.value.volume * outputAttenuation).coerceIn(0f, 1f)
    }

    fun toggleMute() {
        val currentVol = _playbackState.value.volume
        if (currentVol > 0.01f) {
            lastNonZeroVolume = currentVol
            setVolume(0f, smoothTransition = true)
        } else {
            val restoredVolume = lastNonZeroVolume.takeIf { it > 0.01f }
                ?: MusicTrack.DEFAULT_MUSIC_VOLUME
            setVolume(restoredVolume, smoothTransition = true)
        }
    }

    fun cycleRepeatMode() {
        val nextMode = _playbackState.value.repeatMode.nextMode()
        setRepeatMode(nextMode)
    }

    fun setRepeatMode(mode: MusicRepeatMode) {
        syncExoPlayerRepeatMode(
            repeatMode = mode,
            isShuffleEnabled = _playbackState.value.isShuffleEnabled
        )
        _playbackState.update { it.copy(repeatMode = mode) }
    }

    fun clearError() {
        _playbackState.update { it.copy(errorMessage = null) }
    }

    private fun updatePositionTicker() {
        if (exoPlayer.isPlaying) {
            if (positionTickerJob?.isActive == true) return
            positionTickerJob = scope.launch {
                while (isActive && exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration.takeIf { it > 0L && it != C.TIME_UNSET }
                        ?: _playbackState.value.effectiveDurationMs
                    _progressState.update { current ->
                        if (current.currentPositionMs == pos && current.durationMs == dur) {
                            current
                        } else {
                            MusicProgressState(currentPositionMs = pos, durationMs = dur)
                        }
                    }
                    delay(250L)
                }
            }
        } else {
            positionTickerJob?.cancel()
            positionTickerJob = null
        }
    }

    fun release() {
        positionTickerJob?.cancel()
        volumeRampJob?.cancel()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}
