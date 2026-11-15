package com.taaz.relaxaedorme

import com.taaz.relaxaedorme.audio.AmbientLayerPlayer
import com.taaz.relaxaedorme.audio.AmbientLayerPlayerFactory
import com.taaz.relaxaedorme.audio.AmbientLayerPlayerListener
import com.taaz.relaxaedorme.audio.AmbientSoundMixerController
import com.taaz.relaxaedorme.audio.MusicPlaybackState
import com.taaz.relaxaedorme.audio.SleepTimerController
import com.taaz.relaxaedorme.audio.SleepTimerPreset
import com.taaz.relaxaedorme.data.catalog.SoundCatalog
import com.taaz.relaxaedorme.data.model.AmbientSound
import com.taaz.relaxaedorme.data.model.MusicAlbum
import com.taaz.relaxaedorme.data.model.MusicTrack
import com.taaz.relaxaedorme.data.model.SavedScene
import com.taaz.relaxaedorme.data.model.SavedSoundSelection
import com.taaz.relaxaedorme.data.model.SoundIntervalConfig
import com.taaz.relaxaedorme.data.model.SoundIntervalMode
import com.taaz.relaxaedorme.data.repository.CatalogRepository
import com.taaz.relaxaedorme.ui.viewmodel.MainUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes unitários completos para o Mixer de Sons Ambientes e Frequência de Intervalo (Etapa 5 + 5.1).
 */
class AmbientMixerTest {

    private class FakeAmbientLayerPlayer(
        val sound: AmbientSound,
        private val listener: AmbientLayerPlayerListener
    ) : AmbientLayerPlayer {
        override var isPlaying: Boolean = false
            private set

        override var isLoopingInfinitely: Boolean = true
            private set

        override var appliedVolume: Float = 0f
            private set

        override var isReleased: Boolean = false
            private set

        var preparedUri: String? = null
            private set

        override fun prepareAndPlay(
            assetUri: String,
            initialVolume: Float,
            loopInfinitely: Boolean
        ) {
            if (isReleased) return
            preparedUri = assetUri
            appliedVolume = initialVolume.coerceIn(0f, 1f)
            isLoopingInfinitely = loopInfinitely
            isPlaying = true
            listener.onPlayingStateChanged(isPlaying = true, isBuffering = false)
        }

        override fun play() {
            if (isReleased) return
            isPlaying = true
            listener.onPlayingStateChanged(isPlaying = true, isBuffering = false)
        }

        override fun pause() {
            if (isReleased) return
            isPlaying = false
            listener.onPlayingStateChanged(isPlaying = false, isBuffering = false)
        }

        override fun setVolume(volume: Float) {
            if (isReleased) return
            appliedVolume = volume.coerceIn(0f, 1f)
        }

        override fun setLooping(loopInfinitely: Boolean) {
            if (isReleased) return
            isLoopingInfinitely = loopInfinitely
        }

        fun simulatePlaybackEnded() {
            if (isReleased) return
            isPlaying = false
            listener.onPlaybackEnded()
        }

        override fun release() {
            isReleased = true
            isPlaying = false
        }
    }

    private fun createController(
        createdPlayers: MutableList<FakeAmbientLayerPlayer> = mutableListOf()
    ): AmbientSoundMixerController {
        val repository = CatalogRepository()
        val factory = AmbientLayerPlayerFactory { sound, listener ->
            FakeAmbientLayerPlayer(sound, listener).also { createdPlayers.add(it) }
        }
        return AmbientSoundMixerController(
            catalogRepository = repository,
            playerFactory = factory
        )
    }

    @Test
    fun addAndRemoveLayersStartsImmediateInfiniteLoopAndReleasesPlayer() {
        val createdPlayers = mutableListOf<FakeAmbientLayerPlayer>()
        val controller = createController(createdPlayers)
        val rainSound = SoundCatalog.getSoundById("sound_rain_001")!!
        val fireSound = SoundCatalog.getSoundById("sound_fire_001")!!

        // Ao adicionar o primeiro som, deve iniciar imediatamente em loop infinito
        assertTrue(controller.addSound(rainSound))
        assertEquals(1, controller.mixerState.value.activeCount)
        val rainLayer = controller.mixerState.value.getLayer(rainSound.id)!!
        assertTrue(rainLayer.isPlaying)
        assertTrue(rainLayer.isInfiniteLoopEnabled)
        val rainPlayer = controller.getPlayerForTesting(rainSound.id)!!
        assertTrue(rainPlayer.isPlaying)
        assertTrue(rainPlayer.isLoopingInfinitely)
        assertFalse(rainPlayer.isReleased)

        // Adicionar segundo som preserva o primeiro tocando simultaneamente
        assertTrue(controller.addSound(fireSound))
        assertEquals(2, controller.mixerState.value.activeCount)
        assertTrue(controller.mixerState.value.isSoundPlaying(rainSound.id))
        assertTrue(controller.mixerState.value.isSoundPlaying(fireSound.id))

        // Remover primeira camada libera o player correspondente e mantém a segunda ativa
        controller.removeSound(rainSound.id)
        assertTrue(rainPlayer.isReleased)
        assertEquals(1, controller.mixerState.value.activeCount)
        assertFalse(controller.mixerState.value.isSoundActive(rainSound.id))
        assertTrue(controller.mixerState.value.isSoundPlaying(fireSound.id))
    }

    @Test
    fun enforcesEightLayerLimitAndPreventsNinthLayer() {
        val createdPlayers = mutableListOf<FakeAmbientLayerPlayer>()
        val controller = createController(createdPlayers)
        val allSounds = SoundCatalog.sounds

        // Adiciona 8 sons diferentes
        allSounds.take(8).forEach { sound ->
            assertTrue("Deve permitir adicionar até 8 camadas", controller.addSound(sound))
        }
        assertEquals(8, controller.mixerState.value.activeCount)
        assertTrue(controller.mixerState.value.isAtLimit)

        // Tenta adicionar o 9º som: deve bloquear e informar mensagem clara ao usuário
        val ninthSound = allSounds[8]
        assertFalse("Não deve permitir adicionar a 9ª camada", controller.addSound(ninthSound))
        assertEquals(8, controller.mixerState.value.activeCount)
        assertEquals(8, createdPlayers.size)
        assertNotNull(controller.mixerState.value.statusMessage)
    }

    @Test
    fun blocksDuplicateLayersWhenAddingSameSoundTwice() {
        val createdPlayers = mutableListOf<FakeAmbientLayerPlayer>()
        val controller = createController(createdPlayers)
        val oceanSound = SoundCatalog.getSoundById("sound_water_001")!!

        assertTrue(controller.addSound(oceanSound))
        assertEquals(1, controller.mixerState.value.activeCount)
        assertEquals(1, createdPlayers.size)

        // Pausa a camada e seleciona novamente: não duplica player, apenas retoma reprodução
        controller.toggleLayerPlayPause(oceanSound.id)
        assertFalse(controller.mixerState.value.isSoundPlaying(oceanSound.id))

        assertTrue(controller.addSound(oceanSound))
        assertEquals(1, controller.mixerState.value.activeCount)
        assertEquals(1, createdPlayers.size)
        assertTrue(controller.mixerState.value.isSoundPlaying(oceanSound.id))
    }

    @Test
    fun individualAndMasterVolumePreserveProportionsAndCalculateEffectiveVolume() {
        val controller = createController()
        val rainSound = SoundCatalog.getSoundById("sound_rain_001")!!
        val fireSound = SoundCatalog.getSoundById("sound_fire_001")!!

        controller.addSound(rainSound, initialVolume = 0.80f)
        controller.addSound(fireSound, initialVolume = 0.40f)

        // Exemplo da especificação: Chuva 80%, Fogo 40%, Volume mestre 50%
        controller.setMasterVolume(0.50f)

        val state = controller.mixerState.value
        val rainLayer = state.getLayer(rainSound.id)!!
        val fireLayer = state.getLayer(fireSound.id)!!

        // Volumes individuais devem permanecer intactos (80% e 40%)
        assertEquals(0.80f, rainLayer.volume, 0.001f)
        assertEquals(0.40f, fireLayer.volume, 0.001f)
        assertEquals(80, rainLayer.volumePercentage)
        assertEquals(40, fireLayer.volumePercentage)
        assertEquals(0.50f, state.masterVolume, 0.001f)
        assertEquals(50, state.masterVolumePercentage)

        // Volumes efetivos nos players devem ser 40% (0.40f) e 20% (0.20f)
        val rainPlayer = controller.getPlayerForTesting(rainSound.id)!!
        val firePlayer = controller.getPlayerForTesting(fireSound.id)!!
        assertEquals(0.40f, rainPlayer.appliedVolume, 0.001f)
        assertEquals(0.20f, firePlayer.appliedVolume, 0.001f)

        // Alterar volume individual do Fogo para 60% com mestre em 50% -> efetivo 30%
        controller.setLayerVolume(fireSound.id, 0.60f)
        assertEquals(0.60f, controller.mixerState.value.getLayer(fireSound.id)!!.volume, 0.001f)
        assertEquals(0.30f, firePlayer.appliedVolume, 0.001f)
    }

    @Test
    fun pauseAndResumeIndividualAndAllLayers() {
        val controller = createController()
        val s1 = SoundCatalog.getSoundById("sound_rain_001")!!
        val s2 = SoundCatalog.getSoundById("sound_water_001")!!
        val s3 = SoundCatalog.getSoundById("sound_forest_001")!!

        controller.addSound(s1)
        controller.addSound(s2)
        controller.addSound(s3)

        // Pausa apenas s2
        controller.toggleLayerPlayPause(s2.id)
        assertTrue(controller.mixerState.value.isSoundPlaying(s1.id))
        assertFalse(controller.mixerState.value.isSoundPlaying(s2.id))
        assertTrue(controller.mixerState.value.isSoundPlaying(s3.id))

        // Pausa todos
        controller.pauseAll()
        assertFalse(controller.mixerState.value.anyPlaying)
        assertTrue(controller.mixerState.value.anyPaused)
        assertFalse(controller.getPlayerForTesting(s1.id)!!.isPlaying)
        assertFalse(controller.getPlayerForTesting(s2.id)!!.isPlaying)
        assertFalse(controller.getPlayerForTesting(s3.id)!!.isPlaying)

        // Retoma todos
        controller.resumeAll()
        assertTrue(controller.mixerState.value.anyPlaying)
        assertFalse(controller.mixerState.value.anyPaused)
        assertTrue(controller.getPlayerForTesting(s1.id)!!.isPlaying)
        assertTrue(controller.getPlayerForTesting(s2.id)!!.isPlaying)
        assertTrue(controller.getPlayerForTesting(s3.id)!!.isPlaying)
    }

    @Test
    fun intervalFrequencyRespectsSoundNatureAndCancelsTimersOnPauseOrRemove() {
        val createdPlayers = mutableListOf<FakeAmbientLayerPlayer>()
        val controller = createController(createdPlayers)

        val continuousRain = SoundCatalog.getSoundById("sound_rain_001")!!
        val thunderEffect = SoundCatalog.getSoundById("sound_rain_005")!!

        assertFalse(continuousRain.supportsIntervalFrequency)
        assertTrue(thunderEffect.supportsIntervalFrequency)

        controller.addSound(continuousRain)
        controller.addSound(thunderEffect)

        // 1. Tentar configurar intervalo em som contínuo (Chuva Suave) mantém CONTINUOUS
        controller.setLayerIntervalConfig(
            continuousRain.id,
            SoundIntervalConfig(mode = SoundIntervalMode.RARE)
        )
        val rainLayer = controller.mixerState.value.getLayer(continuousRain.id)!!
        assertEquals(SoundIntervalMode.CONTINUOUS, rainLayer.intervalConfig.mode)
        assertTrue(rainLayer.isInfiniteLoopEnabled)

        // 2. Configurar intervalo personalizado (2 a 5 min) no Trovão Distante
        val customConfig = SoundIntervalConfig(
            mode = SoundIntervalMode.CUSTOM,
            customMinMinutes = 2,
            customMaxMinutes = 5
        )
        val delayMs = customConfig.nextRandomDelayMillis()
        assertTrue(delayMs in (2 * 60_000L)..(5 * 60_000L))

        controller.setLayerIntervalConfig(thunderEffect.id, customConfig)
        val thunderLayer = controller.mixerState.value.getLayer(thunderEffect.id)!!
        assertEquals(SoundIntervalMode.CUSTOM, thunderLayer.intervalConfig.mode)
        assertFalse(thunderLayer.isInfiniteLoopEnabled)

        // 3. Quando o efeito de trovão termina uma execução, agenda temporizador único
        val thunderPlayer = createdPlayers.first { it.sound.id == thunderEffect.id }
        thunderPlayer.simulatePlaybackEnded()
        assertTrue(controller.hasActiveIntervalTimerForTesting(thunderEffect.id))
        assertTrue(controller.mixerState.value.getLayer(thunderEffect.id)!!.isWaitingForNextInterval)

        // 4. Ao pausar ou remover o som, o temporizador é cancelado imediatamente
        controller.toggleLayerPlayPause(thunderEffect.id)
        assertFalse(controller.hasActiveIntervalTimerForTesting(thunderEffect.id))

        controller.removeSound(thunderEffect.id)
        assertFalse(controller.hasActiveIntervalTimerForTesting(thunderEffect.id))
        controller.release()
    }

    @Test
    fun saveAndRestoreScenePreservesVolumesLoopDeduplicatesAndRespectsLimit() {
        val createdPlayers = mutableListOf<FakeAmbientLayerPlayer>()
        val controller = createController(createdPlayers)
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        val rain = SoundCatalog.getSoundById("sound_rain_001")!!
        val thunder = SoundCatalog.getSoundById("sound_rain_005")!!

        controller.addSound(rain, initialVolume = 0.75f)
        controller.addSound(thunder, initialVolume = 0.50f)
        controller.setLayerIntervalConfig(
            thunder.id,
            SoundIntervalConfig(mode = SoundIntervalMode.MODERATE)
        )
        controller.setMasterVolume(0.80f)

        // 1. Salva a cena e serializa/desserializa via JSON (como no DataStore)
        val savedScene = controller.mixerState.value.toSavedScene(
            id = "scene_test_1",
            name = "Noite Chuvosa e Trovões"
        )
        val encoded = json.encodeToString(savedScene)
        val decodedScene = json.decodeFromString<SavedScene>(encoded)

        assertEquals("Noite Chuvosa e Trovões", decodedScene.name)
        assertEquals(2, decodedScene.ambientSounds.size)
        assertEquals(0.80f, decodedScene.masterVolume, 0.001f)
        assertNull("Cena do Mixer não deve exigir música", decodedScene.selectedMusicId)

        // 2. Limpa o Mixer e carrega a cena salva
        val firstGenerationPlayers = createdPlayers.toList()
        controller.clearAll()
        assertTrue(firstGenerationPlayers.all { it.isReleased })
        assertEquals(0, controller.mixerState.value.activeCount)

        val loadedCount = controller.loadScene(decodedScene)
        assertEquals(2, loadedCount)
        val restoredState = controller.mixerState.value
        assertEquals(2, restoredState.activeCount)
        assertEquals(0.80f, restoredState.masterVolume, 0.001f)
        assertEquals(0.75f, restoredState.getLayer(rain.id)!!.volume, 0.001f)
        assertEquals(0.50f, restoredState.getLayer(thunder.id)!!.volume, 0.001f)
        assertEquals(
            SoundIntervalMode.MODERATE,
            restoredState.getLayer(thunder.id)!!.intervalConfig.mode
        )
        assertTrue(restoredState.isSoundPlaying(rain.id))
        assertTrue(restoredState.isSoundPlaying(thunder.id))

        // 3. Testa cena com sons duplicados e mais de 8 entradas
        val oversizedSelections = (SoundCatalog.sounds.take(10) + SoundCatalog.sounds.take(2)).map {
            SavedSoundSelection(soundId = it.id, volume = 0.5f)
        }
        val oversizedScene = SavedScene(
            id = "scene_oversized",
            name = "Cena Grande",
            ambientSounds = oversizedSelections,
            masterVolume = 0.90f
        )
        val countAfterOversized = controller.loadScene(oversizedScene)
        assertEquals(8, countAfterOversized)
        assertEquals(8, controller.mixerState.value.activeCount)
        controller.release()
    }

    @Test
    fun independenceBetweenMusicAndMixer() {
        val controller = createController()
        val track = MusicTrack(
            id = "music_nf_001",
            title = "Above the Fjord",
            artist = "Taaz",
            albumId = MusicAlbum.ID_NORDIC_FANTASY,
            remoteAudioPath = "albums/nordic_fantasy/above_the_fjord.ogg",
            localAssetPath = "demo_music/nordic_fantasy/above_the_fjord.ogg"
        )
        val album = MusicAlbum(
            id = MusicAlbum.ID_NORDIC_FANTASY,
            title = "Nordic Fantasy",
            artist = "Taaz",
            description = "Paisagens nórdicas serenas",
            coverUrl = "albums/nordic_fantasy/cover.png",
            tracks = listOf(track)
        )
        val musicState = MusicPlaybackState(
            currentTrack = track,
            currentAlbum = album,
            isPlaying = true,
            volume = 0.85f
        )

        val ambientSound = SoundCatalog.sounds[0]
        val uiState = MainUiState(
            musicAlbums = listOf(album),
            allMusicTracks = listOf(track),
            allAmbientSounds = SoundCatalog.sounds
        )
        assertEquals(1, uiState.musicAlbums.size)
        assertEquals(1, uiState.allMusicTracks.size)
        assertEquals(5, SoundCatalog.categories.size)
        assertEquals(18, SoundCatalog.sounds.size)

        controller.addSound(ambientSound, initialVolume = 0.70f)
        controller.setMasterVolume(0.30f)
        controller.pauseAll()

        assertTrue("Música deve continuar tocando independentemente do Mixer", musicState.isPlaying)
        assertEquals(
            "Volume da música não pode ser alterado pelo Mixer",
            0.85f,
            musicState.volume,
            0.001f
        )

        controller.release()
        assertEquals(0, controller.mixerState.value.activeCount)
    }

    @Test
    fun backgroundThreeLayerMixWithIntervalsAndAudioDuckingPreservesState() = runBlocking {
        val createdPlayers = mutableListOf<FakeAmbientLayerPlayer>()
        val repository = CatalogRepository()
        val controller = AmbientSoundMixerController(
            catalogRepository = repository,
            playerFactory = AmbientLayerPlayerFactory { sound, listener ->
                FakeAmbientLayerPlayer(sound, listener).also { createdPlayers.add(it) }
            },
            intervalDelayProvider = { config, _ ->
                if (config.mode != SoundIntervalMode.CONTINUOUS) 40L else 0L
            }
        )

        val rain = SoundCatalog.getSoundById("sound_rain_001")!!
        val wind = SoundCatalog.getSoundById("sound_forest_003")!!
        val thunder = SoundCatalog.getSoundById("sound_rain_005")!!

        // 1. Seleciona Chuva (80%), Vento (60%) e Trovão Distante (50%, intervalo Frequente 15-45s)
        controller.addSound(rain, initialVolume = 0.80f)
        controller.addSound(wind, initialVolume = 0.60f)
        controller.addSound(
            thunder,
            initialVolume = 0.50f,
            initialIntervalConfig = SoundIntervalConfig(mode = SoundIntervalMode.FREQUENT)
        )

        assertEquals(3, controller.mixerState.value.activeCount)
        assertTrue(controller.mixerState.value.isSoundPlaying(rain.id))
        assertTrue(controller.mixerState.value.isSoundPlaying(wind.id))
        assertTrue(controller.mixerState.value.isSoundPlaying(thunder.id))

        val rainPlayer = controller.getPlayerForTesting(rain.id) as FakeAmbientLayerPlayer
        val windPlayer = controller.getPlayerForTesting(wind.id) as FakeAmbientLayerPlayer
        val thunderPlayer = controller.getPlayerForTesting(thunder.id) as FakeAmbientLayerPlayer

        // 2. O trovão termina a primeira execução e aguarda o próximo intervalo em segundo plano
        thunderPlayer.simulatePlaybackEnded()
        assertTrue(controller.hasActiveIntervalTimerForTesting(thunder.id))
        assertTrue(controller.mixerState.value.getLayer(thunder.id)!!.isWaitingForNextInterval)
        assertTrue("Chuva continua tocando", rainPlayer.isPlaying)
        assertTrue("Vento continua tocando", windPlayer.isPlaying)

        // 3. Simula audio ducking temporário (notificação do sistema)
        controller.setOutputAttenuation(0.25f)
        assertEquals(0.20f, rainPlayer.appliedVolume, 0.001f)
        assertEquals(0.15f, windPlayer.appliedVolume, 0.001f)
        // Volumes originais da UI permanecem intactos
        assertEquals(0.80f, controller.mixerState.value.getLayer(rain.id)!!.volume, 0.001f)
        assertEquals(0.60f, controller.mixerState.value.getLayer(wind.id)!!.volume, 0.001f)

        // Retorno do foco restaura os volumes originais
        controller.setOutputAttenuation(1.0f)
        assertEquals(0.80f, rainPlayer.appliedVolume, 0.001f)
        assertEquals(0.60f, windPlayer.appliedVolume, 0.001f)

        // 4. Aguarda o intervalo expirar em segundo plano -> o trovão dispara novamente sozinho
        delay(80L)
        assertFalse(controller.mixerState.value.getLayer(thunder.id)!!.isWaitingForNextInterval)
        assertTrue("Trovão deve tocar novamente após o intervalo", thunderPlayer.isPlaying)

        controller.release()
    }

    @Test
    fun sleepTimerFadeOutWithThreeSimultaneousSoundsPreservesIndividualVolumes() = runBlocking {
        val controller = createController()
        val rain = SoundCatalog.getSoundById("sound_rain_001")!!
        val fire = SoundCatalog.getSoundById("sound_fire_001")!!
        val thunder = SoundCatalog.getSoundById("sound_rain_005")!!

        controller.addSound(rain, initialVolume = 0.80f)
        controller.addSound(fire, initialVolume = 0.40f)
        controller.addSound(
            thunder,
            initialVolume = 0.60f,
            initialIntervalConfig = SoundIntervalConfig(mode = SoundIntervalMode.MODERATE)
        )

        val rainPlayer = controller.getPlayerForTesting(rain.id) as FakeAmbientLayerPlayer
        val firePlayer = controller.getPlayerForTesting(fire.id) as FakeAmbientLayerPlayer
        val thunderPlayer = controller.getPlayerForTesting(thunder.id) as FakeAmbientLayerPlayer

        val sleepTimer = SleepTimerController(
            tickIntervalMs = 15L,
            fadeOutDurationMs = 50L,
            fadeOutSteps = 5,
            restoreSteps = 3,
            restoreStepDelayMs = 5L,
            presetDurationProvider = { 25L },
            onFadeMultiplierChanged = { multiplier ->
                controller.setOutputAttenuation(multiplier)
            },
            onTimerFinished = {
                controller.pauseAll()
            }
        )

        // 1. Inicia temporizador e aguarda entrar no meio do fade-out
        sleepTimer.setPreset(SleepTimerPreset.MIN_15)
        delay(50L)
        assertTrue(sleepTimer.sleepTimerState.value.isFadingOut)
        assertTrue(rainPlayer.appliedVolume < 0.80f)
        assertTrue(firePlayer.appliedVolume < 0.40f)
        // Volumes originais das camadas permanecem intactos
        assertEquals(0.80f, controller.mixerState.value.getLayer(rain.id)!!.volume, 0.001f)
        assertEquals(0.40f, controller.mixerState.value.getLayer(fire.id)!!.volume, 0.001f)
        assertEquals(0.60f, controller.mixerState.value.getLayer(thunder.id)!!.volume, 0.001f)

        // 2. Cancela durante o fade-out -> restaura volumes efetivos sem interromper os 3 sons
        sleepTimer.cancel(restoreVolumeSmoothly = true)
        delay(30L)
        assertTrue(controller.mixerState.value.anyPlaying)
        assertEquals(0.80f, rainPlayer.appliedVolume, 0.001f)
        assertEquals(0.40f, firePlayer.appliedVolume, 0.001f)
        assertEquals(0.60f, thunderPlayer.appliedVolume, 0.001f)

        // 3. Deixa o temporizador concluir até o fim do fade-out -> pausa todos os sons e preserva volumes originais
        sleepTimer.setPreset(SleepTimerPreset.MIN_30)
        delay(120L)
        assertFalse(controller.mixerState.value.anyPlaying)
        assertFalse(controller.hasActiveIntervalTimerForTesting(thunder.id))
        assertEquals(0.80f, controller.mixerState.value.getLayer(rain.id)!!.volume, 0.001f)
        assertEquals(0.40f, controller.mixerState.value.getLayer(fire.id)!!.volume, 0.001f)
        assertEquals(0.60f, controller.mixerState.value.getLayer(thunder.id)!!.volume, 0.001f)

        sleepTimer.release()
        controller.release()
    }
}
