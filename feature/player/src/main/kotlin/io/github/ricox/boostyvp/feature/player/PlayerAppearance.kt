package io.github.ricox.boostyvp.feature.player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import io.github.ricox.boostyvp.core.ui.R as UiR

internal fun Context.playerDp(value: Int) = (value * resources.displayMetrics.density).toInt()

internal fun playerControlBackground(context: Context): StateListDrawable {
    val radius = context.playerDp(8).toFloat()
    val focused = context.getColor(UiR.color.boostyvp_surface_raised)
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
            setColor(focused); cornerRadius = radius
            setStroke(context.playerDp(2), context.getColor(UiR.color.boostyvp_text))
        })
        addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply {
            setColor(focused); cornerRadius = radius
        })
        addState(intArrayOf(), GradientDrawable().apply {
            setColor(0x10FFFFFF); cornerRadius = radius
        })
    }
}

internal fun playerControlTextColors(context: Context) = ColorStateList(
    arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_pressed), intArrayOf()),
    intArrayOf(context.getColor(UiR.color.boostyvp_on_accent), context.getColor(UiR.color.boostyvp_on_accent),
        context.getColor(UiR.color.boostyvp_text)),
)

/** Style Media3's native controls while preserving its transport and focus behavior. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun applyPlayerAppearance(playerView: PlayerView, title: String) {
    val context = playerView.context
    val accent = context.getColor(UiR.color.boostyvp_accent)
    playerView.findViewById<View>(androidx.media3.ui.R.id.exo_controls_background)?.background =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            0xDA0E0F12.toInt(), 0x220E0F12, 0xEE0E0F12.toInt()))
    playerView.findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.apply {
        setPlayedColor(accent)
        setScrubberColor(accent)
        setBufferedColor(0x99FFFFFF.toInt())
        setUnplayedColor(0x44FFFFFF)
        (layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.leftMargin = context.playerDp(48)
            params.rightMargin = context.playerDp(48)
            params.bottomMargin = context.playerDp(88)
            layoutParams = params
        }
    }
    playerView.findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar)?.apply {
        background = GradientDrawable().apply { setColor(0xEE191C22.toInt()); cornerRadius = context.playerDp(16).toFloat() }
        (layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.leftMargin = context.playerDp(48)
            params.rightMargin = context.playerDp(48)
            params.bottomMargin = context.playerDp(27)
            layoutParams = params
        }
    }
    fun styleChildren(group: ViewGroup) {
        for (index in 0 until group.childCount) {
            when (val child = group.getChildAt(index)) {
                is ImageButton -> {
                    child.background = playerControlBackground(context)
                    child.imageTintList = playerControlTextColors(context)
                }
                is Button -> {
                    child.setTextColor(playerControlTextColors(context))
                    if (child.id == androidx.media3.ui.R.id.exo_rew_with_amount ||
                        child.id == androidx.media3.ui.R.id.exo_ffwd_with_amount
                    ) {
                        // These labels sit inside Media3's circular arrows. The generic button
                        // font and the stock bottom padding move them out of the arrows' centre.
                        child.setTextSize(
                            TypedValue.COMPLEX_UNIT_PX,
                            context.resources.getDimension(androidx.media3.ui.R.dimen.exo_icon_text_size),
                        )
                        child.gravity = Gravity.CENTER
                        child.includeFontPadding = false
                        // The circular part of the arrow sits 1.5dp below the icon's centre.
                        child.setPadding(0, context.playerDp(3), 0, 0)
                        child.background = playerControlBackground(context)
                        child.backgroundTintList = null
                        child.foregroundTintList = playerControlTextColors(context)
                    } else {
                        child.textSize = 14f
                    }
                }
                is TextView -> { child.setTextColor(context.getColor(UiR.color.boostyvp_text)); child.textSize = 15f }
                is ViewGroup -> styleChildren(child)
            }
        }
    }
    playerView.findViewById<PlayerControlView>(androidx.media3.ui.R.id.exo_controller)?.let { controls ->
        styleChildren(controls)
        controls.addView(TextView(context).apply {
            text = title
            textSize = 26f
            setTextColor(context.getColor(UiR.color.boostyvp_text))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            isFocusable = false
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START).apply {
            setMargins(context.playerDp(48), context.playerDp(32), context.playerDp(48), 0)
        })
    }
}
