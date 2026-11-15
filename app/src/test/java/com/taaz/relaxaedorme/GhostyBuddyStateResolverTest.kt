package com.taaz.relaxaedorme

import com.taaz.relaxaedorme.ui.components.GhostyAssets
import com.taaz.relaxaedorme.ui.components.GhostyBuddyStatus
import com.taaz.relaxaedorme.ui.components.GhostyPlayingFocus
import com.taaz.relaxaedorme.ui.components.resolveGhostyBuddyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GhostyBuddyStateResolverTest {

    @Test
    fun `app opened without playback resolves to WELCOME with smile pose`() {
        val state = resolveGhostyBuddyState(
            isMusicPlaying = false,
            hasLoadedTrack = false,
            isMixerPlaying = false,
            hasActiveMixerSounds = false,
            isSleepTimerActive = false,
            isSleepTimerFadingOut = false,
            sleepTimerRemainingMillis = 0L
        )

        assertEquals(GhostyBuddyStatus.WELCOME, state.status)
        assertEquals(GhostyAssets.SMILE, state.poseAssetPath)
        assertEquals("Que bom ter você aqui.", state.message)
    }

    @Test
    fun `browsing library without active audio resolves to BROWSING with reading pose`() {
        val state = resolveGhostyBuddyState(
            isMusicPlaying = false,
            hasLoadedTrack = false,
            isMixerPlaying = false,
            hasActiveMixerSounds = false,
            isSleepTimerActive = false,
            isSleepTimerFadingOut = false,
            sleepTimerRemainingMillis = 0L,
            isBrowsingLibrary = true
        )

        assertEquals(GhostyBuddyStatus.BROWSING, state.status)
        assertEquals(GhostyAssets.READING, state.poseAssetPath)
        assertNull(state.message)
    }

    @Test
    fun `music playing resolves to PLAYING with headphone pose`() {
        val state = resolveGhostyBuddyState(
            isMusicPlaying = true,
            hasLoadedTrack = true,
            isMixerPlaying = false,
            hasActiveMixerSounds = false,
            isSleepTimerActive = false,
            isSleepTimerFadingOut = false,
            sleepTimerRemainingMillis = 0L
        )

        assertEquals(GhostyBuddyStatus.PLAYING, state.status)
        assertEquals(GhostyPlayingFocus.MUSIC, state.playingFocus)
        assertEquals(GhostyAssets.HEADPHONE, state.poseAssetPath)
        assertNull(state.message)
    }

    @Test
    fun `mixer playing without music resolves to PLAYING with meditate pose`() {
        val state = resolveGhostyBuddyState(
            isMusicPlaying = false,
            hasLoadedTrack = false,
            isMixerPlaying = true,
            hasActiveMixerSounds = true,
            isSleepTimerActive = false,
            isSleepTimerFadingOut = false,
            sleepTimerRemainingMillis = 0L
        )

        assertEquals(GhostyBuddyStatus.PLAYING, state.status)
        assertEquals(GhostyPlayingFocus.AMBIENT_MIXER, state.playingFocus)
        assertEquals(GhostyAssets.MEDITATE, state.poseAssetPath)
        assertNull(state.message)
    }

    @Test
    fun `loaded track or mixer paused resolves to PAUSED with idle pose`() {
        val pausedMusicState = resolveGhostyBuddyState(
            isMusicPlaying = false,
            hasLoadedTrack = true,
            isMixerPlaying = false,
            hasActiveMixerSounds = false,
            isSleepTimerActive = false,
            isSleepTimerFadingOut = false,
            sleepTimerRemainingMillis = 0L
        )
        assertEquals(GhostyBuddyStatus.PAUSED, pausedMusicState.status)
        assertEquals(GhostyAssets.IDLE, pausedMusicState.poseAssetPath)
        assertNull(pausedMusicState.message)

        val pausedMixerState = resolveGhostyBuddyState(
            isMusicPlaying = false,
            hasLoadedTrack = false,
            isMixerPlaying = false,
            hasActiveMixerSounds = true,
            isSleepTimerActive = false,
            isSleepTimerFadingOut = false,
            sleepTimerRemainingMillis = 0L
        )
        assertEquals(GhostyBuddyStatus.PAUSED, pausedMixerState.status)
        assertEquals(GhostyAssets.IDLE, pausedMixerState.poseAssetPath)
    }

    @Test
    fun `active sleep timer resolves to TIMER_ACTIVE with blanket pose and mmss countdown`() {
        val state = resolveGhostyBuddyState(
            isMusicPlaying = true,
            hasLoadedTrack = true,
            isMixerPlaying = false,
            hasActiveMixerSounds = false,
            isSleepTimerActive = true,
            isSleepTimerFadingOut = false,
            sleepTimerRemainingMillis = 24L * 60_000L
        )

        assertEquals(GhostyBuddyStatus.TIMER_ACTIVE, state.status)
        assertEquals(GhostyAssets.BLANKET, state.poseAssetPath)
        assertEquals("Desligando em 24:00", state.message)

        val midCountdownState = resolveGhostyBuddyState(
            isMusicPlaying = true,
            hasLoadedTrack = true,
            isMixerPlaying = false,
            hasActiveMixerSounds = false,
            isSleepTimerActive = true,
            isSleepTimerFadingOut = false,
            sleepTimerRemainingMillis = (14L * 60L + 32L) * 1000L
        )
        assertEquals("Desligando em 14:32", midCountdownState.message)
    }

    @Test
    fun `fade out active resolves to FALLING_ASLEEP with zzz pose and overrides timer active`() {
        val state = resolveGhostyBuddyState(
            isMusicPlaying = true,
            hasLoadedTrack = true,
            isMixerPlaying = true,
            hasActiveMixerSounds = true,
            isSleepTimerActive = true,
            isSleepTimerFadingOut = true,
            sleepTimerRemainingMillis = 15_000L,
            sleepTimerFadeMultiplier = 0.5f
        )

        assertEquals(GhostyBuddyStatus.FALLING_ASLEEP, state.status)
        assertEquals(GhostyAssets.ZZZ, state.poseAssetPath)
        assertEquals("Shhh... hora de dormir.", state.message)
        assertEquals(0.5f, state.timerFadeMultiplier, 0.001f)
    }

    @Test
    fun `timer ended session in current run resolves to SLEEPING with sleep pose and no message`() {
        val state = resolveGhostyBuddyState(
            isMusicPlaying = false,
            hasLoadedTrack = true,
            isMixerPlaying = false,
            hasActiveMixerSounds = true,
            isSleepTimerActive = false,
            isSleepTimerFadingOut = false,
            sleepTimerRemainingMillis = 0L,
            hasSleepSessionEnded = true,
            hasPendingWakeUp = true
        )

        assertEquals(GhostyBuddyStatus.SLEEPING, state.status)
        assertEquals(GhostyAssets.SLEEP, state.poseAssetPath)
        assertNull(state.message)
    }

    @Test
    fun `returning to app after timer ended session resolves to WAKING_UP with star pose`() {
        val state = resolveGhostyBuddyState(
            isMusicPlaying = false,
            hasLoadedTrack = true,
            isMixerPlaying = false,
            hasActiveMixerSounds = false,
            isSleepTimerActive = false,
            isSleepTimerFadingOut = false,
            sleepTimerRemainingMillis = 0L,
            hasSleepSessionEnded = false,
            hasPendingWakeUp = true
        )

        assertEquals(GhostyBuddyStatus.WAKING_UP, state.status)
        assertEquals(GhostyAssets.STAR, state.poseAssetPath)
        assertEquals("Bom descanso.", state.message)
    }

    @Test
    fun `simultaneous music and mixer playing prioritizes music headphone pose`() {
        val state = resolveGhostyBuddyState(
            isMusicPlaying = true,
            hasLoadedTrack = true,
            isMixerPlaying = true,
            hasActiveMixerSounds = true,
            isSleepTimerActive = false,
            isSleepTimerFadingOut = false,
            sleepTimerRemainingMillis = 0L,
            hasSleepSessionEnded = true,
            hasPendingWakeUp = true
        )

        assertEquals(GhostyBuddyStatus.PLAYING, state.status)
        assertEquals(GhostyPlayingFocus.MUSIC, state.playingFocus)
        assertEquals(GhostyAssets.HEADPHONE, state.poseAssetPath)
    }
}
