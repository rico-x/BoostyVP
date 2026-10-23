package io.github.ricox.boostyvp.core.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerOptionsTest {
    @Test fun defaultsPreserveExistingPlaybackBehaviour() {
        assertEquals(10, PlayerOptions().seekBackSeconds)
        assertEquals(30, PlayerOptions().seekForwardSeconds)
        assertEquals(4_000, PlayerOptions().controllerTimeoutMs)
    }

    @Test fun invalidStoredValuesFallBackToSafeDefaults() {
        assertEquals(PlayerOptions(), PlayerOptions(-1, 900, -30).normalized())
    }

    @Test fun neverHideIsAnExplicitValidChoice() {
        assertEquals(0, PlayerOptions(controllerSeconds = 0).normalized().controllerTimeoutMs)
    }

    @Test fun choicesWrapWithoutGettingStuck() {
        assertEquals(5, PlayerOptions.BACK_STEPS.nextChoice(30))
        assertEquals(10, PlayerOptions.FORWARD_STEPS.nextChoice(-1))
    }

    @Test fun invalidSpeedDoesNotReachTheDecoder() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f, 10f).forEach {
            assertEquals(1f, PlayerOptions.safeSpeed(it), 0f)
        }
        assertEquals(1.5f, PlayerOptions.safeSpeed(1.5f), 0f)
    }
}
