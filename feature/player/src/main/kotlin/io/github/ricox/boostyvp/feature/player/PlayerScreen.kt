package io.github.ricox.boostyvp.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import io.github.ricox.boostyvp.core.ui.component.EmptyState
import io.github.ricox.boostyvp.core.ui.component.ErrorState
import io.github.ricox.boostyvp.core.ui.component.LoadingState
import io.github.ricox.boostyvp.core.ui.player.PlayerPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PlayerScreen(
    blogUrl: String,
    postId: String,
    onClose: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    LaunchedEffect(blogUrl, postId) { viewModel.load(blogUrl, postId) }

    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is PlayerUiState.Loading -> LoadingState()
        is PlayerUiState.Publication -> PostReaderScreen(current.post, onClose)

        is PlayerUiState.Locked -> EmptyState(
            title = current.title,
            detail = "Это видео доступно на другом уровне подписки. Изменить подписку можно на boosty.to.",
        )

        is PlayerUiState.Unplayable -> EmptyState(
            title = current.title,
            detail = current.reason,
        )

        is PlayerUiState.Failed -> ErrorState(
            detail = current.message,
            onRetry = { viewModel.load(blogUrl, postId) },
        )

        is PlayerUiState.Ready -> VideoSurface(
            onClose = onClose,
            state = current,
            onProgress = { position, duration -> viewModel.onProgress(current, position, duration) },
            onEnded = { viewModel.onPlaybackEnded(current) },
        )
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun VideoSurface(
    onClose: () -> Unit,
    state: PlayerUiState.Ready,
    onProgress: (Long, Long) -> Unit,
    onEnded: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { PlayerPreferences(context) }
    val options = remember(state.postId, state.videoId) { preferences.load() }

    val player = remember(state.postId, state.videoId, state.candidates, state.startPositionMs) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(options.seekBackSeconds * 1_000L)
            .setSeekForwardIncrementMs(options.seekForwardSeconds * 1_000L)
            .build()
            .apply { setPlaybackSpeed(preferences.playbackSpeed()) }
    }

    // Index into state.candidates. On a fatal error the controller steps to the
    // next candidate -- a different rendition, or the same one on the failover
    // host -- because ExoPlayer's own retry only ever re-tries the same URL.
    val fallback = remember(player) { FallbackController(player, state.candidates) }
    val quality = remember(player) { VideoQualityController(player, fallback, state.candidates) }
    var playbackError by remember(player) { mutableStateOf<String?>(null) }
    var playbackEnded by remember(player) { mutableStateOf(false) }
    // Keep callbacks bound to this player's video, including its final checkpoint
    // when a new Ready state replaces it during the same composition.
    val progressForPlayer = remember(player) { onProgress }
    val endedForPlayer = remember(player) { onEnded }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (!fallback.advance(error)) {
                    playbackError = error.localizedMessage ?: "Не удалось воспроизвести видео."
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playbackEnded = playbackState == Player.STATE_ENDED
                if (playbackEnded) endedForPlayer()
            }

            override fun onTracksChanged(tracks: Tracks) = quality.onTracksChanged()

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                preferences.saveSpeed(playbackParameters.speed)
            }
        }
        player.addListener(listener)
        fallback.start(startPositionMs = state.startPositionMs)

        onDispose {
            quality.dismiss()
            progressForPlayer(player.currentPosition, player.duration.coerceAtLeast(0))
            player.removeListener(listener)
            player.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        var resumeOnStart = player.playWhenReady
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeOnStart = player.playWhenReady
                    player.pause()
                    progressForPlayer(player.currentPosition, player.duration.coerceAtLeast(0))
                }
                Lifecycle.Event.ON_START -> if (resumeOnStart) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Periodic checkpoint so a hard power-off still leaves a usable resume
    // point. TV users pull the plug; they do not press Back.
    LaunchedEffect(player) {
        while (isActive) {
            delay(10_000)
            if (player.isPlaying) {
                progressForPlayer(player.currentPosition, player.duration.coerceAtLeast(0))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (playbackError == null) TvPlayerHost(
            onDismiss = onClose,
            factory = { ctx ->
                val view = TvPlayerView(ctx)
                view.apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = options.controllerTimeoutMs
                    setShowRewindButton(true)
                    setShowFastForwardButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    quality.attach(view)
                    applyPlayerAppearance(view, state.title)
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            onRelease = { it.player = null },
            update = {
                it.player = player
                it.controllerShowTimeoutMs = if (playbackEnded) 0 else options.controllerTimeoutMs
                if (playbackEnded) it.showController()
            },
            modifier = Modifier.fillMaxSize(),
        ) else playbackError?.let { detail ->
            ErrorState(
                title = "Не удалось воспроизвести",
                detail = detail,
                onRetry = {
                    playbackError = null
                    fallback.start(player.currentPosition.coerceAtLeast(0))
                },
            )
        }
    }
}
