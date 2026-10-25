package io.github.ricox.boostyvp.core.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerOptionsTest {
    private val language = io.github.ricox.boostyvp.localization.Language.selected
    @org.junit.Before fun chooseRussian() { io.github.ricox.boostyvp.localization.Language.selected = "ru" }
    @org.junit.After fun restoreLanguage() { io.github.ricox.boostyvp.localization.Language.selected = language }
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

    @Test fun newOptionsKeepTheOldBehaviourByDefault() {
        val options = PlayerOptions()
        assertEquals(EndAction.Stop, options.endAction)
        assertEquals(0L, options.sleepTimerMs)
        assertEquals(true, options.rememberQuality)
        assertEquals(false, options.speedPerAuthor)
    }

    @Test fun unknownStoredValuesFallBackSafely() {
        assertEquals(EndAction.Stop, EndAction.fromId("teleport"))
        assertEquals(EndAction.Next, EndAction.fromId("next"))
        assertEquals(0, PlayerOptions(sleepTimerMinutes = 45).normalized().sleepTimerMinutes)
        assertEquals(7_200_000L, PlayerOptions(sleepTimerMinutes = 120).normalized().sleepTimerMs)
    }

    @Test fun endActionsAndSleepTimesCycle() {
        assertEquals(EndAction.Next, EndAction.entries.nextItem(EndAction.Stop))
        assertEquals(EndAction.Stop, EndAction.entries.nextItem(EndAction.Close))
        assertEquals(listOf("выкл", "30 мин", "1 ч", "2 ч", "3 ч"), PlayerOptions.SLEEP_TIMES.map(PlayerOptions::sleepLabel))
    }

    @Test fun remoteMappingsAndExitConfirmationAreOptIn() {
        val defaults = PlayerOptions()
        assertEquals(false, defaults.channelVideoNavigation)
        assertEquals(false, defaults.transportVideoNavigation)
        assertEquals(false, defaults.digitSeek)
        assertEquals(false, defaults.doubleBackExit)
        assertEquals(VideoScale.Fit, defaults.videoScale)
    }

    @Test fun geometryChoicesPersistByStableIdsWithSafeFallback() {
        VideoScale.entries.forEach { assertEquals(it, VideoScale.fromId(it.id)) }
        assertEquals(VideoScale.Fit, VideoScale.fromId("unknown"))
        assertEquals(VideoScale.Fit, VideoScale.fromId(null))
        assertEquals(VideoScale.Fit, VideoScale.entries.nextItem(VideoScale.Crop))
    }
}
