package com.taaz.relaxaedorme.audio

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.taaz.relaxaedorme.data.model.AmbientSound
import com.taaz.relaxaedorme.data.model.SavedScene
import com.taaz.relaxaedorme.data.model.SoundIntervalConfig
import com.taaz.relaxaedorme.data.model.SoundIntervalMode
import com.taaz.relaxaedorme.data.repository.CatalogRepository
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Contrato de um player individual de camada de som ambiente.
 *
 * Em produção utiliza [ExoPlayerAmbientLayerPlayer] (Media3 ExoPlayer sem disputa de AudioFocus),
 * permitindo alternar dinamicamente entre loop infinito (`REPEAT_MODE_ONE`) e disparo único
 * com intervalo programado (`REPEAT_MODE_OFF`), além de injeção de dublês em testes unitários.
 */
interface AmbientLayerPlayer {
    val isPlaying: Boolean
    val isLoopingInfinitely: Boolean
    val appliedVolume: Float
    val isReleased: Boolean

    fun prepareAndPlay(assetUri: String, initialVolume: Float, loopInfinitely: Boolean = true)
    fun play()
    fun pause()
    fun setVolume(volume: Float)
    fun setLooping(loopInfinitely: Boolean)
    fun release()
}

/**
 * Callback de eventos de ciclo de vida/reprodução de uma camada individual.
 */
interface AmbientLayerPlayerListener {
    fun onPlayingStateChanged(isPlaying: Boolean, isBuffering: Boolean)
    fun onPlaybackEnded() {}
    fun onLayerError(message: String)
}

/**
 * Fábrica para criação de instâncias de [AmbientLayerPlayer] sob demanda.
 */
fun interface AmbientLayerPlayerFactory {
    fun create(
        sound: AmbientSound,
        listener: AmbientLayerPlayerListener
    ): AmbientLayerPlayer
}

/**
 * Implementação oficial de [AmbientLayerPlayer] baseada no Media3 [ExoPlayer].
 *
 * Características essenciais:
 * - `handleAudioFocus = false`: permite que até 8 camadas de sons ambientes toquem
 *   simultaneamente entre si e em paralelo ao [MusicPlayerController] sem interromper a música.
 * - Suporta tanto `Player.REPEAT_MODE_ONE` (contínuo) quanto `Player.REPEAT_MODE_OFF`
 *   (para efeitos pontuais com temporizador de intervalo independente).
 */
class ExoPlayerAmbientLayerPlayer(
    context: Context,
    private val sound: AmbientSound,
    private val listener: AmbientLayerPlayerListener
) : AmbientLayerPlayer {

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ false
        )
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    private var released = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (released) return
            if (playbackState == Player.STATE_ENDED) {
                listener.onPlaybackEnded()
                return
            }
            val buffering = playbackState == Player.STATE_BUFFERING
            listener.onPlayingStateChanged(
                isPlaying = exoPlayer.isPlaying,
                isBuffering = buffering
            )
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (released) return
            if (exoPlayer.playbackState == Player.STATE_ENDED && !isPlaying) {
                return
            }
            val buffering = exoPlayer.playbackState == Player.STATE_BUFFERING
            listener.onPlayingStateChanged(
                isPlaying = isPlaying,
                isBuffering = buffering
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            if (released) return
            listener.onLayerError(
                "Não foi possível reproduzir o som \"${sound.name}\"."
            )
        }
    }

    init {
        exoPlayer.addListener(playerListener)
        exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
    }

    override val isPlaying: Boolean
        get() = !released && exoPlayer.isPlaying

    override val isLoopingInfinitely: Boolean
        get() = !released && exoPlayer.repeatMode == Player.REPEAT_MODE_ONE

    override val appliedVolume: Float
        get() = if (released) 0f else exoPlayer.volume

    override val isReleased: Boolean
        get() = released

    override fun prepareAndPlay(
        assetUri: String,
        initialVolume: Float,
        loopInfinitely: Boolean
    ) {
        if (released) return
        try {
            exoPlayer.repeatMode = if (loopInfinitely) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
            exoPlayer.volume = initialVolume.coerceIn(0f, 1f)
            val mediaItem = MediaItem.Builder()
                .setMediaId(sound.id)
                .setUri(assetUri.toUri())
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (_: Exception) {
            listener.onLayerError(
                "Erro ao carregar o arquivo de som \"${sound.name}\"."
            )
        }
    }

    override fun play() {
        if (released) return
        try {
            when (exoPlayer.playbackState) {
                Player.STATE_IDLE -> exoPlayer.prepare()
                Player.STATE_ENDED -> exoPlayer.seekTo(0L)
                Player.STATE_BUFFERING,
                Player.STATE_READY -> Unit
            }
            exoPlayer.play()
        } catch (_: Exception) {
            listener.onLayerError(
                "Erro ao retomar o som \"${sound.name}\"."
            )
        }
    }

    override fun pause() {
        if (released) return
        exoPlayer.pause()
    }

    override fun setVolume(volume: Float) {
        if (released) return
        exoPlayer.volume = volume.coerceIn(0f, 1f)
    }

    override fun setLooping(loopInfinitely: Boolean) {
        if (released) return
        exoPlayer.repeatMode = if (loopInfinitely) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    override fun release() {
        if (released) return
        released = true
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}

/**
 * Controlador independente do Mixer de Sons Ambientes.
 *
 * Gerencia até [AmbientMixerState.MAX_ACTIVE_SOUNDS] (8) camadas simultâneas,
 * cada qual com seu próprio player, volume individual (`0f..1f`), play/pause independente,
 * temporizador individual de intervalo (quando compatível) e volume mestre proporcional.
 *
 * Não possui nenhuma dependência ou acoplamento com [MusicPlayerController].
 */
class AmbientSoundMixerController(
    private val catalogRepository: CatalogRepository,
    private val playerFactory: AmbientLayerPlayerFactory,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    private val ownsScope: Boolean = true,
    private val random: Random = Random.Default,
    private val intervalDelayProvider: (SoundIntervalConfig, Random) -> Long = { config, rng ->
        config.nextRandomDelayMillis(rng)
    }
) {
    constructor(
        context: Context,
        catalogRepository: CatalogRepository,
        scope: CoroutineScope
    ) : this(
        catalogRepository = catalogRepository,
        playerFactory = AmbientLayerPlayerFactory { sound, listener ->
            ExoPlayerAmbientLayerPlayer(
                context = context.applicationContext,
                sound = sound,
                listener = listener
            )
        },
        scope = scope,
        ownsScope = false
    )

    private val activePlayers = LinkedHashMap<String, AmbientLayerPlayer>()
    private val intervalTimerJobs = HashMap<String, Job>()
    private var outputAttenuation: Float = 1.0f

    private val _mixerState = MutableStateFlow(AmbientMixerState())
    val mixerState: StateFlow<AmbientMixerState> = _mixerState.asStateFlow()

    /**
     * Adiciona um som ambiente ao Mixer e inicia imediatamente sua reprodução.
     */
    fun addSound(
        sound: AmbientSound,
        initialVolume: Float = sound.defaultVolume,
        initialIntervalConfig: SoundIntervalConfig = SoundIntervalConfig()
    ): Boolean {
        val existingPlayer = activePlayers[sound.id]
        if (existingPlayer != null) {
            val existingLayer = _mixerState.value.getLayer(sound.id)
            if (existingLayer != null && (!existingLayer.isPlaying || existingLayer.errorMessage != null)) {
                updateLayerState(sound.id) {
                    it.copy(
                        isPlaying = true,
                        isWaitingForNextInterval = false,
                        errorMessage = null
                    )
                }
                existingPlayer.play()
            }
            _mixerState.update {
                it.copy(statusMessage = "\"${sound.name}\" já está ativo no Mixer.")
            }
            return true
        }

        if (activePlayers.size >= AmbientMixerState.MAX_ACTIVE_SOUNDS) {
            _mixerState.update {
                it.copy(
                    statusMessage = "Limite máximo de ${AmbientMixerState.MAX_ACTIVE_SOUNDS} sons simultâneos atingido."
                )
            }
            return false
        }

        if (sound.audioAssetPath.isBlank()) {
            _mixerState.update {
                it.copy(
                    statusMessage = "Arquivo de áudio inválido ou ausente para \"${sound.name}\"."
                )
            }
            return false
        }

        val resolvedIntervalConfig = sanitizeIntervalConfigForSound(sound, initialIntervalConfig)
        val shouldLoopInfinitely = resolvedIntervalConfig.mode == SoundIntervalMode.CONTINUOUS
        val clampedVolume = initialVolume.coerceIn(0f, 1f)
        val currentMaster = _mixerState.value.masterVolume
        val effectiveVolume = (
            AmbientMixerState.calculateEffectiveVolume(
                layerVolume = clampedVolume,
                masterVolume = currentMaster
            ) * outputAttenuation
            ).coerceIn(0f, 1f)
        val categoryName = catalogRepository.getSoundCategoryById(sound.categoryId)?.name ?: ""

        val newLayer = ActiveAmbientLayer(
            sound = sound,
            categoryName = categoryName,
            volume = clampedVolume,
            isPlaying = true,
            isBuffering = false,
            isInfiniteLoopEnabled = shouldLoopInfinitely,
            intervalConfig = resolvedIntervalConfig,
            isWaitingForNextInterval = false,
            errorMessage = null
        )

        val listener = object : AmbientLayerPlayerListener {
            override fun onPlayingStateChanged(isPlaying: Boolean, isBuffering: Boolean) {
                if (!activePlayers.containsKey(sound.id)) return
                val currentLayer = _mixerState.value.getLayer(sound.id) ?: return
                // Enquanto aguarda o próximo intervalo agendado, a camada permanece ativa (isPlaying = true)
                if (currentLayer.isWaitingForNextInterval && !isPlaying) {
                    return
                }
                updateLayerState(sound.id) { layer ->
                    layer.copy(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering
                    )
                }
            }

            override fun onPlaybackEnded() {
                if (!activePlayers.containsKey(sound.id)) return
                handleLayerPlaybackEnded(sound.id)
            }

            override fun onLayerError(message: String) {
                if (!activePlayers.containsKey(sound.id)) return
                cancelIntervalTimer(sound.id)
                updateLayerState(sound.id) { layer ->
                    layer.copy(
                        isPlaying = false,
                        isBuffering = false,
                        isWaitingForNextInterval = false,
                        errorMessage = message
                    )
                }
                _mixerState.update { state ->
                    state.copy(statusMessage = message)
                }
            }
        }

        cancelIntervalTimer(sound.id)
        val player = playerFactory.create(sound, listener)
        activePlayers[sound.id] = player

        _mixerState.update { state ->
            state.copy(
                activeLayers = state.activeLayers + newLayer,
                statusMessage = null
            )
        }

        player.prepareAndPlay(
            assetUri = sound.assetUri,
            initialVolume = effectiveVolume,
            loopInfinitely = shouldLoopInfinitely
        )
        return true
    }

    /**
     * Remove uma camada ativa do Mixer, cancela imediatamente seu temporizador de intervalo
     * e libera o respectivo player.
     */
    fun removeSound(soundId: String) {
        cancelIntervalTimer(soundId)
        val removedPlayer = activePlayers.remove(soundId)
        removedPlayer?.release()

        _mixerState.update { state ->
            state.copy(
                activeLayers = state.activeLayers.filterNot { it.sound.id == soundId },
                statusMessage = null
            )
        }
    }

    /**
     * Alterna entre Play e Pause para uma camada específica, cancelando qualquer temporizador
     * pendente ao pausar.
     */
    fun toggleLayerPlayPause(soundId: String) {
        val layer = _mixerState.value.getLayer(soundId) ?: return
        val player = activePlayers[soundId] ?: return

        if (layer.isPlaying) {
            cancelIntervalTimer(soundId)
            updateLayerState(soundId) {
                it.copy(isPlaying = false, isWaitingForNextInterval = false)
            }
            player.pause()
        } else {
            cancelIntervalTimer(soundId)
            updateLayerState(soundId) {
                it.copy(isPlaying = true, isWaitingForNextInterval = false, errorMessage = null)
            }
            player.play()
        }
    }

    /**
     * Ajusta o volume individual (`0f` a `1f`) de uma camada específica, recalculando o volume
     * efetivo com base no [AmbientMixerState.masterVolume].
     */
    fun setLayerVolume(soundId: String, volume: Float) {
        val player = activePlayers[soundId] ?: return
        val clampedVolume = volume.coerceIn(0f, 1f)
        val masterVolume = _mixerState.value.masterVolume
        val effectiveVolume = (
            AmbientMixerState.calculateEffectiveVolume(
                layerVolume = clampedVolume,
                masterVolume = masterVolume
            ) * outputAttenuation
            ).coerceIn(0f, 1f)

        updateLayerState(soundId) { layer ->
            layer.copy(volume = clampedVolume)
        }
        player.setVolume(effectiveVolume)
    }

    /**
     * Configura o modo e a faixa de intervalo de reprodução de um som compatível.
     *
     * - Sons contínuos (`supportsIntervalFrequency == false`) permanecem sempre em [SoundIntervalMode.CONTINUOUS].
     * - Ao mudar a configuração, qualquer temporizador anterior daquele som é cancelado imediatamente,
     *   garantindo que nunca existam múltiplos temporizadores para o mesmo som.
     */
    fun setLayerIntervalConfig(soundId: String, config: SoundIntervalConfig) {
        val layer = _mixerState.value.getLayer(soundId) ?: return
        val player = activePlayers[soundId] ?: return

        val resolvedConfig = sanitizeIntervalConfigForSound(layer.sound, config)
        val shouldLoopInfinitely = resolvedConfig.mode == SoundIntervalMode.CONTINUOUS

        cancelIntervalTimer(soundId)
        player.setLooping(shouldLoopInfinitely)

        updateLayerState(soundId) { current ->
            current.copy(
                intervalConfig = resolvedConfig,
                isInfiniteLoopEnabled = shouldLoopInfinitely,
                isWaitingForNextInterval = false
            )
        }

        if (layer.isPlaying) {
            if (shouldLoopInfinitely) {
                if (!player.isPlaying) {
                    player.play()
                }
            } else if (!player.isPlaying) {
                scheduleNextIntervalPlayback(soundId, resolvedConfig)
            }
        }
    }

    /**
     * Ajusta o volume mestre (`0f` a `1f`) do Mixer, preservando intactos os volumes individuais
     * de cada camada e atualizando proporcionalmente o volume efetivo em cada player ativo.
     */
    fun setMasterVolume(masterVolume: Float) {
        val clampedMaster = masterVolume.coerceIn(0f, 1f)
        _mixerState.update { state ->
            state.copy(masterVolume = clampedMaster)
        }

        _mixerState.value.activeLayers.forEach { layer ->
            val player = activePlayers[layer.sound.id] ?: return@forEach
            val effective = (
                AmbientMixerState.calculateEffectiveVolume(
                    layerVolume = layer.volume,
                    masterVolume = clampedMaster
                ) * outputAttenuation
                ).coerceIn(0f, 1f)
            player.setVolume(effective)
        }
    }

    /**
     * Aplica um fator temporário de atenuação de saída (ex: audio ducking durante notificações)
     * sem modificar os volumes individuais ou mestre configurados pelo usuário.
     */
    fun setOutputAttenuation(factor: Float) {
        outputAttenuation = factor.coerceIn(0f, 1f)
        val currentMaster = _mixerState.value.masterVolume
        _mixerState.value.activeLayers.forEach { layer ->
            val player = activePlayers[layer.sound.id] ?: return@forEach
            val effective = (
                AmbientMixerState.calculateEffectiveVolume(
                    layerVolume = layer.volume,
                    masterVolume = currentMaster
                ) * outputAttenuation
                ).coerceIn(0f, 1f)
            player.setVolume(effective)
        }
    }

    /**
     * Pausa todas as camadas ativas no Mixer e cancela todos os temporizadores de intervalo pendentes.
     */
    fun pauseAll() {
        if (activePlayers.isEmpty()) return
        cancelAllIntervalTimers()
        _mixerState.update { state ->
            state.copy(
                activeLayers = state.activeLayers.map {
                    it.copy(isPlaying = false, isWaitingForNextInterval = false)
                }
            )
        }
        activePlayers.values.forEach { it.pause() }
    }

    /**
     * Retoma todas as camadas ativas do Mixer que estavam pausadas.
     */
    fun resumeAll() {
        if (activePlayers.isEmpty()) return
        cancelAllIntervalTimers()
        _mixerState.update { state ->
            state.copy(
                activeLayers = state.activeLayers.map {
                    it.copy(
                        isPlaying = true,
                        isWaitingForNextInterval = false,
                        errorMessage = null
                    )
                }
            )
        }
        activePlayers.values.forEach { it.play() }
    }

    /**
     * Remove todas as camadas ativas do Mixer, cancela todos os temporizadores e libera todos os players.
     */
    fun clearAll() {
        releaseAllActivePlayers()
        _mixerState.update { state ->
            state.copy(
                activeLayers = emptyList(),
                statusMessage = null
            )
        }
    }

    /**
     * Restaura uma cena salva ([SavedScene]) no Mixer, incluindo volumes e configurações de intervalo.
     */
    fun loadScene(scene: SavedScene): Int {
        releaseAllActivePlayers()

        val restoredMaster = scene.masterVolume.coerceIn(0f, 1f)
        val uniqueSelections = scene.ambientSounds
            .distinctBy { it.soundId }
            .take(AmbientMixerState.MAX_ACTIVE_SOUNDS)

        _mixerState.value = AmbientMixerState(
            activeLayers = emptyList(),
            masterVolume = restoredMaster,
            statusMessage = null
        )

        var loadedCount = 0
        uniqueSelections.forEach { selection ->
            val sound = catalogRepository.getAmbientSoundById(selection.soundId)
            if (sound != null) {
                val added = addSound(
                    sound = sound,
                    initialVolume = selection.volume.coerceIn(0f, 1f),
                    initialIntervalConfig = selection.intervalConfig
                )
                if (added) {
                    loadedCount++
                }
            }
        }

        _mixerState.update { state ->
            val message = if (loadedCount > 0) {
                "Cena \"${scene.name}\" carregada ($loadedCount ${if (loadedCount == 1) "som" else "sons"})."
            } else {
                "A cena \"${scene.name}\" não contém sons válidos disponíveis no catálogo."
            }
            state.copy(statusMessage = message)
        }
        return loadedCount
    }

    fun clearStatusMessage() {
        _mixerState.update { it.copy(statusMessage = null) }
    }

    internal fun getPlayerForTesting(soundId: String): AmbientLayerPlayer? =
        activePlayers[soundId]

    internal fun hasActiveIntervalTimerForTesting(soundId: String): Boolean =
        intervalTimerJobs[soundId]?.isActive == true

    private fun handleLayerPlaybackEnded(soundId: String) {
        val layer = _mixerState.value.getLayer(soundId) ?: return
        val player = activePlayers[soundId] ?: return

        if (!layer.isPlaying) return

        if (!layer.isIntervalModeActive) {
            player.setLooping(true)
            player.play()
            return
        }

        scheduleNextIntervalPlayback(soundId, layer.intervalConfig)
    }

    private fun scheduleNextIntervalPlayback(
        soundId: String,
        config: SoundIntervalConfig
    ) {
        cancelIntervalTimer(soundId)

        val delayMillis = intervalDelayProvider(config, random)
        if (delayMillis <= 0L) {
            activePlayers[soundId]?.play()
            return
        }

        updateLayerState(soundId) { layer ->
            layer.copy(
                isPlaying = true,
                isBuffering = false,
                isWaitingForNextInterval = true
            )
        }

        intervalTimerJobs[soundId] = scope.launch {
            delay(delayMillis)
            if (!isActive) return@launch
            val currentLayer = _mixerState.value.getLayer(soundId) ?: return@launch
            val player = activePlayers[soundId] ?: return@launch
            if (!currentLayer.isPlaying) return@launch

            updateLayerState(soundId) { layer ->
                layer.copy(isWaitingForNextInterval = false)
            }
            player.play()
        }
    }

    private fun sanitizeIntervalConfigForSound(
        sound: AmbientSound,
        requested: SoundIntervalConfig
    ): SoundIntervalConfig {
        return if (!sound.supportsIntervalFrequency) {
            SoundIntervalConfig(mode = SoundIntervalMode.CONTINUOUS)
        } else {
            SoundIntervalConfig(
                mode = requested.mode,
                customMinMinutes = requested.sanitizedMinMinutes,
                customMaxMinutes = requested.sanitizedMaxMinutes
            )
        }
    }

    private fun cancelIntervalTimer(soundId: String) {
        intervalTimerJobs.remove(soundId)?.cancel()
    }

    private fun cancelAllIntervalTimers() {
        val jobs = intervalTimerJobs.values.toList()
        intervalTimerJobs.clear()
        jobs.forEach { it.cancel() }
    }

    private fun updateLayerState(
        soundId: String,
        transform: (ActiveAmbientLayer) -> ActiveAmbientLayer
    ) {
        _mixerState.update { state ->
            state.copy(
                activeLayers = state.activeLayers.map { layer ->
                    if (layer.sound.id == soundId) transform(layer) else layer
                }
            )
        }
    }

    private fun releaseAllActivePlayers() {
        cancelAllIntervalTimers()
        val playersToRelease = activePlayers.values.toList()
        activePlayers.clear()
        playersToRelease.forEach { it.release() }
    }

    /**
     * Libera todos os recursos e cancela todos os temporizadores quando o controlador é encerrado.
     */
    fun release() {
        releaseAllActivePlayers()
        if (ownsScope) {
            scope.cancel()
        }
        _mixerState.value = AmbientMixerState()
    }
}
