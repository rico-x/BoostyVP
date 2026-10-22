package ru.ardcode.boostytv.feature.player

import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class TvPlayerView(context: Context) : PlayerView(context) {
    private var hidingControlsOnBack = false
    private val restorePlaybackFocus = Runnable {
        // The factory runs before AndroidView is attached. Compose can take focus
        // back while the dialog's loading screen is replaced by this native view.
        if (isAttachedToWindow && hasWindowFocus() && !hasFocus()) requestFocus()
    }
    private val hideControlsOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            hideController()
            requestFocus()
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        // Media3 slides the bottom controls by its stock bar height in two stages.
        // Our inset bar and time bar need to disappear together with the whole overlay.
        setControllerAnimationEnabled(false)
        setControllerVisibilityListener(ControllerVisibilityListener { visibility ->
            hideControlsOnBack.isEnabled = visibility == View.VISIBLE
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The player lives in a Compose navigation dialog, whose dispatcher receives
        // system Back directly on newer Android versions instead of native key events.
        findViewTreeOnBackPressedDispatcherOwner()?.onBackPressedDispatcher
            ?.addCallback(hideControlsOnBack)
        hideControlsOnBack.isEnabled = isControllerFullyVisible
        post(restorePlaybackFocus)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) post(restorePlaybackFocus)
    }

    override fun onDetachedFromWindow() {
        hideControlsOnBack.remove()
        removeCallbacks(restorePlaybackFocus)
        hidingControlsOnBack = false
        super.onDetachedFromWindow()
    }

    /** Bridge only player keys when Compose, rather than a native child, has focus. */
    internal fun dispatchKeyEventFromHost(event: KeyEvent, takeComposeFocus: () -> Unit = {}): Boolean {
        if (hasFocus() || event.keyCode !in PLAYER_KEYS) return false
        takeComposeFocus()
        requestFocus()
        return dispatchKeyEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                hidingControlsOnBack = isControllerFullyVisible
            }
            if (hidingControlsOnBack) {
                if (event.action == KeyEvent.ACTION_UP) {
                    hidingControlsOnBack = false
                    if (!event.isCanceled) {
                        hideController()
                        requestFocus()
                    }
                }
                // Consume the entire press before PlayerView or the enclosing dialog sees it.
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private companion object {
        val PLAYER_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_STOP,
        )
    }
}
