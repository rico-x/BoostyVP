package io.github.ricox.boostyvp.core.model

/**
 * How far one video of a post was watched, with enough of the post to draw its
 * card in "Продолжить просмотр" without a network request.
 *
 * [blogUrl] and [title] are empty for positions carried over from 0.2 storage,
 * which recorded only the position; such entries resume playback but are not
 * listed until the video is played again.
 */
data class WatchProgress(
    val postId: String,
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    /** Epoch milliseconds of the last checkpoint. */
    val updatedAt: Long,
    val blogUrl: String = "",
    val title: String = "",
    val blogTitle: String = "",
    val previewUrl: String? = null,
) {
    /** 0..1; 0 when the duration is unknown. */
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val hasCard: Boolean get() = blogUrl.isNotBlank()
}
