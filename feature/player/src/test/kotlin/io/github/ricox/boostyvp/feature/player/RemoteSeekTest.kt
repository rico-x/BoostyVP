package io.github.ricox.boostyvp.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSeekTest {
    private val seek = RemoteSeek()
    private val duration = 3_600_000L

    /** A press at [at] held until [releaseAt], with key repeats every [repeatMs] as Android sends them. */
    private fun hold(direction: Int, at: Long, releaseAt: Long, position: Long, step: Long = 30_000,
        repeatMs: Long = 50, durationMs: Long = duration): Long? {
        seek.press(direction, step, at, at, position, durationMs, firstEvent = true)
        var t = at + 400
        while (t < releaseAt) {
            seek.press(direction, step, at, t, position, durationMs, firstEvent = false)
            t += repeatMs
        }
        return seek.commit(releaseAt)
    }

    @Test fun `a tap moves exactly one step each way`() {
        assertEquals(70_000L, hold(1, at = 0, releaseAt = 100, position = 40_000))
        assertEquals(30_000L, hold(-1, at = 10_000, releaseAt = 10_100, position = 40_000, step = 10_000))
    }

    @Test fun `quick taps continue from the last target, not the lagging player position`() {
        assertEquals(70_000L, hold(1, at = 0, releaseAt = 100, position = 40_000))
        // The player is still buffering the first seek and reports an old position.
        val preview = seek.press(1, 30_000, 900, 900, 41_000, duration, firstEvent = true)
        assertEquals(100_000L, preview.targetMs)
        assertEquals("The indicator counts the whole chain", 60_000L, preview.deltaMs)
        assertEquals(100_000L, seek.commit(1_000))
    }

    @Test fun `a later tap starts again from where the player is`() {
        assertEquals(70_000L, hold(1, at = 0, releaseAt = 100, position = 40_000))
        val preview = seek.press(1, 30_000, 5_000, 5_000, 75_000, duration, firstEvent = true)
        assertEquals(105_000L, preview.targetMs)
        assertEquals(30_000L, preview.deltaMs)
    }

    @Test fun `holding accelerates and seeks only once on release`() {
        seek.press(1, 30_000, 0, 0, 0, duration, firstEvent = true)
        val samples = mutableMapOf<Long, Long>()
        var t = 400L
        while (t <= 7_000) {
            samples[t] = seek.press(1, 30_000, 0, t, 0, duration, firstEvent = false).targetMs
            t += 50
        }
        fun covered(from: Long, to: Long) = samples.getValue(to) - samples.getValue(from)
        val early = covered(1_000, 2_000)
        val middle = covered(3_000, 4_000)
        val late = covered(6_000, 7_000)
        assertTrue("$early < $middle", early < middle)
        assertTrue("$middle < $late", middle < late)
        assertEquals("Under half a second it is still one step", 30_000L, samples.getValue(400))
        assertEquals(samples.getValue(7_000), seek.commit(7_050))
        assertNull("Nothing left to commit", seek.commit(7_100))
    }

    @Test fun `seconds per second follow the step size`() {
        val short = hold(1, at = 0, releaseAt = 3_000, position = 0, step = 10_000)!!
        val long = hold(1, at = 100_000, releaseAt = 103_000, position = 0, step = 30_000)!!
        assertEquals(3.0, long.toDouble() / short, 0.15)
    }

    @Test fun `a stalled remote cannot turn one late repeat into a jump`() {
        seek.press(1, 30_000, 0, 0, 0, duration, firstEvent = true)
        seek.press(1, 30_000, 0, 1_000, 0, duration, firstEvent = false)
        val before = seek.press(1, 30_000, 0, 1_050, 0, duration, firstEvent = false).targetMs
        val after = seek.press(1, 30_000, 0, 6_050, 0, duration, firstEvent = false).targetMs
        assertTrue(after - before <= 30_000L * 15 * RemoteSeek.MAX_REPEAT_GAP_MS / 1_000)
    }

    @Test fun `targets stay inside the video`() {
        assertEquals(0L, hold(-1, at = 0, releaseAt = 100, position = 5_000, step = 10_000))
        assertEquals(120_000L, hold(1, at = 10_000, releaseAt = 13_000, position = 100_000, durationMs = 120_000))
        // Unknown duration: no upper bound.
        assertEquals(90_000L, hold(1, at = 20_000, releaseAt = 20_100, position = 60_000, durationMs = 0))
    }

    @Test fun `the other arrow while holding turns around from the current target and from zero`() {
        seek.press(1, 30_000, 0, 0, 40_000, duration, firstEvent = true)
        val back = seek.press(-1, 10_000, 200, 200, 40_000, duration, firstEvent = true)
        assertEquals(60_000L, back.targetMs)
        assertEquals(-1, back.direction)
        assertEquals(-10_000L, back.deltaMs)
    }

    @Test fun `back right after holding forward counts down from zero, not through the forward amount`() {
        val forward = hold(1, at = 0, releaseAt = 3_000, position = 40_000)!!
        assertTrue(forward > 40_000 + 30_000 * 4)
        // The player still reports the old position while the forward seek buffers.
        val tap = seek.press(-1, 10_000, 3_300, 3_300, 41_000, duration, firstEvent = true)
        assertEquals(forward - 10_000, tap.targetMs)
        assertEquals(-10_000L, tap.deltaMs)
        // Holding back keeps growing the minus from that same zero.
        var shown = tap.deltaMs
        var t = 3_700L
        while (t <= 5_300) {
            val step = seek.press(-1, 10_000, 3_300, t, 41_000, duration, firstEvent = false)
            assertTrue("${step.deltaMs} <= $shown", step.deltaMs <= shown)
            assertEquals(forward + step.deltaMs, step.targetMs)
            shown = step.deltaMs
            t += 50
        }
        assertTrue(shown < -10_000)
        assertEquals(forward + shown, seek.commit(5_350))
    }

    @Test fun `forward again after back also starts from zero`() {
        assertEquals(30_000L, hold(-1, at = 0, releaseAt = 100, position = 40_000, step = 10_000))
        val forward = seek.press(1, 30_000, 500, 500, 39_000, duration, firstEvent = true)
        assertEquals(60_000L, forward.targetMs)
        assertEquals(30_000L, forward.deltaMs)
    }

    @Test fun `a cancelled press seeks nowhere and ends the chain`() {
        seek.press(1, 30_000, 0, 0, 40_000, duration, firstEvent = true)
        seek.cancel()
        assertNull(seek.commit(100))
        assertEquals(50_000L, seek.press(1, 10_000, 200, 200, 40_000, duration, firstEvent = true).targetMs)
    }

    @Test fun `times read like the player's own labels`() {
        assertEquals("0:05", formatSeekTime(5_400))
        assertEquals("12:34", formatSeekTime(754_000))
        assertEquals("1:02:03", formatSeekTime(3_723_000))
        assertEquals("+1:30", formatSeekDelta(90_000))
        assertEquals("−0:10", formatSeekDelta(-10_000))
    }
}
