package io.github.ricox.boostyvp.feature.player

/** Only playback identity/state, never signed URLs or publication content. */
internal data class PlaybackCheckpoint(val blog: String, val post: String, val video: String,
    val positionMs: Long, val durationMs: Long, val playWhenReady: Boolean, val generation: Long = 0) {
    fun matches(playback: PlayerUiState.Ready) = blog == playback.blogUrl && post == playback.postId && video == playback.videoId
}

/** ON_PAUSE and ON_STOP may both fire. An automatic pause must not erase play intent. */
internal class PlaybackVisibility(initialIntent: Boolean, initiallyForeground: Boolean = true) {
    private var background = !initiallyForeground
    private var resumeIntent = initialIntent
    fun desired(actualPlayWhenReady: Boolean) = if (background) resumeIntent else actualPlayWhenReady
    fun leave(actualPlayWhenReady: Boolean) {
        if (!background) resumeIntent = actualPlayWhenReady
        background = true
    }
    fun enter(): Boolean { background = false; return resumeIntent }
}
