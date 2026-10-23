package ru.ardcode.boostytv.feature.player

import android.content.Context
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.media3.ui.R as MediaR

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerControlsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: TvPlayerView
    private var dismissed = false
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun wakeDevice() { device.wakeUp() }

    @Test fun realVideoRendersPausesAndResumesFromRemote() {
        var renderedFrame = false
        screen(createPlayer = { context ->
            ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { renderedFrame = true }
                })
                setMediaItem(MediaItem.fromUri("asset:///remote-control-test.mp4"))
                prepare()
                playWhenReady = true
            }
        })
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.runOnIdle { renderedFrame && view.player!!.isPlaying }
        }
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE)
        compose.waitUntil(timeoutMillis = 3_000) { compose.runOnIdle { !view.player!!.isPlaying } }
        val pausedAt = compose.runOnIdle { view.player!!.currentPosition }
        SystemClock.sleep(600)
        compose.runOnIdle {
            assertEquals("Decoded video stays paused", pausedAt, view.player!!.currentPosition)
            assertFalse("Pause never closes playback", dismissed)
        }
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY)
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.runOnIdle { view.player!!.isPlaying && view.player!!.currentPosition > pausedAt + 300 }
        }
    }

    @Test fun playerAddedAfterLoadingAcceptsPauseSeekAndDpad() {
        screen(afterLoading = true)
        compose.runOnIdle { view.hideController() }
        device.pressDPadCenter()
        compose.waitUntil(timeoutMillis = 3_000) { compose.runOnIdle { view.isControllerFullyVisible } }
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE)
        compose.waitUntil(timeoutMillis = 3_000) { compose.runOnIdle { !view.player!!.playWhenReady } }
        val before = compose.runOnIdle { view.player!!.currentPosition }
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.runOnIdle { view.player!!.currentPosition >= before + 29_000 }
        }
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_REWIND)
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.runOnIdle { view.player!!.currentPosition < before + 21_000 }
        }
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY)
        compose.waitUntil(timeoutMillis = 3_000) { compose.runOnIdle { view.player!!.playWhenReady } }
        compose.runOnIdle { assertFalse("Transport keys do not dismiss video", dismissed) }
    }

    @Test fun secondaryWindowDoesNotBreakNativeFocusOrPause() {
        screen(afterLoading = true)
        val secondary = compose.runOnIdle {
            ComponentDialog(view.context).apply {
                setContentView(Button(context).apply { text = "Secondary window" })
                show()
            }
        }
        compose.waitUntil(timeoutMillis = 3_000) { compose.runOnIdle { !view.hasWindowFocus() } }
        compose.runOnIdle { secondary.dismiss() }
        compose.waitUntil(timeoutMillis = 3_000) { compose.runOnIdle { view.hasWindowFocus() && view.hasFocus() } }
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE)
        compose.waitUntil(timeoutMillis = 3_000) { compose.runOnIdle { !view.player!!.playWhenReady } }
        compose.runOnIdle { assertTrue("Native player regains focus", view.hasFocus()) }
    }

    @Test fun backHidesControlsBeforeClosingVideoAndDpadCanShowThemAgain() {
        screen()
        device.pressBack()
        awaitHiddenControlsOrDismissal()
        compose.runOnIdle {
            assertFalse("First Back keeps the video open", dismissed)
            assertOverlayHidden()
            assertTrue("Hidden video surface retains D-pad focus", view.hasFocus())
        }

        device.pressDPadCenter()
        compose.waitUntil(timeoutMillis = 2_000) { compose.runOnIdle { view.isControllerFullyVisible } }
        compose.runOnIdle { assertTrue("D-pad restores controls", view.isControllerFullyVisible) }
        device.pressBack()
        awaitHiddenControlsOrDismissal()
        compose.runOnIdle {
            assertFalse(dismissed)
            assertOverlayHidden()
        }
        device.pressBack()
        compose.waitUntil(timeoutMillis = 2_000) { dismissed }
        compose.runOnIdle { assertTrue("Back with hidden controls closes the video", dismissed) }
    }

    @Test fun timeoutHidesTheWholeOverlayWithoutLeavingTheBottomBar() {
        screen()
        compose.runOnIdle {
            // Configure through PlayerView so later focus/layout updates do not
            // overwrite a timeout set directly on its internal controller.
            view.controllerShowTimeoutMs = 150
            view.showController()
        }
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.runOnIdle { !view.isControllerFullyVisible }
        }
        compose.runOnIdle {
            assertFalse(dismissed)
            assertOverlayHidden()
        }
        device.pressBack()
        compose.waitUntil(timeoutMillis = 2_000) { dismissed }
        compose.runOnIdle { assertTrue("Back after auto-hide closes immediately", dismissed) }
    }

    @Test fun seekAmountsAreCentredInsideTheirArrowIcons() {
        screen()
        compose.runOnIdle {
            listOf(MediaR.id.exo_rew_with_amount to "10", MediaR.id.exo_ffwd_with_amount to "30").forEach { (id, amount) ->
                val button = view.findViewById<Button>(id)
                val text = button.text.toString()
                assertEquals(amount, text)
                val glyphs = Rect()
                button.paint.getTextBounds(text, 0, text.length, glyphs)
                val textCentreY = button.baseline + glyphs.exactCenterY()
                // The arrow's circular part is centred at y=13 in a 24-unit viewport,
                // scaled to 70% around the centre by Media3's vector drawable.
                val arrowCentreY = button.height * (12f + 0.7f) / 24f
                val tolerance = button.resources.displayMetrics.density * 1.5f
                assertEquals("Seek amount is vertically centred", arrowCentreY, textCentreY, tolerance)
                val line = button.layout
                assertEquals("Seek amount is horizontally centred", button.width / 2f,
                    button.compoundPaddingLeft + (line.getLineLeft(0) + line.getLineRight(0)) / 2f, tolerance)
            }
        }
        device.takeScreenshot(File(InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null), "player-controls.png"))
    }

    @Test fun holdingOrCancellingBackDoesNotCloseTheVideo() {
        screen()
        compose.runOnIdle {
            val now = SystemClock.uptimeMillis()
            assertTrue(view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)))
            assertTrue(view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 1)))
            assertTrue(view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0,
                0, -1, 0, KeyEvent.FLAG_CANCELED)))
            assertTrue(view.isControllerFullyVisible)
            assertFalse(dismissed)
        }
        device.pressBack()
        awaitHiddenControlsOrDismissal()
        compose.runOnIdle {
            assertFalse(dismissed)
            assertOverlayHidden()
        }
    }

    private fun assertOverlayHidden() {
        assertEquals(View.GONE, view.findViewById<PlayerControlView>(MediaR.id.exo_controller).visibility)
        listOf(MediaR.id.exo_bottom_bar, MediaR.id.exo_progress, MediaR.id.exo_center_controls).forEach { id ->
            assertFalse("No part of the controls remains visible", view.findViewById<View>(id).isShown)
        }
    }

    private fun awaitHiddenControlsOrDismissal() {
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.runOnIdle { dismissed || !view.isControllerFullyVisible }
        }
    }

    private fun screen(
        afterLoading: Boolean = false,
        createPlayer: (Context) -> Player = { TestPlayer() },
    ) {
        var ready by mutableStateOf(!afterLoading)
        compose.setContent {
            var open by androidx.compose.runtime.remember { mutableStateOf(true) }
            if (open) Dialog(
                onDismissRequest = { dismissed = true; open = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                if (!ready) Box(Modifier.fillMaxSize()) else TvPlayerHost(
                    onDismiss = { dismissed = true; open = false },
                    factory = { context ->
                        TvPlayerView(context).apply {
                            view = this
                            player = createPlayer(context)
                            // Back tests must not race Media3's independent 5s
                            // auto-hide timer while UiAutomator waits for idle.
                            // The timeout test explicitly enables its own timer.
                            controllerShowTimeoutMs = 0
                            keepScreenOn = true
                            applyPlayerAppearance(this, "Тестовое видео")
                            showController()
                        }
                    },
                    onRelease = { it.player?.release(); it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()
        if (afterLoading) {
            // The dialog/Compose owner already holds window focus before the native
            // player is created, matching asynchronous Ready on the real TV.
            compose.runOnIdle { ready = true }
        }
        try {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.runOnIdle { ::view.isInitialized && view.hasWindowFocus() && view.hasFocus() }
            }
        } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            val detail = compose.runOnIdle {
                if (!::view.isInitialized) "Player was never created" else
                    "attached=${view.isAttachedToWindow}, windowFocus=${view.hasWindowFocus()}, " +
                        "nativeFocus=${view.hasFocus()}, rootFocus=${view.rootView.findFocus()?.javaClass?.name}"
            }
            throw AssertionError(detail, error)
        }
    }

    /** Seekable video state with no account, network or decoder involved. */
    private class TestPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        private var playing = true
        private var positionMs = 40_000L
        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAll(
                Player.COMMAND_PLAY_PAUSE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
            ).build())
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(listOf(MediaItemData.Builder("test-video")
                .setIsSeekable(true).setDurationUs(120_000_000).build()))
            .setContentPositionMs(positionMs)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            positionMs = currentPosition
            playing = playWhenReady
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            this.positionMs = positionMs.coerceIn(0, 120_000)
            return Futures.immediateVoidFuture()
        }
    }
}
