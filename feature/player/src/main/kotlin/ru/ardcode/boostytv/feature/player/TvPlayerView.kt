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
        // Request only after attachment/window focus, never during view construction.
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
        // ComponentDialog receives system Back through its dispatcher on newer
        // Android versions, including predictive Back rather than native key events.
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

}
