package com.taaz.relaxaedorme

import com.taaz.relaxaedorme.audio.ActiveAmbientLayer
import com.taaz.relaxaedorme.audio.AmbientMixerState
import com.taaz.relaxaedorme.audio.AppAudioSessionCoordinator
import com.taaz.relaxaedorme.audio.MusicPlaybackState
import com.taaz.relaxaedorme.audio.MusicProgressState
import com.taaz.relaxaedorme.audio.MusicRepeatMode
import com.taaz.relaxaedorme.audio.SleepTimerController
import com.taaz.relaxaedorme.audio.SleepTimerPreset
import com.taaz.relaxaedorme.audio.SleepTimerState
import com.taaz.relaxaedorme.data.catalog.SoundCatalog
import com.taaz.relaxaedorme.data.model.MusicAlbum
import com.taaz.relaxaedorme.data.model.MusicTrack
import com.taaz.relaxaedorme.data.model.SoundCategory
import com.taaz.relaxaedorme.data.repository.CatalogRepository
import com.taaz.relaxaedorme.ui.components.GhostyAssets
import com.taaz.relaxaedorme.ui.navigation.AppDestination
import com.taaz.relaxaedorme.ui.theme.AppColors
import com.taaz.relaxaedorme.ui.theme.AppIcons
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStateAndNavigationTest {

    private val sampleNordicTracks = listOf(
        MusicTrack(
            id = "music_nf_001",
            title = "Above the Fjord",
            artist = "Taaz",
            albumId = MusicAlbum.ID_NORDIC_FANTASY,
            remoteAudioPath = "albums/nordic_fantasy/above_the_fjord.ogg",
            localAssetPath = "demo_music/nordic_fantasy/above_the_fjord.ogg",
            durationMs = 174_262L,
            trackOrder = 1,
            coverUrl = "albums/nordic_fantasy/cover.png"
        ),
        MusicTrack(
            id = "music_nf_002",
            title = "Dawn Over Frozen Pine",
            artist = "Taaz",
            albumId = MusicAlbum.ID_NORDIC_FANTASY,
            remoteAudioPath = "albums/nordic_fantasy/dawn_over_frozen_pine.ogg",
            localAssetPath = "demo_music/nordic_fantasy/dawn_over_frozen_pine.ogg",
            durationMs = 188_948L,
            trackOrder = 2,
            coverUrl = "albums/nordic_fantasy/cover.png"
        )
    )

    private val sampleNordicAlbum = MusicAlbum(
        id = MusicAlbum.ID_NORDIC_FANTASY,
        title = "Nordic Fantasy",
        artist = "Taaz",
        description = "Montanhas, aurora boreal e florestas geladas.",
        coverUrl = "albums/nordic_fantasy/cover.png",
        tracks = sampleNordicTracks
    )

    @Test
    fun validateMainNavigationDestinations() {
        val destinations = AppDestination.entries
        assertEquals(4, destinations.size)
        assertEquals(
            listOf("Início", "Músicas", "Sons", "Mixer"),
            destinations.map { it.label }
        )
        assertEquals(
            listOf("home", "music", "sounds", "mixer"),
            destinations.map { it.route }
        )
    }

    @Test
    fun validateRepeatModeCycle() {
        var mode = MusicRepeatMode.OFF
        mode = mode.nextMode()
        assertEquals(MusicRepeatMode.REPEAT_ONE, mode)
        mode = mode.nextMode()
        assertEquals(MusicRepeatMode.REPEAT_ALL, mode)
        mode = mode.nextMode()
        assertEquals(MusicRepeatMode.OFF, mode)
    }

    @Test
    fun validatePreviousTrackRestartThresholdLogic() {
        // Quando a faixa já avançou mais de 3 segundos, "Anterior" deve voltar ao início da faixa
        assertTrue(
            MusicPlaybackState.shouldRestartCurrentTrackOnPrevious(
                positionMs = 4_500L,
                currentQueueIndex = 2,
                queueSize = 6,
                repeatMode = MusicRepeatMode.REPEAT_ALL
            )
        )

        // Quando está nos primeiros 3 segundos e existe faixa anterior na fila, deve voltar para a anterior
        assertFalse(
            MusicPlaybackState.shouldRestartCurrentTrackOnPrevious(
                positionMs = 1_200L,
                currentQueueIndex = 2,
                queueSize = 6,
                repeatMode = MusicRepeatMode.OFF
            )
        )

        // Na primeira faixa (índice 0) com REPEAT_ALL e fila > 1, permite voltar para a última faixa
        assertFalse(
            MusicPlaybackState.shouldRestartCurrentTrackOnPrevious(
                positionMs = 1_000L,
                currentQueueIndex = 0,
                queueSize = 6,
                repeatMode = MusicRepeatMode.REPEAT_ALL
            )
        )

        // Na primeira faixa (índice 0) com repetição OFF, reinicia a primeira faixa
        assertTrue(
            MusicPlaybackState.shouldRestartCurrentTrackOnPrevious(
                positionMs = 1_000L,
                currentQueueIndex = 0,
                queueSize = 6,
                repeatMode = MusicRepeatMode.OFF
            )
        )
    }

    @Test
    fun validateRecentHistoryDeduplicationOnRepeat() {
        // Primeira execução da faixa deve registrar no histórico
        assertTrue(
            MusicPlaybackState.shouldRecordRecentOnTransition(
                newTrackId = "music_nf_001",
                lastRecordedTrackId = null,
                isRepeatTransition = false
            )
        )

        // Repetição automática (REPEAT_ONE) não deve registrar novamente
        assertFalse(
            MusicPlaybackState.shouldRecordRecentOnTransition(
                newTrackId = "music_nf_001",
                lastRecordedTrackId = "music_nf_001",
                isRepeatTransition = true
            )
        )

        // Transição redundante para a mesma faixa já registrada não deve duplicar
        assertFalse(
            MusicPlaybackState.shouldRecordRecentOnTransition(
                newTrackId = "music_nf_001",
                lastRecordedTrackId = "music_nf_001",
                isRepeatTransition = false
            )
        )

        // Mudança para uma nova faixa deve registrar normalmente
        assertTrue(
            MusicPlaybackState.shouldRecordRecentOnTransition(
                newTrackId = "music_nf_002",
                lastRecordedTrackId = "music_nf_001",
                isRepeatTransition = false
            )
        )
    }

    @Test
    fun validatePlaybackAndProgressStates() {
        val firstTrack = sampleNordicTracks.first()
        val playbackState = MusicPlaybackState(
            currentTrack = firstTrack,
            currentAlbum = sampleNordicAlbum,
            queue = sampleNordicTracks,
            currentQueueIndex = 0,
            durationMs = 174_262L,
            isPlaying = true
        )
        val progressState = MusicProgressState(
            currentPositionMs = 87_131L,
            durationMs = 174_262L
        )

        assertEquals(0.5f, progressState.progressFraction, 0.01f)
        assertEquals("01:27", progressState.formattedPosition)
        assertEquals("02:54", progressState.formattedDuration)
        assertFalse(playbackState.hasPreviousInQueue)
        assertTrue(playbackState.hasNextInQueue)
    }

    @Test
    fun validateAlbumAndSoundCategoryIconMappingInUiLayer() {
        assertEquals(
            AppIcons.NordicFantasy,
            AppIcons.forMusicAlbum(MusicAlbum.ID_NORDIC_FANTASY)
        )
        assertEquals(
            AppIcons.ElementalDreams,
            AppIcons.forMusicAlbum(MusicAlbum.ID_ELEMENTAL_DREAMS)
        )
        assertEquals(
            AppIcons.Music,
            AppIcons.forMusicAlbum("future_custom_album_id")
        )
        assertEquals(
            AppIcons.RainAndStorms,
            AppIcons.forSoundCategory(SoundCategory.ID_RAIN)
        )
        assertEquals(
            AppIcons.OceanAndRivers,
            AppIcons.forSoundCategory(SoundCategory.ID_WATER)
        )
        assertEquals(
            AppIcons.ForestAndAnimals,
            AppIcons.forSoundCategory(SoundCategory.ID_FOREST)
        )
        assertEquals(
            AppIcons.FireplaceAndCampfire,
            AppIcons.forSoundCategory(SoundCategory.ID_FIRE)
        )
        assertEquals(
            AppIcons.AmbientNoises,
            AppIcons.forSoundCategory(SoundCategory.ID_NOISE)
        )
    }

    @Test
    fun validateCatalogRepositoryEmptyByDefaultWithoutCacheAndPreservesAmbientSounds() {
        val repository = CatalogRepository()
        // Sem cache local nem sincronização remota, a biblioteca musical inicia vazia (sem dados falsos)
        assertTrue(repository.musicAlbums.isEmpty())
        assertTrue(repository.allMusicTracks.isEmpty())

        // O catálogo de sons ambientes embarcados continua intacto e disponível offline
        assertEquals(5, repository.soundCategories.size)
        assertEquals(18, repository.allAmbientSounds.size)
        assertEquals(6, repository.getAmbientSoundsByCategory(SoundCategory.ID_RAIN).size)
        assertEquals(3, repository.getAmbientSoundsByCategory(SoundCategory.ID_WATER).size)
        assertEquals(3, repository.getAmbientSoundsByCategory(SoundCategory.ID_FOREST).size)
        assertEquals(3, repository.getAmbientSoundsByCategory(SoundCategory.ID_FIRE).size)
        assertEquals(3, repository.getAmbientSoundsByCategory(SoundCategory.ID_NOISE).size)
    }

    @Test
    fun validateMediaSessionPresentationForMusicAndMixer() {
        // 1. Estado ocioso: nem música nem Mixer ativos
        val idlePresentation = AppAudioSessionCoordinator.buildSessionPresentation(
            musicState = MusicPlaybackState(),
            mixerState = AmbientMixerState()
        )
        assertFalse(idlePresentation.isActive)

        // 2. Somente Música ativa
        val firstTrack = sampleNordicTracks.first()
        val musicActiveState = MusicPlaybackState(
            currentTrack = firstTrack,
            currentAlbum = sampleNordicAlbum,
            queue = sampleNordicTracks,
            currentQueueIndex = 0,
            durationMs = 174_263L,
            isPlaying = true
        )
        val musicPresentation = AppAudioSessionCoordinator.buildSessionPresentation(
            musicState = musicActiveState,
            mixerState = AmbientMixerState()
        )
        assertTrue(musicPresentation.isActive)
        assertFalse(musicPresentation.isMixerPrimary)
        assertEquals(firstTrack.title, musicPresentation.title)
        assertEquals("Taaz", musicPresentation.artist)
        assertEquals("Nordic Fantasy", musicPresentation.albumTitle)
        assertTrue(musicPresentation.canSkipNextOrPrevious)
        assertTrue(musicPresentation.isSeekable)

        // 3. Somente Mixer ativo (ex: Chuva Suave, Trovão Distante, Vento na Floresta)
        val rain = SoundCatalog.getSoundById("sound_rain_001")!!
        val thunder = SoundCatalog.getSoundById("sound_rain_005")!!
        val wind = SoundCatalog.getSoundById("sound_forest_003")!!
        val mixerActiveState = AmbientMixerState(
            activeLayers = listOf(
                ActiveAmbientLayer(sound = rain, categoryName = "Chuva e tempestade", volume = 0.7f, isPlaying = true),
                ActiveAmbientLayer(sound = thunder, categoryName = "Chuva e tempestade", volume = 0.5f, isPlaying = true),
                ActiveAmbientLayer(sound = wind, categoryName = "Floresta e animais", volume = 0.6f, isPlaying = true)
            )
        )
        val mixerPresentation = AppAudioSessionCoordinator.buildSessionPresentation(
            musicState = MusicPlaybackState(),
            mixerState = mixerActiveState
        )
        assertTrue(mixerPresentation.isActive)
        assertTrue(mixerPresentation.isMixerPrimary)
        assertEquals(AppAudioSessionCoordinator.MIXER_NOTIFICATION_TITLE, mixerPresentation.title)
        assertEquals("Chuva Suave, Trovão Distante, Vento na Floresta", mixerPresentation.artist)
        assertFalse(mixerPresentation.canSkipNextOrPrevious)
        assertFalse(mixerPresentation.isSeekable)

        // 4. Música e Mixer tocando simultaneamente
        val combinedPresentation = AppAudioSessionCoordinator.buildSessionPresentation(
            musicState = musicActiveState,
            mixerState = mixerActiveState
        )
        assertTrue(combinedPresentation.isActive)
        assertFalse(combinedPresentation.isMixerPrimary)
        assertEquals(firstTrack.title, combinedPresentation.title)
        assertEquals("Taaz • + Sons ambientes", combinedPresentation.artist)
        assertTrue(combinedPresentation.isPlaying)
    }

    @Test
    fun validateSleepTimerPresetCycleAndFormatting() {
        var preset = SleepTimerPreset.OFF
        val visited = mutableListOf<SleepTimerPreset>()
        repeat(7) {
            preset = preset.nextPreset()
            visited.add(preset)
        }
        assertEquals(
            listOf(
                SleepTimerPreset.MIN_15,
                SleepTimerPreset.MIN_30,
                SleepTimerPreset.MIN_45,
                SleepTimerPreset.HOUR_1,
                SleepTimerPreset.HOUR_1_30,
                SleepTimerPreset.HOUR_2,
                SleepTimerPreset.OFF
            ),
            visited
        )
        assertEquals("15:00", SleepTimerState.formatCountdownMillis(15 * 60_000L))
        assertEquals("1:30:00", SleepTimerState.formatCountdownMillis(90 * 60_000L))
        assertEquals("2:00:00", SleepTimerState.formatCountdownMillis(120 * 60_000L))
    }

    @Test
    fun validateShuffleSequenceNoPrematureRepeatAndAvoidsPreviousLastTrack() {
        val sixAlbumTracks = (1..6).map { index ->
            MusicTrack(
                id = "music_ed_00$index",
                title = "Elemental Track $index",
                albumId = MusicAlbum.ID_ELEMENTAL_DREAMS,
                remoteAudioPath = "albums/elemental_dreams/track_$index.ogg",
                trackOrder = index
            )
        }
        assertEquals(6, sixAlbumTracks.size)
        val rng = Random(42)

        // 1. Ao ativar shuffle com uma música já selecionada, ela permanece em 1º e as outras 5 são embaralhadas sem repetição
        val lockedFirst = sixAlbumTracks[2]
        val firstSequence = MusicPlaybackState.generateShuffledQueue(
            tracks = sixAlbumTracks,
            lockedFirstTrack = lockedFirst,
            random = rng
        )
        assertEquals(6, firstSequence.size)
        assertEquals(lockedFirst.id, firstSequence.first().id)
        assertEquals(sixAlbumTracks.map { it.id }.toSet(), firstSequence.map { it.id }.toSet())

        // 2. Em 50 ciclos seguidos, cada nova sequência contém todas as músicas sem repetição
        //    e NUNCA começa com a última música da sequência anterior
        var previousLastId = firstSequence.last().id
        repeat(50) {
            val nextSeq = MusicPlaybackState.generateShuffledQueue(
                tracks = sixAlbumTracks,
                avoidFirstTrackId = previousLastId,
                random = rng
            )
            assertEquals(6, nextSeq.size)
            assertEquals(sixAlbumTracks.map { it.id }.toSet(), nextSeq.map { it.id }.toSet())
            assertNotEquals(
                "A primeira música da nova sequência não deve repetir a última da anterior",
                previousLastId,
                nextSeq.first().id
            )
            previousLastId = nextSeq.last().id
        }

        // 3. Mesmo em álbum com apenas 2 músicas (Nordic Fantasy), evita repetir a última música na virada de sequência
        val nordicTracks = sampleNordicTracks
        assertEquals(2, nordicTracks.size)
        val seqA = MusicPlaybackState.generateShuffledQueue(
            tracks = nordicTracks,
            lockedFirstTrack = nordicTracks[0],
            random = rng
        )
        assertEquals(nordicTracks[0].id, seqA.first().id)
        assertEquals(nordicTracks[1].id, seqA.last().id)

        val seqB = MusicPlaybackState.generateShuffledQueue(
            tracks = nordicTracks,
            avoidFirstTrackId = seqA.last().id,
            random = rng
        )
        assertEquals(nordicTracks[0].id, seqB.first().id)
    }

    @Test
    fun validateSleepTimerCountdownFadeOutAndCancelDuringFadeOut() = runBlocking {
        val recordedMultipliers = mutableListOf<Float>()
        var timerFinishedCount = 0

        val timerController = SleepTimerController(
            tickIntervalMs = 15L,
            fadeOutDurationMs = 50L,
            fadeOutSteps = 5,
            restoreSteps = 3,
            restoreStepDelayMs = 5L,
            presetDurationProvider = { preset ->
                if (preset == SleepTimerPreset.OFF) 0L else 30L
            },
            onFadeMultiplierChanged = { multiplier ->
                recordedMultipliers.add(multiplier)
            },
            onTimerFinished = {
                timerFinishedCount++
            }
        )

        // 1. Alterna opções sem criar temporizadores duplicados
        timerController.cyclePreset() // 15m
        assertEquals(SleepTimerPreset.MIN_15, timerController.sleepTimerState.value.preset)
        assertTrue(timerController.hasActiveTimerJobForTesting)
        timerController.cyclePreset() // 30m (substitui o anterior)
        assertEquals(SleepTimerPreset.MIN_30, timerController.sleepTimerState.value.preset)
        assertTrue(timerController.hasActiveTimerJobForTesting)

        // 2. Aguarda entrar em fade-out e cancela no meio do fade-out -> interrompe encerramento e restaura volume 1.0f
        delay(55L)
        assertTrue(timerController.sleepTimerState.value.isFadingOut)
        assertTrue(timerController.sleepTimerState.value.fadeMultiplier < 1.0f)

        // Toque no botão Zz durante o fade-out cancela o encerramento e restaura o volume
        timerController.cyclePreset()
        delay(30L)
        assertFalse(timerController.sleepTimerState.value.isActive)
        assertEquals(SleepTimerPreset.OFF, timerController.sleepTimerState.value.preset)
        assertEquals(0, timerFinishedCount)
        assertEquals(1.0f, recordedMultipliers.last(), 0.001f)

        // 3. Execução completa até o fim do fade-out -> encerra reprodução e preserva multiplicador final restaurado
        timerController.setPreset(SleepTimerPreset.MIN_15)
        delay(120L)
        assertEquals(1, timerFinishedCount)
        assertFalse(timerController.sleepTimerState.value.isActive)
        assertEquals(SleepTimerPreset.OFF, timerController.sleepTimerState.value.preset)
        assertEquals(1.0f, recordedMultipliers.last(), 0.001f)

        timerController.release()
    }

    @Test
    fun validateGhostyHomeAssetsAndThemeColors() {
        assertEquals("ghosty/Ghosty_idle.png", GhostyAssets.IDLE)
        assertEquals("ghosty/Ghosty_laugh.png", GhostyAssets.LAUGH)
        assertEquals("ghosty/Ghosty_sleep.png", GhostyAssets.SLEEP)
        assertEquals("ghosty/Ghosty_zzz.png", GhostyAssets.ZZZ)
        assertEquals("ghosty/Ghosty_thinking.png", GhostyAssets.THINKING)
        assertEquals(0xFF090C15L, AppColors.NightBackground.value.shr(32).toLong() and 0xFFFFFFFFL)
    }
}
