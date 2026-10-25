package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.ExoPlayer
import io.github.ricox.boostyvp.core.ui.player.PlayerOptions
import io.github.ricox.boostyvp.core.ui.player.VideoScale
import io.github.ricox.boostyvp.core.ui.player.nextItem

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class TvPlayerView(
    context: Context,
    private val interaction: PlaybackInteraction = PlaybackInteraction(SystemClock.uptimeMillis()),
) : PlayerView(context) {
    private var hidingControlsOnBack = false
    private var statistics: StreamStatistics? = null
    private var options = PlayerOptions()
    private var onNavigateVideo: ((VideoDirection) -> Unit)? = null
    private var onVideoScaleChanged: ((VideoScale) -> Unit)? = null
    var onExitRequest: (() -> Unit)? = null
    private val exitConfirmation = ExitConfirmation()
    private val expireExitHint = Runnable { exitConfirmation.reset(); notice.hide() }
    /** Only a complete, uncancelled press may execute a mapped action. */
    private val mappedKeys = mutableSetOf<Int>()
    private val restorePlaybackFocus = Runnable {
        // Request only after attachment/window focus, never during view construction.
        if (isAttachedToWindow && hasWindowFocus() && !hasFocus()) requestFocus()
    }
    private val hideControlsOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            interaction.recordKey(SystemClock.uptimeMillis())
            handleBack()
        }
    }

    private val remoteSeek = RemoteSeek()
    private val seekIndicator = SeekIndicator(context)
    /** Arrow keys whose current press drives [remoteSeek]; other presses stay native. */
    private val seekKeys = mutableSetOf<Int>()
    private val nextUp = NextUpOverlay(context)
    private val notice = PlayerNotice(context)
    private val scaleButton = Button(context, null, android.R.attr.borderlessButtonStyle).apply {
        tag = "video_scale_button"
        text = tr(UiText.tv_player_view_001)
        contentDescription = tr(UiText.player_settings_dialog_021, options.videoScale.label)
        isAllCaps = false
        textSize = 14f
        background = playerControlBackground(context)
        setTextColor(playerControlTextColors(context))
        setOnClickListener {
            val scale = VideoScale.entries.nextItem(options.videoScale)
            options = options.copy(videoScale = scale)
            applyVideoScale()
            onVideoScaleChanged?.invoke(scale)
            showNotice(tr(UiText.tv_player_view_002, scale.label))
        }
    }
    /** A key whose press dismissed [nextUp]; its release must not reach Media3 either. */
    private var nextUpKey: Int? = null
    private var onNextUpCancelled: (() -> Unit)? = null

    /** Uptime of the last remote key press; the sleep timer counts from it. */
    val lastInteractionAt: Long get() = interaction.lastInteractionAt

    // Without this the TV's screensaver starts over a long video nobody touches.
    private val screenOnListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = updateKeepScreenOn(player)
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        overlayFrameLayout?.addView(seekIndicator, SeekIndicator.layoutParams(context))
        overlayFrameLayout?.addView(nextUp, NextUpOverlay.layoutParams(context))
        overlayFrameLayout?.addView(notice, PlayerNotice.layoutParams(context))
        findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_basic_controls)?.addView(scaleButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Media3 slides the bottom controls by its stock bar height in two stages.
        // Our inset bar and time bar need to disappear together with the whole overlay.
        setControllerAnimationEnabled(false)
        setControllerVisibilityListener(ControllerVisibilityListener { visibility ->
            updateBackCallback()
            if (visibility == View.VISIBLE) clearExitConfirmation()
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
        updateBackCallback()
        statistics?.start()
        post(restorePlaybackFocus)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) post(restorePlaybackFocus)
        else {
            mappedKeys.clear()
            seekKeys.clear()
            remoteSeek.cancel()
            seekIndicator.hideNow()
            clearExitConfirmation()
        }
    }

    override fun onDetachedFromWindow() {
        hideControlsOnBack.remove()
        removeCallbacks(restorePlaybackFocus)
        hidingControlsOnBack = false
        mappedKeys.clear()
        clearExitConfirmation()
        seekKeys.clear()
        remoteSeek.cancel()
        statistics?.stop()
        nextUp.hide()
        super.onDetachedFromWindow()
    }

    override fun setPlayer(player: Player?) {
        if (this.player === player) return
        statistics?.release()
        statistics = null
        this.player?.removeListener(screenOnListener)
        super.setPlayer(player)
        if (player is ExoPlayer) {
            statistics = StreamStatistics(this, player)
            if (isAttachedToWindow) statistics?.start()
        }
        player?.addListener(screenOnListener)
        updateKeepScreenOn(player)
    }

    /** On while the video plays or is about to; paused or finished lets the TV sleep as usual. */
    private fun updateKeepScreenOn(player: Player?) {
        keepScreenOn = player != null && player.playWhenReady &&
            (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
    }

    /** Offer [title] next; it plays after [seconds] or on OK. Any other key cancels. */
    fun showNextUp(title: String, seconds: Int, onPlay: () -> Unit, onCancel: () -> Unit) {
        onNextUpCancelled = onCancel
        hideController()
        nextUp.show(title, seconds) {
            onNextUpCancelled = null
            onPlay()
        }
        updateBackCallback()
    }

    val isNextUpShown: Boolean get() = nextUp.isOffered

    fun showNotice(message: String) { clearExitConfirmation(); notice.show(message) }

    fun configureControls(options: PlayerOptions, onNavigate: (VideoDirection) -> Unit,
        onScaleChanged: (VideoScale) -> Unit) {
        this.options = options.normalized()
        onNavigateVideo = onNavigate
        onVideoScaleChanged = onScaleChanged
        clearExitConfirmation()
        applyVideoScale()
        updateBackCallback()
    }

    private fun applyVideoScale() {
        resizeMode = options.videoScale.media3ResizeMode
        scaleButton.contentDescription = tr(UiText.player_settings_dialog_021, options.videoScale.label)
    }

    private fun updateBackCallback() {
        hideControlsOnBack.isEnabled = options.doubleBackExit || isControllerFullyVisible ||
            statistics?.shown == true || nextUp.isOffered
    }

    private fun clearExitConfirmation() {
        exitConfirmation.reset()
        removeCallbacks(expireExitHint)
    }

    private fun handleBack() {
        when {
            nextUp.isOffered -> {
                nextUp.hide()
                onNextUpCancelled?.invoke()
                onNextUpCancelled = null
                clearExitConfirmation()
            }
            statistics?.shown == true -> { statistics?.hide(); clearExitConfirmation() }
            isControllerFullyVisible -> { hideController(); clearExitConfirmation() }
            options.doubleBackExit -> {
                if (exitConfirmation.press(SystemClock.uptimeMillis())) {
                    removeCallbacks(expireExitHint)
                    onExitRequest?.invoke()
                    return
                }
                notice.show(tr(UiText.tv_player_view_003))
                removeCallbacks(expireExitHint)
                postDelayed(expireExitHint, ExitConfirmation.WINDOW_MS)
            }
            else -> { onExitRequest?.invoke(); return }
        }
        updateBackCallback()
        requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            interaction.recordKey(event.eventTime)
            notice.hide()
            if (event.keyCode != KeyEvent.KEYCODE_BACK) clearExitConfirmation()
        }
        if (dispatchNextUp(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_INFO) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                statistics?.toggle()
                updateBackCallback()
            }
            return true
        }
        if (dispatchMappedKey(event)) return true
        if (dispatchRemoteSeek(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) showController()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                hidingControlsOnBack = options.doubleBackExit || isControllerFullyVisible || statistics?.shown == true
            }
            if (hidingControlsOnBack) {
                if (event.action == KeyEvent.ACTION_UP) {
                    hidingControlsOnBack = false
                    if (!event.isCanceled) {
                        handleBack()
                    }
                }
                // Consume the entire press before PlayerView or the enclosing dialog sees it.
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun dispatchMappedKey(event: KeyEvent): Boolean {
        val direction = when {
            options.channelVideoNavigation && event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP -> VideoDirection.Next
            options.channelVideoNavigation && event.keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN -> VideoDirection.Previous
            options.transportVideoNavigation && event.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> VideoDirection.Next
            options.transportVideoNavigation && event.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND -> VideoDirection.Previous
            else -> null
        }
        val digit = when (event.keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> event.keyCode - KeyEvent.KEYCODE_0
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> event.keyCode - KeyEvent.KEYCODE_NUMPAD_0
            else -> null
        }.takeIf { options.digitSeek }
        if (direction == null && digit == null) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) mappedKeys += event.keyCode
            KeyEvent.ACTION_UP -> if (mappedKeys.remove(event.keyCode) && !event.isCanceled) {
                remoteSeek.cancel()
                seekKeys.clear()
                seekIndicator.hideNow()
                if (direction != null) onNavigateVideo?.invoke(direction)
                else {
                    val player = player
                    val target = player?.duration?.let { digitSeekPosition(it, requireNotNull(digit)) }
                    if (player != null && target != null && !player.isCurrentMediaItemLive &&
                        player.isCurrentMediaItemSeekable && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                        player.seekTo(target)
                        showNotice(tr(UiText.tv_player_view_004, requireNotNull(digit) * 10))
                    } else showNotice(tr(UiText.tv_player_view_005))
                }
            }
        }
        // Includes orphan releases and auto-repeat: never leak half a press to Media3.
        return true
    }

    private fun dispatchNextUp(event: KeyEvent): Boolean {
        if (nextUpKey == event.keyCode) {
            if (event.action == KeyEvent.ACTION_UP) nextUpKey = null
            return true
        }
        if (!nextUp.isOffered || event.action != KeyEvent.ACTION_DOWN) return false
        nextUpKey = event.keyCode
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> nextUp.play()
            else -> {
                nextUp.hide()
                onNextUpCancelled?.invoke()
                onNextUpCancelled = null
            }
        }
        updateBackCallback()
        return true
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
