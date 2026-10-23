package io.github.ricox.boostyvp.feature.player

import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
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
import android.widget.TextView
import androidx.media3.ui.R as MediaR

/** ←/→ on the real TV remote path, with no account, network or decoder. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RemoteSeekDeviceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: TvPlayerView
    private lateinit var player: SeekablePlayer
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun wakeDevice() {
        device.wakeUp()
        device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
    }

    @Test fun arrowsWithHiddenControlsSeekAStepAndShowTheIndicator() {
        screen()
        hideControls()
        device.pressDPadRight()
        compose.waitUntil(3_000) { compose.runOnIdle { player.currentPosition == 70_000L } }
        compose.runOnIdle {
            assertFalse("Controls stay hidden", view.isControllerFullyVisible)
            val indicator = indicator()
            assertEquals(View.VISIBLE, indicator.visibility)
            val texts = (0 until indicator.childCount).mapNotNull { (indicator.getChildAt(it) as? TextView)?.text?.toString() }
            assertTrue(texts.toString(), texts.any { it.endsWith("+0:30") })
            assertTrue(texts.toString(), texts.contains("1:10 / 2:00"))
            assertFalse(indicator.isFocusable)
            assertTrue("The player keeps the remote", view.hasFocus())
        }
        device.takeScreenshot(File(InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null), "player-seek-indicator.png"))
        compose.waitUntil(3_000) { compose.runOnIdle { indicator().visibility == View.GONE } }

        device.pressDPadLeft()
        compose.waitUntil(3_000) { compose.runOnIdle { player.currentPosition == 60_000L } }
        compose.runOnIdle { assertEquals(2, player.seeks) }
    }

    @Test fun quickTapsAddUpFromTheLastTarget() {
        screen()
        hideControls()
        repeat(3) { device.pressDPadRight() }
        compose.waitUntil(3_000) { compose.runOnIdle { player.currentPosition == 120_000L } }
        compose.runOnIdle {
            val texts = (0 until indicator().childCount)
                .mapNotNull { (indicator().getChildAt(it) as? TextView)?.text?.toString() }
            assertTrue(texts.toString(), texts.any { it.endsWith("+1:20") || it.endsWith("+1:30") })
        }
    }

    @Test fun turningAroundCountsFromZero() {
        screen()
        hideControls()
        device.pressDPadRight()
        device.pressDPadLeft()
        compose.waitUntil(3_000) { compose.runOnIdle { player.seeks == 2 } }
        compose.runOnIdle {
            assertEquals("Back is taken from the forward target", 60_000L, player.currentPosition)
            val texts = (0 until indicator().childCount)
                .mapNotNull { (indicator().getChildAt(it) as? TextView)?.text?.toString() }
            assertTrue(texts.toString(), texts.any { it.endsWith("−0:10") })
        }
    }

    @Test fun holdingAcceleratesAndSeeksOnlyOnRelease() {
        screen(durationMs = 3_600_000)
        hideControls()
        var releasedAt = 0L
        compose.runOnIdle {
            val down = SystemClock.uptimeMillis()
            assertTrue(view.dispatchKeyEvent(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0)))
            var t = down + 400
            var repeat = 1
            while (t <= down + 4_000) {
                assertTrue(view.dispatchKeyEvent(KeyEvent(down, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, repeat++)))
                t += 50
            }
            assertEquals("No seek while the key is held", 0, player.seeks)
            assertEquals(40_000L, player.currentPosition)
            assertFalse(view.isControllerFullyVisible)
            assertTrue(view.dispatchKeyEvent(KeyEvent(down, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0)))
            releasedAt = t
        }
        compose.waitUntil(3_000) { compose.runOnIdle { player.seeks == 1 } }
        val forward = compose.runOnIdle {
            // One step, 1.5 s at 2 steps/s and 2 s at 6 steps/s, with 30 s steps.
            assertTrue("${player.currentPosition}", player.currentPosition > 40_000 + 30_000 * 10)
            assertFalse(view.isControllerFullyVisible)
            player.currentPosition
        }
        // Back straight after the hold: the shown amount starts at zero, not at the forward total.
        compose.runOnIdle {
            val back = releasedAt + 300
            assertTrue(view.dispatchKeyEvent(KeyEvent(back, back, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0)))
            val texts = (0 until indicator().childCount)
                .mapNotNull { (indicator().getChildAt(it) as? TextView)?.text?.toString() }
            assertTrue(texts.toString(), texts.any { it.endsWith("\u22120:10") })
            assertTrue(view.dispatchKeyEvent(KeyEvent(back, back + 80, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT, 0)))
        }
        compose.waitUntil(3_000) { compose.runOnIdle { player.seeks == 2 } }
        compose.runOnIdle { assertEquals(forward - 10_000, player.currentPosition) }
    }

    @Test fun visibleControlsKeepNativeArrowNavigation() {
        screen()
        val focused = compose.runOnIdle {
            assertTrue(view.isControllerFullyVisible)
            assertTrue(view.findViewById<View>(MediaR.id.exo_play_pause).requestFocus())
            view.findFocus()
        }
        device.pressDPadRight()
        compose.runOnIdle {
            assertEquals("No seek through the visible panel", 0, player.seeks)
            assertTrue(view.isControllerFullyVisible)
            assertTrue("Focus moved to another control", view.findFocus() != focused)
            assertEquals(View.GONE, indicator().visibility)
        }
    }

    @Test fun unseekableVideoStillRevealsControls() {
        screen(seekable = false)
        hideControls()
        device.pressDPadRight()
        compose.waitUntil(3_000) { compose.runOnIdle { view.isControllerFullyVisible } }
        compose.runOnIdle {
            assertEquals(0, player.seeks)
            assertEquals(View.GONE, indicator().visibility)
        }
    }

    @Test fun afterTheControlsTimeOutArrowsSeek() {
        // Media3 keeps the controls up while paused; they time out only during playback.
        screen(playing = true)
        compose.runOnIdle {
            view.controllerShowTimeoutMs = 150
            view.showController()
        }
        compose.waitUntil(3_000) { compose.runOnIdle { !view.isControllerFullyVisible } }
        val before = compose.runOnIdle { player.currentPosition }
        device.pressDPadLeft()
        compose.waitUntil(3_000) { compose.runOnIdle { player.seeks == 1 } }
        // Playing on: the position keeps moving, so only the jump back is exact enough.
        compose.runOnIdle { assertTrue("${player.currentPosition} < $before", player.currentPosition < before) }
        compose.runOnIdle { assertFalse(view.isControllerFullyVisible) }
        // Up, down and OK still bring the controls back.
        device.pressDPadCenter()
        compose.waitUntil(3_000) { compose.runOnIdle { view.isControllerFullyVisible } }
    }

    private fun indicator() = view.findViewWithTag<SeekIndicator>(SeekIndicator.TAG)

    private fun hideControls() {
        compose.runOnIdle {
            view.hideController()
            view.requestFocus()
        }
        compose.waitUntil(2_000) { compose.runOnIdle { !view.isControllerFullyVisible && view.hasFocus() } }
    }

    private fun screen(durationMs: Long = 120_000, seekable: Boolean = true, playing: Boolean = false) {
        player = SeekablePlayer(durationMs, seekable, playing)
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            if (open) Dialog(
                onDismissRequest = { open = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                TvPlayerHost(
                    onDismiss = { open = false },
                    factory = { context ->
                        TvPlayerView(context).apply {
                            view = this
                            this.player = this@RemoteSeekDeviceTest.player
                            controllerShowTimeoutMs = 0
                            keepScreenOn = true
                            applyPlayerAppearance(this, "Тестовое видео")
                            showController()
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

    /** Held at 40 s with the app's default steps (10 s back, 30 s forward); counts seeks. */
    class SeekablePlayer(private val durationMs: Long, private val seekable: Boolean, private val playing: Boolean) :
        SimpleBasePlayer(Looper.getMainLooper()) {
        var seeks = 0
        private var positionMs = 40_000L

        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAll(
                Player.COMMAND_PLAY_PAUSE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            ).build())
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(listOf(MediaItemData.Builder("test-video")
                .setIsSeekable(seekable).setDurationUs(durationMs * 1_000).build()))
            .setContentPositionMs(positionMs)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> =
            Futures.immediateVoidFuture()

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            seeks++
            this.positionMs = positionMs.coerceIn(0, durationMs)
            return Futures.immediateVoidFuture()
        }
    }
}
