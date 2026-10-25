package io.github.ricox.boostyvp.feature.player

import androidx.media3.ui.AspectRatioFrameLayout
import io.github.ricox.boostyvp.core.ui.player.VideoScale

enum class VideoDirection { Previous, Next }

/** Overflow-safe 0–90 % target. Unknown durations and invalid digits never seek. */
internal fun digitSeekPosition(durationMs: Long, digit: Int): Long? {
    if (durationMs <= 0 || digit !in 0..9) return null
    return durationMs / 10 * digit + durationMs % 10 * digit / 10
}

/** A second *independent* Back within two seconds confirms exit. */
internal class ExitConfirmation {
    private var armedAt: Long? = null

    fun press(now: Long): Boolean {
        val previous = armedAt
        if (previous != null && now - previous in 0..WINDOW_MS) {
            reset()
            return true
        }
        armedAt = now
        return false
    }

    fun reset() { armedAt = null }

    companion object { const val WINDOW_MS = 2_000L }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal val VideoScale.media3ResizeMode: Int get() = when (this) {
    VideoScale.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    VideoScale.Width -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    VideoScale.Height -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
    VideoScale.Stretch -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    VideoScale.Crop -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}
