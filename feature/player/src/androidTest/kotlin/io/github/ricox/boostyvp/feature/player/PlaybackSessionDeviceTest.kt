package io.github.ricox.boostyvp.feature.player

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlaybackSessionDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun systemControllerReadsMetadataAndControlsRealPlayback() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).wakeUp()
        lateinit var player: ExoPlayer
        lateinit var session: MediaSession
        var closed by mutableStateOf(false)
        compose.setContent {
            if (!closed) DisposableEffect(Unit) {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                player = ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.Builder().setUri("asset:///remote-control-test.mp4").setMediaId("synthetic-post")
                        .setMediaMetadata(MediaMetadata.Builder().setTitle("Проверка MediaSession").setArtist("Тестовый автор").build()).build())
                    prepare(); playWhenReady = true
                }
                session = createPlaybackSession(context, player, "synthetic-post")
                onDispose { session.release(); player.release() }
            }
        }
        compose.waitUntil(10_000) { compose.runOnIdle { player.isPlaying } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val future = compose.runOnIdle { MediaController.Builder(context, session.token).buildAsync() }
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            compose.runOnIdle { assertEquals("Проверка MediaSession", controller.mediaMetadata.title.toString()); controller.pause() }
            compose.waitUntil(3_000) { compose.runOnIdle { !player.playWhenReady } }
            compose.runOnIdle { controller.seekTo(2_000); controller.play() }
            compose.waitUntil(3_000) { compose.runOnIdle { player.isPlaying && player.currentPosition >= 2_000 } }
            compose.runOnIdle { closed = true }
            compose.waitUntil(3_000) { compose.runOnIdle { !controller.isConnected } }
        } finally { compose.runOnIdle { controller.release() } }
    }
}
