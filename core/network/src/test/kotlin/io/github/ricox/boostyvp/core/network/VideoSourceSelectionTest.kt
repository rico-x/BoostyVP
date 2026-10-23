package io.github.ricox.boostyvp.core.network

import io.github.ricox.boostyvp.core.model.VideoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoSourceSelectionTest {

    private fun source(type: String, kind: VideoSource.Kind, rank: Int = 0) =
        VideoSource(kind = kind, url = "https://cdn.example/$type", qualityRank = rank, rawType = type)

    @Test
    fun `prefers dash over hls and progressive`() {
        val sources = listOf(
            source("full_hd", VideoSource.Kind.Progressive, 6),
            source("hls", VideoSource.Kind.Hls),
            source("dash", VideoSource.Kind.Dash),
        )
        assertEquals(VideoSource.Kind.Dash, VideoSource.pick(sources)?.kind)
    }

    @Test
    fun `prefers hls when no dash is offered`() {
        val sources = listOf(
            source("high", VideoSource.Kind.Progressive, 5),
            source("hls", VideoSource.Kind.Hls),
        )
        assertEquals(VideoSource.Kind.Hls, VideoSource.pick(sources)?.kind)
    }

    @Test
    fun `picks the highest progressive rendition when nothing adaptive exists`() {
        val sources = listOf(
            source("low", VideoSource.Kind.Progressive, 3),
            source("ultra_hd", VideoSource.Kind.Progressive, 8),
            source("medium", VideoSource.Kind.Progressive, 4),
        )
        assertEquals("ultra_hd", VideoSource.pick(sources)?.rawType)
    }

    @Test
    fun `quality cap applies to progressive only`() {
        val sources = listOf(
            source("ultra_hd", VideoSource.Kind.Progressive, 8),
            source("high", VideoSource.Kind.Progressive, 5),
        )
        assertEquals("high", VideoSource.pick(sources, maxProgressiveRank = 5)?.rawType)
    }

    @Test
    fun `quality cap does not discard adaptive manifests`() {
        // An adaptive manifest carries every rendition in one URL, so capping it
        // here would wrongly drop the best option; the track selector handles it.
        val sources = listOf(source("dash", VideoSource.Kind.Dash))
        assertEquals(VideoSource.Kind.Dash, VideoSource.pick(sources, maxProgressiveRank = 1)?.kind)
    }

    @Test
    fun `returns null for an empty list`() {
        assertNull(VideoSource.pick(emptyList()))
    }
}
