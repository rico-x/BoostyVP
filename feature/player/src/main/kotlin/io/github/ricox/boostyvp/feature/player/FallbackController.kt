package io.github.ricox.boostyvp.feature.player

import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import io.github.ricox.boostyvp.core.model.VideoSource

/**
 * Walks an ordered list of sources, moving to the next one when playback dies.
 *
 * ExoPlayer retries transient errors against the same URL, which is right for a
 * flaky connection but useless for the two failures Boosty actually produces: a
 * rendition that was never encoded (404) and an expired or host-specific signed
 * URL (403). Both need a *different* URL, so the ladder is stepped here.
 *
 * The current position is carried across so a mid-video failure resumes where
 * it stopped instead of restarting.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class FallbackController(
    private val player: ExoPlayer,
    private var candidates: List<VideoSource>,
    private val mediaSourceFactory: (VideoSource) -> MediaSource = { buildMediaSource(it) },
) {
    private var index = -1

    fun start(startPositionMs: Long, playWhenReady: Boolean = true) {
        index = -1
        advanceTo(0, startPositionMs, playWhenReady)
    }

    /** Change rendition without losing the position or unpausing the video. */
    fun selectSources(sources: List<VideoSource>, keepCurrent: Boolean = false) {
        require(sources.isNotEmpty())
        val current = candidates.getOrNull(index)
        candidates = sources
        val retainedIndex = sources.indexOfFirst { it.url == current?.url }
        if (keepCurrent && retainedIndex >= 0) {
            index = retainedIndex
        } else {
            start(
                player.currentPosition.coerceAtLeast(0),
                player.playWhenReady && player.playbackState != androidx.media3.common.Player.STATE_ENDED,
            )
        }
    }

    /**
     * @return true if another candidate was tried, false when the ladder is
     *   exhausted and the error should surface to the user.
     */
    fun advance(error: PlaybackException): Boolean {
        val next = index + 1
        if (next >= candidates.size) {
            Log.e(TAG, "All ${candidates.size} sources failed", error)
            return false
        }
        Log.w(
            TAG,
            "Source ${candidates.getOrNull(index)?.rawType} failed " +
                "(${error.errorCodeName}); trying ${candidates[next].rawType}",
        )
        // Resume where it died rather than from the top.
        advanceTo(next, player.currentPosition.coerceAtLeast(0), player.playWhenReady)
        return true
    }

    private fun advanceTo(target: Int, positionMs: Long, playWhenReady: Boolean) {
        if (target >= candidates.size) return
        index = target
        val source = candidates[target]
        player.setMediaSource(mediaSourceFactory(source), positionMs)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    private companion object {
        const val TAG = "FallbackController"
    }
}
