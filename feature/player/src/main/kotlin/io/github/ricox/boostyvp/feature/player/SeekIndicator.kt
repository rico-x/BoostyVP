package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import io.github.ricox.boostyvp.core.ui.R as UiR
import io.github.ricox.boostyvp.core.ui.appearance.InterfacePreferences

/**
 * What a remote seek will do, shown over the video while the controls stay
 * hidden: direction and amount, the target time and where it lies in the video.
 * Never focusable, so it cannot take the remote away from the player.
 */
internal class SeekIndicator(context: Context) : LinearLayout(context) {
    private val delta = TextView(context)
    private val time = TextView(context)
    private val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val hide = Runnable { visibility = View.GONE }

    init {
        tag = TAG
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        visibility = View.GONE
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        val appearance = InterfacePreferences(context).load()
        background = GradientDrawable().apply {
            setColor(appearance.theme.surface and 0x00FFFFFF or (0xE6 shl 24))
            cornerRadius = context.playerDp(20).toFloat()
            setStroke(context.playerDp(1), context.getColor(UiR.color.boostyvp_border))
        }
        val padding = context.playerDp(20)
        setPadding(padding, context.playerDp(14), padding, context.playerDp(16))
        delta.apply {
            textSize = 30f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(appearance.accent.textArgb)
        }
        time.apply {
            textSize = 18f
            setTextColor(context.getColor(UiR.color.boostyvp_text))
        }
        bar.apply {
            max = PROGRESS_MAX
            progressTintList = android.content.res.ColorStateList.valueOf(appearance.accent.argb)
        }
        addView(delta, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(time, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(bar, LayoutParams(context.playerDp(260), context.playerDp(6)).apply { topMargin = context.playerDp(10) })
    }

    fun show(preview: RemoteSeek.Preview) {
        removeCallbacks(hide)
        val arrows = if (preview.direction < 0) "◀◀" else "▶▶"
        delta.text = "$arrows  ${formatSeekDelta(preview.deltaMs)}"
        val known = preview.durationMs > 0
        time.text = if (known) "${formatSeekTime(preview.targetMs)} / ${formatSeekTime(preview.durationMs)}"
            else formatSeekTime(preview.targetMs)
        bar.visibility = if (known) View.VISIBLE else View.GONE
        if (known) bar.progress = (preview.targetMs * PROGRESS_MAX / preview.durationMs).toInt()
        contentDescription = tr(UiText.seek_indicator_001, delta.text.drop(arrows.length).trim(), time.text)
        visibility = View.VISIBLE
    }

    fun hideSoon() {
        removeCallbacks(hide)
        postDelayed(hide, HIDE_AFTER_MS)
    }

    fun hideNow() {
        removeCallbacks(hide)
        visibility = View.GONE
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hide)
        super.onDetachedFromWindow()
    }

    companion object {
        const val TAG = "seek_indicator"
        const val HIDE_AFTER_MS = 900L
        private const val PROGRESS_MAX = 1_000

        fun layoutParams(context: Context) = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { bottomMargin = context.playerDp(96) }
    }
}
