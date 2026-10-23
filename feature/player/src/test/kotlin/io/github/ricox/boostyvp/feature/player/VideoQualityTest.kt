package io.github.ricox.boostyvp.feature.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import io.github.ricox.boostyvp.core.model.VideoSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoQualityTest {
    private val hd = VideoSource(VideoSource.Kind.Progressive, "https://cdn.example/720?sig=a%2Fb", 5, "high")
    private val fullHd = VideoSource(VideoSource.Kind.Progressive, "https://cdn.example/1080", 6, "full_hd")

    @Test
    fun `manual quality retains signed CDN alternates and excludes other resolutions`() {
        val backup = hd.copy(url = "https://backup.example/720?sig=a%2Fb")
        val candidates = listOf(fullHd, hd, backup, hd)
        assertEquals(listOf(hd, backup), sourcesForResolution(candidates, 720))
        assertEquals(listOf(1080, 720, 480), qualityResolutions(candidates, listOf(1080, 480, -1)))
    }

    @Test
    fun `switching quality while paused retains position and pause`() {
        val player = mockk<ExoPlayer>(relaxed = true)
        val media = mockk<MediaSource>()
        every { player.currentPosition } returns 123_000
        every { player.playWhenReady } returns false
        every { player.playbackState } returns Player.STATE_READY
        val fallback = FallbackController(player, listOf(fullHd, hd)) { media }

        fallback.selectSources(listOf(hd))

        verify { player.setMediaSource(media, 123_000) }
        verify { player.playWhenReady = false }
        verify(exactly = 0) { player.playWhenReady = true }
    }

    @Test
    fun `changing quality after the end does not restart playback`() {
        val player = mockk<ExoPlayer>(relaxed = true)
        val media = mockk<MediaSource>()
        every { player.currentPosition } returns 600_000
        every { player.playWhenReady } returns true
        every { player.playbackState } returns Player.STATE_ENDED
        val fallback = FallbackController(player, listOf(fullHd, hd)) { media }

        fallback.selectSources(listOf(hd))

        verify { player.setMediaSource(media, 600_000) }
        verify { player.playWhenReady = false }
        verify(exactly = 0) { player.playWhenReady = true }
    }

    @Test
    fun `a remembered quality starts the next video at that height or the nearest below`() {
        val sd = VideoSource(VideoSource.Kind.Progressive, "https://cdn.example/480", 4, "medium")
        val player = mockk<ExoPlayer>(relaxed = true)
        val started = mutableListOf<VideoSource>()
        val fallback = FallbackController(player, listOf(fullHd, hd, sd)) { started += it; mockk() }
        val chosen = mutableListOf<Int?>()
        val quality = VideoQualityController(player, fallback, listOf(fullHd, hd, sd), onManualChoice = { chosen += it })

        quality.preselect(1_000)
        fallback.start(startPositionMs = 42_000)

        assertEquals("1080p was never chosen, so 720p it is", listOf(hd), started)
        assertEquals("Preselection is not a manual choice", emptyList<Int?>(), chosen)
    }

    @Test
    fun `no remembered quality keeps the full ladder`() {
        val player = mockk<ExoPlayer>(relaxed = true)
        val started = mutableListOf<VideoSource>()
        val fallback = FallbackController(player, listOf(fullHd, hd)) { started += it; mockk() }
        VideoQualityController(player, fallback, listOf(fullHd, hd)).preselect(null)
        fallback.start(startPositionMs = 0)
        assertEquals(listOf(fullHd), started)
    }
}
