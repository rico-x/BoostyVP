package io.github.ricox.boostyvp.feature.player

import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.media3.common.C
import androidx.media3.common.Player
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

    private val remoteSeek = RemoteSeek()
    private val seekIndicator = SeekIndicator(context)
    /** Arrow keys whose current press drives [remoteSeek]; other presses stay native. */
    private val seekKeys = mutableSetOf<Int>()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        overlayFrameLayout?.addView(seekIndicator, SeekIndicator.layoutParams(context))
        // Media3 slides the bottom controls by its stock bar height in two stages.
        // Our inset bar and time bar need to disappear together with the whole overlay.
        setControllerAnimationEnabled(false)
        setControllerVisibilityListener(ControllerVisibilityListener { visibility ->
            hideControlsOnBack.isEnabled = visibility == View.VISIBLE
            if (visibility == View.VISIBLE) seekIndicator.hideNow()
            // A control that had focus disappears with the panel; arrows must still reach us.
            else post(restorePlaybackFocus)
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
        seekKeys.clear()
        remoteSeek.cancel()
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (dispatchRemoteSeek(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) showController()
            return true
        }
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

    /**
     * ←/→ with hidden controls seek straight away, as on SmartTube, instead of
     * Media3's default of only revealing the controls. Visible controls keep
     * native focus movement and time bar scrubbing; unseekable media (live,
     * still loading) keeps the default.
     */
    private fun dispatchRemoteSeek(event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            else -> return false
        }
        val player = player ?: return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val first = event.repeatCount == 0
                if (first) {
                    val seekable = player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
                        player.isCurrentMediaItemSeekable
                    if (isControllerFullyVisible || !seekable) return false
                    seekKeys += event.keyCode
                } else if (event.keyCode !in seekKeys) {
                    return false
                }
                val step = if (direction < 0) player.seekBackIncrement else player.seekForwardIncrement
                val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                seekIndicator.show(remoteSeek.press(direction, step, event.downTime, event.eventTime,
                    player.currentPosition, duration, first))
                return true
            }
            KeyEvent.ACTION_UP -> {
                if (!seekKeys.remove(event.keyCode)) return false
                if (event.isCanceled) {
                    remoteSeek.cancel()
                    seekIndicator.hideNow()
                } else if (seekKeys.isEmpty()) {
                    remoteSeek.commit(event.eventTime)?.let(player::seekTo)
                    seekIndicator.hideSoon()
                }
                // The press began as a seek; Media3 never sees any part of it.
                return true
            }
            else -> return false
        }
    }
}
