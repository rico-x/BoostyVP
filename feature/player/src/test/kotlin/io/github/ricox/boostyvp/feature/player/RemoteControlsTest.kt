package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.core.ui.player.VideoScale
import org.junit.Assert.*
import org.junit.Test

class RemoteControlsTest {
    @Test fun `digits map to zero through ninety percent`() {
        assertEquals((0..9).map { it * 12_000L }, (0..9).map { digitSeekPosition(120_000, it) })
        assertEquals(110L, digitSeekPosition(123, 9))
    }
    @Test fun `unknown duration or invalid digit cannot seek`() {
        for (duration in listOf(0L, -1L, Long.MIN_VALUE)) assertNull(digitSeekPosition(duration, 5))
        assertNull(digitSeekPosition(120_000, -1))
        assertNull(digitSeekPosition(120_000, 10))
    }
    @Test fun `very long durations cannot overflow`() {
        val target = digitSeekPosition(Long.MAX_VALUE, 9)!!
        assertTrue(target > 0 && target < Long.MAX_VALUE)
        assertEquals(8_301_034_833_169_298_226L, target)
    }
    @Test fun `second back confirms and a third starts over`() {
        val exit = ExitConfirmation()
        assertFalse(exit.press(0))
        assertTrue(exit.press(1_500))
        assertFalse(exit.press(1_600))
    }
    @Test fun `expired or reset confirmation needs two new presses`() {
        val exit = ExitConfirmation()
        assertFalse(exit.press(100))
        assertFalse(exit.press(2_101))
        exit.reset()
        assertFalse(exit.press(2_200))
        assertTrue(exit.press(2_300))
    }
    @Test fun `clock moving backwards does not confirm exit`() {
        val exit = ExitConfirmation()
        assertFalse(exit.press(10))
        assertFalse(exit.press(5))
    }
    @Test fun `each geometry mode has a distinct native resize policy`() {
        assertEquals(listOf(0, 1, 2, 3, 4), VideoScale.entries.map { it.media3ResizeMode })
    }
}
