package io.github.ricox.boostyvp.feature.player

import android.view.KeyEvent
import android.widget.TextView
import android.widget.Button
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ricox.boostyvp.core.ui.player.PlayerOptions
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StreamStatisticsDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var view: TvPlayerView
    private lateinit var player: ExoPlayer
    private var dismissed = false

    @Before fun wake() { device.wakeUp(); device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP) }
    @Test fun infoShowsActualDecodedFormatAndBackHidesItWithoutExitingVideo() {
        screen()
        compose.waitUntil(10_000) { compose.runOnIdle { player.videoFormat != null && player.isPlaying } }
        device.pressKeyCode(KeyEvent.KEYCODE_INFO)
        compose.waitUntil(4_000) { compose.runOnIdle { info().isShown && (info().text.contains("video/avc") || info().text.contains("avc1")) } }
        compose.runOnIdle {
            val format = player.videoFormat!!
            assertTrue(info().text.toString(), info().text.contains("${format.width} × ${format.height}"))
            assertTrue(info().text.contains("Буфер:"))
            assertTrue(info().text.contains("Пропущено кадров:"))
            assertFalse(info().isFocusable)
            assertFalse(info().text.contains("asset:"))
            assertFalse(info().text.contains("http"))
        }
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE)
        compose.waitUntil(3_000) { compose.runOnIdle { !player.playWhenReady } }
        device.pressBack()
        compose.runOnIdle { assertFalse(info().isShown); assertFalse(dismissed) }
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY)
        compose.waitUntil(3_000) { compose.runOnIdle { player.isPlaying } }
        device.pressBack()
        // Media3 may reveal the transport panel on MEDIA_PLAY. Its first Back
        // still hides that panel; only Back with no panels exits the video.
        compose.waitUntil(3_000) { compose.runOnIdle { dismissed || !view.isControllerFullyVisible } }
        if (!dismissed) device.pressBack()
        compose.waitUntil(3_000) { dismissed }
    }
    @Test fun streamButtonTogglesTheSameOverlayWithoutMovingFocusOrPausing() {
        screen()
        compose.waitUntil(10_000) { compose.runOnIdle { player.videoFormat != null && player.isPlaying } }
        compose.runOnIdle {
            view.showController()
            val button = view.findViewWithTag<Button>("stream_statistics_button")
            assertTrue(button.requestFocus())
        }
        device.pressDPadCenter()
        compose.runOnIdle { assertTrue(info().isShown); assertTrue(player.playWhenReady); assertFalse(dismissed) }
        device.pressDPadCenter()
        compose.runOnIdle { assertFalse(info().isShown); assertTrue(player.playWhenReady) }
    }

    @Test fun closingStatisticsDoesNotCountAsFirstBackForDoubleExit() {
        screen(PlayerOptions(doubleBackExit = true))
        compose.waitUntil(10_000) { compose.runOnIdle { player.videoFormat != null && player.isPlaying } }
        compose.runOnIdle { view.hideController(); view.requestFocus() }
        device.pressKeyCode(KeyEvent.KEYCODE_INFO)
        compose.runOnIdle { assertTrue(info().isShown) }
        device.pressBack()
        compose.runOnIdle {
            assertFalse(info().isShown)
            assertFalse(dismissed)
            assertFalse(view.findViewWithTag<TextView>(PlayerNotice.TAG).isShown)
        }
        device.pressBack()
        compose.runOnIdle {
            assertFalse(dismissed)
            assertTrue(view.findViewWithTag<TextView>(PlayerNotice.TAG).text.contains("ещё раз"))
        }
        device.pressBack()
        compose.waitUntil(3_000) { dismissed }
    }
    private fun info() = view.findViewWithTag<TextView>("stream_statistics")
    private fun screen(options: PlayerOptions = PlayerOptions()) {
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            if (open) Dialog(onDismissRequest = { dismissed = true; open = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                TvPlayerHost(factory = { context ->
                    player = ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri("asset:///remote-control-test.mp4"))
                        prepare(); playWhenReady = true
                    }
                    TvPlayerView(context).apply {
                        view = this; this.player = this@StreamStatisticsDeviceTest.player
                        configureControls(options, {}, {})
                        controllerShowTimeoutMs = 0; hideController()
                    }
                }, onDismiss = { dismissed = true; open = false },
                    onRelease = { it.player = null; player.release() }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
