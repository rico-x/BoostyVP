package io.github.ricox.boostyvp.feature.player

import org.junit.Assert.*
import org.junit.Test

class PlayerClockTest {
    @Test fun endEstimateRespectsPositionAndSpeed() {
        assertEquals(180_000L, estimatedEndTimeMs(100_000, 40_000, 120_000, 1f))
        assertEquals(140_000L, estimatedEndTimeMs(100_000, 40_000, 120_000, 2f))
        assertEquals(260_000L, estimatedEndTimeMs(100_000, 40_000, 120_000, 0.5f))
    }
    @Test fun unknownDurationOrInvalidSpeedHasNoEndEstimate() {
        assertNull(estimatedEndTimeMs(0, 0, -9223372036854775807L, 1f))
        assertNull(estimatedEndTimeMs(0, 0, 0, 1f))
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertNull(estimatedEndTimeMs(0, 0, 10_000, it))
        }
    }
    @Test fun completedVideoHasNoNegativeRemainingTime() {
        assertEquals(100_000L, estimatedEndTimeMs(100_000, 150_000, 120_000, 1f))
        assertEquals(220_000L, estimatedEndTimeMs(100_000, -10, 120_000, 1f))
    }
}
