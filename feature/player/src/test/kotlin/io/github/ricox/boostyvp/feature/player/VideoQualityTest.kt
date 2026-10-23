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
}
