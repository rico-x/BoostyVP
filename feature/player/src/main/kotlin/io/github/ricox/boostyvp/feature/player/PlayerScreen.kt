package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import android.os.SystemClock
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
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.component.EmptyState
import io.github.ricox.boostyvp.core.ui.component.ErrorState
import io.github.ricox.boostyvp.core.ui.component.LoadingState
import io.github.ricox.boostyvp.core.ui.component.LocalCatalogControls
import io.github.ricox.boostyvp.core.domain.repository.queueContinuation
import io.github.ricox.boostyvp.core.ui.player.EndAction
import io.github.ricox.boostyvp.core.ui.player.PlayerPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PlayerScreen(
    blogUrl: String,
    postId: String,
    onClose: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
    startFromBeginning: Boolean = false,
) {
    // Keep the inactivity clock outside Ready/Loading: autoplay and a link refresh
    // replace the player view but are not remote-control interaction.
    val interaction = remember { PlaybackInteraction(SystemClock.uptimeMillis()) }
    LaunchedEffect(blogUrl, postId, startFromBeginning) {
        viewModel.open(blogUrl, postId, startFromBeginning)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is PlayerUiState.Loading -> LoadingState()
        is PlayerUiState.Publication -> PostReaderScreen(current.post, onClose)

        is PlayerUiState.Locked -> EmptyState(
            title = current.title,
            detail = tr(UiText.player_screen_001),
        )

        is PlayerUiState.Unplayable -> EmptyState(
            title = current.title,
            detail = current.reason,
        )

        is PlayerUiState.Failed -> ErrorState(
            detail = current.message,
            onRetry = viewModel::retry,
        )

        is PlayerUiState.Ready -> VideoSurface(
            onClose = onClose,
            state = current,
            interaction = interaction,
            initialSession = { viewModel.sessionStart(current) },
            onProgress = { position, duration, playing -> viewModel.onProgress(current, position, duration, playing) },
            onEnded = { viewModel.onPlaybackEnded(current) },
            onRefresh = viewModel::refresh,
            findNext = { viewModel.nextVideo(current) },
            findAdjacent = { direction -> viewModel.adjacentVideo(current, direction) },
            onPlayNext = viewModel::playNext,
        )
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun VideoSurface(
    onClose: () -> Unit,
    state: PlayerUiState.Ready,
    interaction: PlaybackInteraction,
    initialSession: () -> Pair<Long, Boolean>,
    onProgress: (Long, Long, Boolean) -> Unit,
    onEnded: () -> Unit,
    onRefresh: (positionMs: Long, force: Boolean) -> Boolean,
    findNext: suspend () -> Post?,
    findAdjacent: suspend (VideoDirection) -> AdjacentVideoResult,
    onPlayNext: (Post) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { PlayerPreferences(context) }
    val queue = LocalCatalogControls.current
    val currentQueue by androidx.compose.runtime.rememberUpdatedState(queue)
    val options = remember(state.postId, state.videoId) { preferences.load() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val start = remember(state.generation, state.postId, state.videoId, state.candidates) { initialSession() }

    val player = remember(state.generation, state.postId, state.videoId, state.candidates, state.startPositionMs) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(options.seekBackSeconds * 1_000L)
            .setSeekForwardIncrementMs(options.seekForwardSeconds * 1_000L)
            .build()
            .apply { setPlaybackSpeed(preferences.playbackSpeed(state.blogUrl)) }
    }

    // Index into state.candidates. On a fatal error the controller steps to the
    // next candidate -- a different rendition, or the same one on the failover
    // host -- because ExoPlayer's own retry only ever re-tries the same URL.
    val fallback = remember(player) {
        val metadata = MediaMetadata.Builder().setTitle(state.title).setArtist(state.blogTitle)
            .setArtworkUri(state.previewUrl?.let(android.net.Uri::parse)).build()
        FallbackController(player, state.candidates) { buildMediaSource(it, metadata = metadata, mediaId = state.postId) }
    }
    val quality = remember(player) {
        VideoQualityController(player, fallback, state.candidates, onManualChoice = preferences::savePreferredQuality)
    }
    val visibility = remember(player) { PlaybackVisibility(start.second,
        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var mediaReady by remember(player) { mutableStateOf(false) }
    var playbackError by remember(player) { mutableStateOf<String?>(null) }
    var playbackEnded by remember(player) { mutableStateOf(false) }
    var surface by remember(player) { mutableStateOf<TvPlayerView?>(null) }
    var navigationRequest by remember(player) { mutableStateOf<VideoDirection?>(null) }
    val stalls = remember(player) { StallDetector() }
    val refreshForPlayer = remember(player) { onRefresh }
    // Keep callbacks bound to this player's video, including its final checkpoint
    // when a new Ready state replaces it during the same composition.
    val progressForPlayer = remember(player) { onProgress }
    val endedForPlayer = remember(player) { onEnded }
    fun checkpoint() {
        // Before READY a surface can report 0 while a saved seek is still pending.
        if (mediaReady) progressForPlayer(player.currentPosition.coerceAtLeast(0),
            player.duration.coerceAtLeast(0), visibility.desired(player.playWhenReady))
    }

    DisposableEffect(player) {
        val session = createPlaybackSession(context, player, state.postId)
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (fallback.advance(error)) return
                // Every link of this post failed together: most likely they expired.
                if (refreshForPlayer(player.currentPosition.coerceAtLeast(0), false)) return
                playbackError = error.localizedMessage ?: tr(UiText.player_screen_002)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                trackStall()
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                    mediaReady = true
                    // History starts once media is ready and retains its final checkpoint,
                    // independently of the resume point removed when a video finishes.
                    checkpoint()
                }
                playbackEnded = playbackState == Player.STATE_ENDED
                if (playbackEnded) endedForPlayer()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                trackStall(); checkpoint()
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo,
                reason: Int) { checkpoint() }

            /** Only buffering the viewer is waiting for counts; a paused load does not. */
            private fun trackStall() {
                val now = SystemClock.elapsedRealtime()
                if (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
                    stalls.bufferingStarted(now)
                } else {
                    stalls.bufferingEnded(now)
                }
            }

            override fun onTracksChanged(tracks: Tracks) = quality.onTracksChanged()

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                preferences.saveSpeed(playbackParameters.speed, state.blogUrl)
            }
        }
        player.addListener(listener)
        quality.preselect(preferences.preferredQuality())
        fallback.start(startPositionMs = start.first,
            playWhenReady = start.second && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))

        onDispose {
            quality.dismiss()
            checkpoint()
            player.removeListener(listener)
            session.release()
            player.release()
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    visibility.leave(player.playWhenReady)
                    checkpoint()
                    player.pause()
                }
                Lifecycle.Event.ON_RESUME -> if (visibility.enter()) player.play()
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
                checkpoint()
            }
        }
    }

    // Buffering that does not end: fetch fresh links and continue at the same place.
    LaunchedEffect(player) {
        while (isActive) {
            delay(2_000)
            stalls.recoverIfStalled(SystemClock.elapsedRealtime()) {
                refreshForPlayer(player.currentPosition.coerceAtLeast(0), false)
            }
        }
    }

    // Sleep timer: pause after the configured time without a remote key press.
    LaunchedEffect(player) {
        if (options.sleepTimerMs <= 0) return@LaunchedEffect
        while (isActive) {
            delay(15_000)
            val view = surface ?: continue
            if (sleepTimerDue(SystemClock.uptimeMillis(), view.lastInteractionAt, options.sleepTimerMs, player.isPlaying)) {
                player.pause()
                view.showController()
                view.showNotice(tr(UiText.player_screen_003))
            }
        }
    }

    LaunchedEffect(player, playbackEnded) {
        if (!playbackEnded) return@LaunchedEffect
        val continuation = queueContinuation(currentQueue.queue, state.postId)
        val queuedNext = continuation.next
        if (queuedNext != null) {
            surface?.showNextUp(queuedNext.title, NEXT_UP_SECONDS, onPlay = {
                continuation.completed.forEach(currentQueue.remove)
                onPlayNext(queuedNext.toPost())
            }, onCancel = { surface?.showController() })
            return@LaunchedEffect
        }
        continuation.completed.forEach(currentQueue.remove)
        when (options.endAction) {
            EndAction.Stop -> Unit
            EndAction.Repeat -> {
                player.seekTo(0)
                player.play()
            }
            EndAction.Close -> onClose()
            EndAction.Next -> {
                val next = findNext() ?: return@LaunchedEffect
                surface?.showNextUp(next.title, NEXT_UP_SECONDS, onPlay = { onPlayNext(next) },
                    onCancel = { surface?.showController() })
            }
        }
    }

    // One navigation request at a time; replacing/closing this player cancels it.
    LaunchedEffect(player, navigationRequest) {
        val direction = navigationRequest ?: return@LaunchedEffect
        surface?.showNotice(if (direction == VideoDirection.Next) tr(UiText.player_screen_004) else tr(UiText.player_screen_005))
        when (val result = findAdjacent(direction)) {
            is AdjacentVideoResult.Found -> onPlayNext(result.post)
            AdjacentVideoResult.Boundary -> surface?.showNotice(
                if (direction == VideoDirection.Next) tr(UiText.player_screen_006) else tr(UiText.player_screen_007))
            AdjacentVideoResult.Unavailable -> surface?.showNotice(tr(UiText.player_screen_008))
        }
        navigationRequest = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (playbackError == null) TvPlayerHost(
            onDismiss = onClose,
            factory = { ctx ->
                val view = TvPlayerView(ctx, interaction)
                surface = view
                view.apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = options.controllerTimeoutMs
                    setShowRewindButton(true)
                    setShowFastForwardButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    configureControls(preferences.load(), onNavigate = { direction ->
                        if (navigationRequest == null) navigationRequest = direction
                    }, onScaleChanged = { scale ->
                        // Read the latest preferences so this button never overwrites other choices.
                        preferences.save(preferences.load().copy(videoScale = scale))
                    })
                    quality.attach(view)
                    applyPlayerAppearance(view, state.title)
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            onRelease = { it.player = null },
            update = {
                it.player = player
                it.controllerShowTimeoutMs = if (playbackEnded) 0 else options.controllerTimeoutMs
                if (playbackEnded && !it.isNextUpShown) it.showController()
            },
            modifier = Modifier.fillMaxSize(),
        ) else playbackError?.let { detail ->
            ErrorState(
                title = tr(UiText.player_screen_009),
                detail = detail,
                // The viewer's own retry always fetches fresh links, unlike the same dead ones.
                onRetry = { refreshForPlayer(player.currentPosition.coerceAtLeast(0), true) },
            )
        }
    }
}

private const val NEXT_UP_SECONDS = 8
