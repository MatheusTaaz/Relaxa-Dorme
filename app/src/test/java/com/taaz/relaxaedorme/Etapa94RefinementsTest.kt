package com.taaz.relaxaedorme

import com.taaz.relaxaedorme.audio.MusicPlaybackState
import com.taaz.relaxaedorme.data.model.MusicTrack
import com.taaz.relaxaedorme.ui.components.formatBuddyTimerRemaining
import com.taaz.relaxaedorme.ui.screens.home.HomeTimeOfDayPeriod
import com.taaz.relaxaedorme.ui.screens.home.resolveHomeGreeting
import com.taaz.relaxaedorme.ui.screens.home.resolveHomePeriod
import com.taaz.relaxaedorme.ui.screens.music.shouldShowExpandToggle
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Etapa94RefinementsTest {

    @Test
    fun `global shuffle gathers tracks from multiple albums and avoids immediate repeat`() {
        val tracks = listOf(
            MusicTrack(
                id = "nordic_1",
                albumId = "nordic_fantasy",
                title = "Aurora",
                artist = "mTaaz",
                remoteAudioPath = "albums/nordic/1.mp3",
                durationMs = 180_000L
            ),
            MusicTrack(
                id = "nordic_2",
                albumId = "nordic_fantasy",
                title = "Fjord",
                artist = "mTaaz",
                remoteAudioPath = "albums/nordic/2.mp3",
                durationMs = 190_000L
            ),
            MusicTrack(
                id = "elemental_1",
                albumId = "elemental_dreams",
                title = "Rain",
                artist = "mTaaz",
                remoteAudioPath = "albums/elemental/1.mp3",
                durationMs = 240_000L
            ),
            MusicTrack(
                id = "elemental_2",
                albumId = "elemental_dreams",
                title = "Wind",
                artist = "mTaaz",
                remoteAudioPath = "albums/elemental/2.mp3",
                durationMs = 260_000L
            )
        )

        val shuffled = MusicPlaybackState.generateShuffledQueue(
            tracks = tracks,
            avoidFirstTrackId = "nordic_1",
            random = Random(42)
        )

        assertEquals(tracks.size, shuffled.size)
        assertEquals(tracks.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
        assertNotEquals("nordic_1", shuffled.first().id)
        assertEquals(2, shuffled.map { it.albumId }.toSet().size)
    }

    @Test
    fun `home period and greeting adapt across morning afternoon and night`() {
        assertEquals(HomeTimeOfDayPeriod.MORNING, resolveHomePeriod(5))
        assertEquals(HomeTimeOfDayPeriod.MORNING, resolveHomePeriod(11))
        assertEquals(HomeTimeOfDayPeriod.AFTERNOON, resolveHomePeriod(12))
        assertEquals(HomeTimeOfDayPeriod.AFTERNOON, resolveHomePeriod(17))
        assertEquals(HomeTimeOfDayPeriod.NIGHT, resolveHomePeriod(18))
        assertEquals(HomeTimeOfDayPeriod.NIGHT, resolveHomePeriod(23))
        assertEquals(HomeTimeOfDayPeriod.NIGHT, resolveHomePeriod(0))
        assertEquals(HomeTimeOfDayPeriod.NIGHT, resolveHomePeriod(4))

        val morning = resolveHomeGreeting(hourOfDay = 8, seed = 2)
        val afternoon = resolveHomeGreeting(hourOfDay = 15, seed = 2)
        val night = resolveHomeGreeting(hourOfDay = 22, seed = 2)

        assertEquals("Bom dia!", morning.title)
        assertEquals("Boa tarde!", afternoon.title)
        assertEquals("Boa noite!", night.title)
        assertTrue(morning.subtitle.isNotBlank())
        assertTrue(afternoon.subtitle.isNotBlank())
        assertTrue(night.subtitle.isNotBlank())

        // Estável para a mesma combinação de período e seed durante recomposições
        assertEquals(morning, resolveHomeGreeting(hourOfDay = 9, seed = 2))
    }

    @Test
    fun `buddy timer countdown formats mmss and hmmss accurately`() {
        assertEquals("14:32", formatBuddyTimerRemaining((14L * 60L + 32L) * 1000L))
        assertEquals("00:05", formatBuddyTimerRemaining(5_000L))
        assertEquals("1:05:09", formatBuddyTimerRemaining((3600L + 5L * 60L + 9L) * 1000L))
    }

    @Test
    fun `expandable album description toggle appears only when overflowing or expanded`() {
        assertFalse(shouldShowExpandToggle(hasOverflow = false, isExpanded = false))
        assertTrue(shouldShowExpandToggle(hasOverflow = true, isExpanded = false))
        assertTrue(shouldShowExpandToggle(hasOverflow = false, isExpanded = true))
        assertTrue(shouldShowExpandToggle(hasOverflow = true, isExpanded = true))
    }
}
