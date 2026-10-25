package io.github.ricox.boostyvp.core.ui.component

import org.junit.Assert.*
import org.junit.Test

class OpeningOkGuardTest {
    @Test fun openerRepeatsAndZeroRepeatReleaseNeverActivateTheFirstItem() {
        val guard = OpeningOkGuard()
        assertTrue(guard.consume(true, 1))
        assertTrue(guard.consume(true, 5))
        assertTrue(guard.consume(false, 0))
        assertFalse(guard.consume(true, 0))
        assertFalse(guard.consume(false, 0))
    }
    @Test fun openerWithoutAnyRepeatsStillConsumesTheRelease() {
        assertTrue(OpeningOkGuard().consume(false, 0))
    }
    @Test fun menuAndAccessibilityOpeningDoNotEatTheNextFreshClick() {
        val guard = OpeningOkGuard()
        assertFalse(guard.consume(true, 0))
        assertFalse(guard.consume(false, 0))
    }
}
