package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.VideoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCandidatesTest {
    private fun video(sources: List<VideoSource>, failoverHost: String? = null) = ContentBlock.Video(
        id = "video", vid = null, title = null, duration = 120,
        width = null, height = null, previewUrl = null, failoverHost = failoverHost,
        timeCode = null, sources = sources, isReady = true,
    )

    @Test
    fun `fallback host preserves the exact encoded path and signature`() {
        val original = "https://primary.example/a%2Fb/video?sig=a%2Bb%2Fc%3D&next=%252F#part%201"
        assertEquals(
            "https://backup.example/a%2Fb/video?sig=a%2Bb%2Fc%3D&next=%252F#part%201",
            rewriteHost(original, "https://backup.example/"),
        )
    }

    @Test
    fun `quality cap applies to every fallback`() {
        val high = VideoSource(VideoSource.Kind.Progressive, "https://cdn.example/high", 6, "full_hd")
        val low = VideoSource(VideoSource.Kind.Progressive, "https://cdn.example/low", 3, "low")
        val candidates = playbackCandidates(video(listOf(high, low), "backup.example"), 3)
        assertEquals(2, candidates.size)
        assertTrue(candidates.all { it.qualityRank <= 3 })
        assertTrue(playbackCandidates(video(listOf(high)), 3).isEmpty())
    }

    @Test
    fun `identical URLs are tried only once`() {
        val source = VideoSource(VideoSource.Kind.Hls, "https://cdn.example/video", 0, "hls")
        assertEquals(listOf(source), playbackCandidates(video(listOf(source, source), "cdn.example")))
    }
}
