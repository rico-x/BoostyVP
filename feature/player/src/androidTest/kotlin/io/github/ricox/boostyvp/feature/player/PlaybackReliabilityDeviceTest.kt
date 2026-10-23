package io.github.ricox.boostyvp.feature.player

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Screen-on, "Далее" and the sleep timer notice on the real remote path, with no network or decoder. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackReliabilityDeviceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: TvPlayerView
    private lateinit var player: StatePlayer
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun wakeDevice() {
        device.wakeUp()
        device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
    }

    @Test fun screenStaysOnOnlyWhileTheVideoPlays() {
        screen()
        compose.runOnIdle { assertTrue("Playing", view.keepScreenOn) }
        compose.runOnIdle { player.update(playWhenReady = true, state = Player.STATE_BUFFERING) }
        compose.runOnIdle { assertTrue("Buffering towards playback", view.keepScreenOn) }
        compose.runOnIdle { player.update(playWhenReady = false, state = Player.STATE_READY) }
        compose.runOnIdle { assertFalse("Paused", view.keepScreenOn) }
        compose.runOnIdle { player.update(playWhenReady = true, state = Player.STATE_ENDED) }
        compose.runOnIdle { assertFalse("Finished", view.keepScreenOn) }
        compose.runOnIdle { player.update(playWhenReady = true, state = Player.STATE_READY) }
        compose.runOnIdle {
            assertTrue("Playing again", view.keepScreenOn)
            view.player = null
            assertFalse("Released", view.keepScreenOn)
        }
    }

    @Test fun okPlaysTheNextVideoAtOnceWithoutRevealingControls() {
        screen()
        var played = 0
        var cancelled = 0
        compose.runOnIdle {
            view.showNextUp("Следующее видео автора", 8, onPlay = { played++ }, onCancel = { cancelled++ })
            assertTrue(view.isNextUpShown)
            assertFalse(view.isControllerFullyVisible)
            val texts = texts(nextUp())
            assertTrue(texts.toString(), texts.contains("Следующее видео автора"))
            assertTrue(texts.toString(), texts.any { it.startsWith("Через 8 с") })
        }
        // Drawn on screen, not only marked visible: laid out inside the player with a real size.
        compose.waitUntil(3_000) { compose.runOnIdle { nextUp().isShown && nextUp().isLaidOut && nextUp().height > 0 } }
        compose.runOnIdle {
            val box = android.graphics.Rect()
            assertTrue("On screen", nextUp().getGlobalVisibleRect(box))
            assertTrue("$box", box.width() > 300 && box.bottom <= view.height)
        }
        Thread.sleep(300)
        device.takeScreenshot(File(InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null), "player-next-up.png"))
        device.pressDPadCenter()
        compose.waitUntil(3_000) { compose.runOnIdle { played == 1 } }
        compose.runOnIdle {
            assertFalse(view.isNextUpShown)
            assertEquals(0, cancelled)
            assertFalse("The release of OK does not reach Media3", view.isControllerFullyVisible)
        }
    }

    @Test fun anyOtherKeyCancelsTheCountdown() {
        screen()
        var played = 0
        var cancelled = 0
        compose.runOnIdle {
            view.showNextUp("Следующее", 2, onPlay = { played++ }, onCancel = { cancelled++ })
        }
        device.pressDPadDown()
        compose.waitUntil(3_000) { compose.runOnIdle { cancelled == 1 } }
        compose.runOnIdle { assertFalse(view.isNextUpShown) }
        // Past the countdown nothing starts by itself.
        Thread.sleep(2_500)
        compose.runOnIdle { assertEquals(0, played) }
    }

    @Test fun theCountdownPlaysTheNextVideo() {
        screen()
        var played = 0
        compose.runOnIdle { view.showNextUp("Следующее", 1, onPlay = { played++ }, onCancel = {}) }
        compose.waitUntil(4_000) { compose.runOnIdle { played == 1 } }
        compose.runOnIdle { assertFalse(view.isNextUpShown) }
    }

    @Test fun backCancelsNextUpBeforeClosingThePlayer() {
        screen()
        var played = 0
        var cancelled = 0
        compose.runOnIdle {
            view.showNextUp("Следующее", 2, onPlay = { played++ }, onCancel = {
                cancelled++
                view.showController()
            })
        }
        device.pressBack()
        compose.waitUntil(3_000) { compose.runOnIdle { cancelled == 1 } }
        compose.runOnIdle {
            assertTrue("Back cancels the offer, not the player", view.isAttachedToWindow)
            assertFalse(view.isNextUpShown)
            assertTrue(view.isControllerFullyVisible)
        }
        Thread.sleep(2_500)
        compose.runOnIdle { assertEquals(0, played) }
    }

    @Test fun theSleepNoticeGoesAwayOnTheNextKey() {
        screen()
        val before = compose.runOnIdle {
            view.showNotice("Таймер сна: воспроизведение остановлено")
            assertEquals(View.VISIBLE, notice().visibility)
            assertEquals("Таймер сна: воспроизведение остановлено", notice().text.toString())
            assertFalse(notice().isFocusable)
            view.lastInteractionAt
        }
        compose.waitUntil(3_000) { compose.runOnIdle { notice().isShown && notice().isLaidOut && notice().width > 0 } }
        Thread.sleep(300)
        device.takeScreenshot(File(InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null), "player-sleep-notice.png"))
        device.pressDPadDown()
        compose.waitUntil(3_000) { compose.runOnIdle { notice().visibility == View.GONE } }
        compose.runOnIdle { assertTrue("A key press restarts the sleep timer", view.lastInteractionAt > before) }
    }

    @Test fun aNewPlayerViewKeepsTheSessionsLastRemotePress() {
        val session = PlaybackInteraction(0)
        screen(session)
        device.pressDPadDown()
        val lastInput = compose.runOnIdle { view.lastInteractionAt }
        assertTrue(lastInput > 0)
        compose.runOnIdle {
            // Autoplay and link recovery construct the next view with the same session.
            val replacement = TvPlayerView(view.context, session)
            assertEquals(lastInput, replacement.lastInteractionAt)
            assertTrue(sleepTimerDue(lastInput + 1_800_000, replacement.lastInteractionAt, 1_800_000, true))
            session.recordKey(lastInput + 1_800_000)
            assertFalse(sleepTimerDue(lastInput + 1_801_000, replacement.lastInteractionAt, 1_800_000, true))
        }
    }

    private fun nextUp() = view.findViewWithTag<NextUpOverlay>(NextUpOverlay.TAG)
    private fun notice() = view.findViewWithTag<PlayerNotice>(PlayerNotice.TAG)

    private fun texts(group: android.view.ViewGroup) =
        (0 until group.childCount).mapNotNull { (group.getChildAt(it) as? TextView)?.text?.toString() }

    private fun screen(interaction: PlaybackInteraction = PlaybackInteraction(android.os.SystemClock.uptimeMillis())) {
        player = StatePlayer()
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            if (open) Dialog(
                onDismissRequest = { open = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                TvPlayerHost(
                    onDismiss = { open = false },
                    factory = { context ->
                        TvPlayerView(context, interaction).apply {
                            view = this
                            this.player = this@PlaybackReliabilityDeviceTest.player
                            controllerShowTimeoutMs = 0
                            applyPlayerAppearance(this, "Тестовое видео")
                            hideController()
                        }
                    },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitUntil(5_000) {
            compose.runOnIdle { ::view.isInitialized && view.hasWindowFocus() && view.hasFocus() }
        }
    }

    /** A two-minute video whose play/pause and playback state the test sets. */
    class StatePlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        private var playWhenReady = true
        private var state = Player.STATE_READY

        fun update(playWhenReady: Boolean, state: Int) {
            this.playWhenReady = playWhenReady
            this.state = state
            invalidateState()
        }

        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAll(
                Player.COMMAND_PLAY_PAUSE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
            ).build())
            .setPlaybackState(state)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(listOf(MediaItemData.Builder("test-video").setDurationUs(120_000_000).build()))
            .setContentPositionMs(40_000)
            .build()

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            this.playWhenReady = playWhenReady
            return Futures.immediateVoidFuture()
        }
    }
}
