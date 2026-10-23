package io.github.ricox.boostyvp.feature.player

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import io.github.ricox.boostyvp.core.ui.appearance.InterfaceOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Estimate only, respecting playback speed. Unknown/live durations have no ETA. */
internal fun estimatedEndTimeMs(nowMs: Long, positionMs: Long, durationMs: Long, speed: Float): Long? {
    if (durationMs <= 0 || !speed.isFinite() || speed <= 0f) return null
    val remaining = max(0L, durationMs - positionMs.coerceAtLeast(0)) / speed.toDouble()
    return (nowMs.toDouble() + remaining).takeIf { it < Long.MAX_VALUE.toDouble() }?.toLong()
}

/** Child of the native controller: hides with it and never receives D-pad focus. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun attachPlayerClock(playerView: PlayerView, controls: PlayerControlView, options: InterfaceOptions) {
    val context = playerView.context
    val clock = TextView(context).apply {
        tag = "player_clock"
        textSize = options.playerText.timeSp.toFloat()
        setTextColor(context.getColor(io.github.ricox.boostyvp.core.ui.R.color.boostyvp_text))
        gravity = Gravity.END
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    controls.addView(clock, FrameLayout.LayoutParams(context.playerDp(216), ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.TOP or Gravity.END).apply { setMargins(0, context.playerDp(32), context.playerDp(48), 0) })
    val format = SimpleDateFormat("HH:mm", Locale.forLanguageTag("ru"))
    val update = object : Runnable {
        override fun run() {
            if (!clock.isAttachedToWindow) return
            if (clock.isShown) {
                val now = System.currentTimeMillis()
                val player = playerView.player
                clock.text = buildList {
                    if (options.playerClock) add(format.format(Date(now)))
                    if (options.playerEndTime && player != null && !player.isCurrentMediaItemLive) {
                        if (!player.playWhenReady && player.duration > 0) add("Окончание: на паузе")
                        else estimatedEndTimeMs(now, player.currentPosition, player.duration, player.playbackParameters.speed)
                            ?.let { add("Окончание ≈ ${format.format(Date(it))}") }
                    }
                }.joinToString("\n")
            }
            clock.postDelayed(this, 1_000)
        }
    }
    clock.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) { view.post(update) }
        override fun onViewDetachedFromWindow(view: View) { view.removeCallbacks(update) }
    })
    if (clock.isAttachedToWindow) clock.post(update)
}
