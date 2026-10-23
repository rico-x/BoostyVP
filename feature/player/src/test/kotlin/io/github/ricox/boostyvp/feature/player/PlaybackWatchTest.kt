package io.github.ricox.boostyvp.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackWatchTest {
    @Test fun `twenty seconds of buffering within a minute is a stall`() {
        val stalls = StallDetector()
        stalls.bufferingStarted(0)
        assertFalse(stalls.isStalled(19_000))
        assertTrue(stalls.isStalled(20_000))
    }

    @Test fun `short buffering spells add up inside the window`() {
        val stalls = StallDetector()
        listOf(0L, 15_000L, 30_000L).forEach { start ->
            stalls.bufferingStarted(start)
            stalls.bufferingEnded(start + 5_000)
        }
        assertFalse("Not stalled while playing", stalls.isStalled(40_000))
        stalls.bufferingStarted(45_000)
        assertFalse(stalls.isStalled(49_000))
        assertTrue("20 s of buffering since the window began", stalls.isStalled(50_000))
    }

    @Test fun `buffering long ago does not count`() {
        val stalls = StallDetector()
        stalls.bufferingStarted(0)
        stalls.bufferingEnded(15_000)
        stalls.bufferingStarted(120_000)
        assertFalse(stalls.isStalled(130_000))
        assertTrue(stalls.isStalled(140_000))
    }

    @Test fun `reset clears an ongoing stall`() {
        val stalls = StallDetector()
        stalls.bufferingStarted(0)
        assertTrue(stalls.isStalled(25_000))
        stalls.reset()
        assertFalse(stalls.isStalled(26_000))
    }

    @Test fun `a rejected recovery keeps watching the same uninterrupted buffering`() {
        val stalls = StallDetector()
        stalls.bufferingStarted(0)
        var requests = 0
        assertFalse(stalls.recoverIfStalled(20_000) { requests++; false })
        assertTrue("A cooldown must not erase the ongoing stall", stalls.isStalled(22_000))
        assertTrue(stalls.recoverIfStalled(120_000) { requests++; true })
        assertEquals(2, requests)
        assertFalse("An accepted recovery resets the old player's watchdog", stalls.isStalled(122_000))
    }

    @Test fun `a recovered buffer does not request a refresh after the cooldown`() {
        val stalls = StallDetector()
        stalls.bufferingStarted(0)
        assertFalse(stalls.recoverIfStalled(20_000) { false })
        stalls.bufferingEnded(21_000)
        assertFalse(stalls.recoverIfStalled(120_000) { error("Already playing") })
    }

    @Test fun `sleep timer needs playback, a timer and enough quiet time`() {
        assertTrue(sleepTimerDue(nowMs = 3_600_000, lastInputMs = 0, timerMs = 3_600_000, playing = true))
        assertFalse(sleepTimerDue(nowMs = 3_599_999, lastInputMs = 0, timerMs = 3_600_000, playing = true))
        assertFalse("Already paused", sleepTimerDue(3_600_000, 0, 3_600_000, playing = false))
        assertFalse("Off", sleepTimerDue(10_000_000, 0, 0, playing = true))
    }

    @Test fun `one inactivity clock is retained across autoplay and recovery`() {
        val session = PlaybackInteraction(0)
        session.recordKey(1_000)
        val nextVideo = session
        assertFalse(sleepTimerDue(1_200_000, nextVideo.lastInteractionAt, 1_800_000, true))
        val recoveredVideo = session
        assertTrue(sleepTimerDue(1_801_000, recoveredVideo.lastInteractionAt, 1_800_000, true))
        recoveredVideo.recordKey(1_801_000)
        assertFalse(sleepTimerDue(1_802_000, nextVideo.lastInteractionAt, 1_800_000, true))
    }

    @Test fun `remembered quality never asks for more than was chosen`() {
        assertEquals(720, rememberedResolution(listOf(1080, 720, 480), 720))
        assertEquals(720, rememberedResolution(listOf(1080, 720, 480), 1000))
        assertEquals("Everything is higher: the lowest", 480, rememberedResolution(listOf(1080, 480), 360))
        assertNull(rememberedResolution(emptyList(), 720))
    }

    @Test fun `a resume point at the very end starts the video again`() {
        assertEquals(20_000L, resumeStartMs(20_000, 35_000))
        assertEquals("Within the last 10 s", 0L, resumeStartMs(25_000, 35_000))
        assertEquals(0L, resumeStartMs(35_000, 35_000))
        assertEquals("5 % of a long video", 0L, resumeStartMs(920_000, 966_000))
        assertEquals(900_000L, resumeStartMs(900_000, 966_000))
        assertEquals("Unknown duration keeps the point", 50_000L, resumeStartMs(50_000, 0))
        assertEquals(0L, resumeStartMs(0, 35_000))
    }
}
