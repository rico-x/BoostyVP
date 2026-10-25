package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.ricox.boostyvp.core.ui.R as UiR
import io.github.ricox.boostyvp.core.ui.appearance.InterfacePreferences

private fun overlayBackground(context: Context) = GradientDrawable().apply {
    val surface = InterfacePreferences(context).load().theme.surface
    setColor(surface and 0x00FFFFFF or (0xE6 shl 24))
    cornerRadius = context.playerDp(20).toFloat()
    setStroke(context.playerDp(1), context.getColor(UiR.color.boostyvp_border))
}

/**
 * "Далее" at the end of a video: the next title and a countdown. The keys are
 * handled by [TvPlayerView]: OK plays it now, any other key cancels.
 */
internal class NextUpOverlay(context: Context) : LinearLayout(context) {
    private val heading = TextView(context)
    private val title = TextView(context)
    private val hint = TextView(context)
    private var remaining = 0
    private var onPlay: (() -> Unit)? = null
    private val tick = object : Runnable {
        override fun run() {
            remaining--
            if (remaining <= 0) play() else {
                render()
                postDelayed(this, 1_000)
            }
        }
    }

    init {
        tag = TAG
        orientation = VERTICAL
        visibility = View.GONE
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        background = overlayBackground(context)
        val padding = context.playerDp(24)
        setPadding(padding, context.playerDp(18), padding, context.playerDp(20))
        val accent = InterfacePreferences(context).load().accent.textArgb
        heading.apply {
            text = tr(UiText.player_overlays_001)
            textSize = 16f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(accent)
        }
        title.apply {
            textSize = 24f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(context.getColor(UiR.color.boostyvp_text))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        hint.apply {
            textSize = 16f
            setTextColor(context.getColor(UiR.color.boostyvp_text_secondary))
        }
        addView(heading)
        addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.playerDp(6)
        })
        addView(hint, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.playerDp(10)
        })
    }

    val isOffered get() = visibility == View.VISIBLE

    fun show(nextTitle: String, seconds: Int, onPlay: () -> Unit) {
        removeCallbacks(tick)
        this.onPlay = onPlay
        remaining = seconds.coerceAtLeast(1)
        title.text = nextTitle.ifBlank { tr(UiText.post_actions_dialog_002) }
        render()
        visibility = View.VISIBLE
        postDelayed(tick, 1_000)
    }

    fun play() {
        val action = onPlay ?: return
        hide()
        action()
    }

    fun hide() {
        removeCallbacks(tick)
        onPlay = null
        visibility = View.GONE
    }

    private fun render() {
        hint.text = tr(UiText.player_overlays_002, remaining)
        contentDescription = tr(UiText.player_overlays_003, title.text, hint.text)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    companion object {
        const val TAG = "next_up"

        fun layoutParams(context: Context) = FrameLayout.LayoutParams(
            context.playerDp(560), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END,
        ).apply { setMargins(0, 0, context.playerDp(48), context.playerDp(48)) }
    }
}

/** A one-line message over the video, e.g. from the sleep timer; the next key press dismisses it. */
internal class PlayerNotice(context: Context) : TextView(context) {
    init {
        tag = TAG
        visibility = View.GONE
        isFocusable = false
        textSize = 20f
        setTextColor(context.getColor(UiR.color.boostyvp_text))
        background = overlayBackground(context)
        val padding = context.playerDp(20)
        setPadding(padding, context.playerDp(12), padding, context.playerDp(12))
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    fun show(message: String) {
        text = message
        visibility = View.VISIBLE
    }

    fun hide() {
        visibility = View.GONE
    }

    companion object {
        const val TAG = "player_notice"

        fun layoutParams(context: Context) = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ).apply { topMargin = context.playerDp(48) }
    }
}
