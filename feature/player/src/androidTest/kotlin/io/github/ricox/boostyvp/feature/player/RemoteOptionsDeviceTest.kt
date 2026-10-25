package io.github.ricox.boostyvp.feature.player

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ricox.boostyvp.core.ui.player.PlayerOptions
import io.github.ricox.boostyvp.core.ui.player.VideoScale
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Physical TV key dispatch, with a disposable synthetic player and no account. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RemoteOptionsDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var view: TvPlayerView
    private lateinit var player: RemoteSeekDeviceTest.SeekablePlayer
    private val navigations = mutableListOf<VideoDirection>()
    private val scales = mutableListOf<VideoScale>()
    private var dismissed = false

    @Before fun wake() { device.wakeUp(); device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP) }

    @Test fun channelAndTransportKeysNavigateOnceWithoutSeeking() {
        screen(PlayerOptions(channelVideoNavigation = true, transportVideoNavigation = true))
        for (key in listOf(KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND)) device.pressKeyCode(key)
        compose.waitUntil(3_000) { compose.runOnIdle { navigations.size == 4 } }
        compose.runOnIdle {
            assertEquals(listOf(VideoDirection.Next, VideoDirection.Previous, VideoDirection.Next, VideoDirection.Previous), navigations)
            assertEquals(0, player.seeks)
            assertFalse(dismissed)
        }
    }

    @Test fun heldCancelledAndOrphanMappedKeysDoNotTriggerExtraNavigation() {
        screen(PlayerOptions(channelVideoNavigation = true))
        compose.runOnIdle {
            val now = SystemClock.uptimeMillis()
            fun event(action: Int, repeat: Int = 0, flags: Int = 0) = KeyEvent(now, now + repeat * 50L,
                action, KeyEvent.KEYCODE_CHANNEL_UP, repeat, 0, -1, 0, flags)
            view.dispatchKeyEvent(event(KeyEvent.ACTION_DOWN))
            repeat(8) { view.dispatchKeyEvent(event(KeyEvent.ACTION_DOWN, it + 1)) }
            assertTrue(navigations.isEmpty())
            view.dispatchKeyEvent(event(KeyEvent.ACTION_UP))
            view.dispatchKeyEvent(event(KeyEvent.ACTION_UP))
            assertEquals(1, navigations.size)
            view.dispatchKeyEvent(event(KeyEvent.ACTION_DOWN))
            view.dispatchKeyEvent(event(KeyEvent.ACTION_UP, flags = KeyEvent.FLAG_CANCELED))
            assertEquals(1, navigations.size)
        }
    }

    @Test fun digitsAndNumpadSeekWithoutStartingPausedPlayback() {
        screen(PlayerOptions(digitSeek = true))
        device.pressKeyCode(KeyEvent.KEYCODE_5)
        compose.waitUntil(3_000) { compose.runOnIdle { player.currentPosition == 60_000L } }
        device.pressKeyCode(KeyEvent.KEYCODE_NUMPAD_9)
        compose.waitUntil(3_000) { compose.runOnIdle { player.currentPosition == 108_000L } }
        device.pressKeyCode(KeyEvent.KEYCODE_0)
        compose.waitUntil(3_000) { compose.runOnIdle { player.currentPosition == 0L } }
        compose.runOnIdle { assertFalse(player.playWhenReady); assertEquals(3, player.seeks) }
    }

    @Test fun unseekableVideoAndDisabledDigitsDoNotJump() {
        screen(PlayerOptions(digitSeek = true), seekable = false)
        device.pressKeyCode(KeyEvent.KEYCODE_5)
        compose.runOnIdle {
            assertEquals(0, player.seeks)
            assertTrue(notice().text.contains("недоступен"))
            view.configureControls(PlayerOptions(), navigations::add, scales::add)
        }
        device.pressKeyCode(KeyEvent.KEYCODE_NUMPAD_5)
        compose.runOnIdle { assertEquals(0, player.seeks); assertTrue(navigations.isEmpty()) }
    }

    @Test fun backHidesControlsBeforeArmingExitAndSecondBackCloses() {
        screen(PlayerOptions(doubleBackExit = true))
        compose.runOnIdle { view.showController() }
        device.pressBack()
        compose.runOnIdle { assertFalse(view.isControllerFullyVisible); assertFalse(dismissed) }
        device.pressBack()
        compose.runOnIdle { assertFalse(dismissed); assertTrue(notice().isShown); assertTrue(notice().text.contains("ещё раз")) }
        device.pressBack()
        compose.waitUntil(3_000) { dismissed }
    }

    @Test fun backConfirmationExpiresAndAnotherKeyDisarmsIt() {
        screen(PlayerOptions(doubleBackExit = true))
        device.pressBack()
        compose.waitUntil(4_000) { compose.runOnIdle { !notice().isShown } }
        device.pressBack()
        compose.runOnIdle { assertFalse(dismissed); assertTrue(notice().isShown) }
        device.pressKeyCode(KeyEvent.KEYCODE_MENU)
        device.pressBack() // only hide the newly opened panel
        device.pressBack() // a new first confirmation, not an exit
        compose.runOnIdle { assertFalse(dismissed); assertTrue(notice().text.contains("ещё раз")) }
    }

    @Test fun holdingBackIsNotTwoPresses() {
        screen(PlayerOptions(doubleBackExit = true))
        compose.runOnIdle {
            val now = SystemClock.uptimeMillis()
            view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))
            repeat(5) { view.dispatchKeyEvent(KeyEvent(now, now + it * 100L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, it + 1)) }
            view.dispatchKeyEvent(KeyEvent(now, now + 600, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0))
            assertFalse(dismissed)
            assertTrue(notice().text.contains("ещё раз"))
        }
    }

    @Test fun scaleButtonCyclesNativeGeometryWithoutScalingTheControls() {
        screen()
        compose.runOnIdle {
            view.showController()
            assertTrue(view.findViewWithTag<Button>("video_scale_button").requestFocus())
        }
        for (scale in listOf(VideoScale.Width, VideoScale.Height, VideoScale.Stretch, VideoScale.Crop, VideoScale.Fit)) {
            device.pressDPadCenter()
            compose.runOnIdle {
                assertEquals(scale.media3ResizeMode, view.resizeMode)
                assertEquals(scale, scales.last())
                assertTrue(notice().text.contains(scale.label))
                assertEquals(1f, view.scaleX, 0f)
                assertEquals(1f, view.scaleY, 0f)
                assertFalse(dismissed)
            }
        }
        compose.runOnIdle {
            val frame = view.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
            frame.setAspectRatio(2.35f)
        }
        compose.waitUntil(3_000) { compose.runOnIdle {
            val frame = view.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
            frame.height < view.height && frame.width == view.width
        } }
        compose.runOnIdle {
            view.configureControls(PlayerOptions(videoScale = VideoScale.Crop), navigations::add, scales::add)
        }
        compose.waitUntil(3_000) { compose.runOnIdle {
            val frame = view.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
            frame.width > view.width && frame.height == view.height
        } }
    }

    private fun notice() = view.findViewWithTag<TextView>(PlayerNotice.TAG)

    private fun screen(options: PlayerOptions = PlayerOptions(), seekable: Boolean = true) {
        player = RemoteSeekDeviceTest.SeekablePlayer(120_000, seekable, false)
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            if (open) Dialog(onDismissRequest = { dismissed = true; open = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                TvPlayerHost(factory = { context ->
                    TvPlayerView(context).apply {
                        view = this
                        this.player = this@RemoteOptionsDeviceTest.player
                        controllerShowTimeoutMs = 0
                        configureControls(options, navigations::add, scales::add)
                        applyPlayerAppearance(this, "Тестовое видео")
                    }
                }, onDismiss = { dismissed = true; open = false },
                    onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
            }
        }
        compose.waitUntil(5_000) { compose.runOnIdle { ::view.isInitialized && view.hasWindowFocus() && view.hasFocus() } }
        compose.runOnIdle { view.hideController(); view.requestFocus() }
    }
}
