package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.domain.repository.PlaybackProgressRepository
import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.VideoSource
import io.github.ricox.boostyvp.core.model.WatchProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Publication(val post: Post) : PlayerUiState

    data class Ready(
        val postId: String,
        val videoId: String,
        val title: String,
        val video: ContentBlock.Video,
        val candidates: List<VideoSource>,
        val startPositionMs: Long,
        // Saved with each checkpoint to draw the "Продолжить просмотр" card.
        val blogUrl: String = "",
        val blogTitle: String = "",
        val previewUrl: String? = null,
        val startPlayWhenReady: Boolean = true,
        val generation: Long = 0,
    ) : PlayerUiState

    /** Subscription tier does not cover this post. */
    data class Locked(val title: String) : PlayerUiState

    data class Unplayable(val title: String, val reason: String) : PlayerUiState

    data class Failed(val message: String) : PlayerUiState
}

sealed interface AdjacentVideoResult {
    data class Found(val post: Post) : AdjacentVideoResult
    data object Boundary : AdjacentVideoResult
    /** Network failure, repeated cursor or current post outside the bounded search. */
    data object Unavailable : AdjacentVideoResult
}

@HiltViewModel
class PlayerViewModel private constructor(
    private val blogRepository: BlogRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val savedState: SavedStateHandle,
    private val now: () -> Long,
) : ViewModel() {
    constructor(blogRepository: BlogRepository, progressRepository: PlaybackProgressRepository,
        now: () -> Long = System::currentTimeMillis) : this(blogRepository, progressRepository, SavedStateHandle(), now)

    @Inject constructor(blogRepository: BlogRepository, progressRepository: PlaybackProgressRepository,
        savedState: SavedStateHandle) : this(blogRepository, progressRepository, savedState, System::currentTimeMillis)


    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    /** The post now in the player; [refresh] and [playNext] work from it. */
    private var current: Pair<String, String>? = null
    private var requestedResumeMs: Long? = null
    private var lastRefreshAt: Long? = null
    private var entryOpened = false
    private var entryRequest: Triple<String, String, Boolean>? = null
    private var generation = 0L
    private var persistSession = false
    private var checkpoint: PlaybackCheckpoint? = null
    private val progressWrites = Mutex()

    /** A retained dialog/Activity may compose again. Do not reload its original route/video. */
    fun open(blogUrl: String, postId: String, fromStart: Boolean = false) {
        val request = Triple(blogUrl, postId, fromStart)
        if (entryOpened && entryRequest == request) return
        val restoreEntry = !entryOpened
        entryOpened = true
        entryRequest = request
        startLoad(blogUrl, postId, if (fromStart) 0L else null, restoreEntry = restoreEntry)
    }

    /** @param resumeAtMs continue here instead of the saved resume point, e.g. after [refresh]. */
    fun load(blogUrl: String, postId: String, resumeAtMs: Long? = null) {
        startLoad(blogUrl, postId, resumeAtMs)
    }

    private fun startLoad(blogUrl: String, postId: String, resumeAtMs: Long?, restoreEntry: Boolean = false) {
        current = blogUrl to postId
        requestedResumeMs = resumeAtMs
        val nextGeneration = ++generation
        val carried = checkpoint?.takeIf { it.blog == blogUrl && it.post == postId && resumeAtMs != 0L }
        if (!restoreEntry && carried == null) {
            checkpoint = null
            clearSavedCheckpoint()
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = PlayerUiState.Loading
            _state.value = try {
                persistSession = progressRepository.recordsResumePositions()
                val restored = if (restoreEntry && persistSession) readCheckpoint() else null
                if (!persistSession) clearSavedCheckpoint()
                val target = restored?.let { it.blog to it.post } ?: (blogUrl to postId)
                current = target
                val exact = restored ?: carried
                requestedResumeMs = restored?.positionMs ?: resumeAtMs
                val result = resolve(target.first, target.second,
                    if (restored != null) null else resumeAtMs, exact)
                currentCoroutineContext().ensureActive()
                if (result is PlayerUiState.Ready) {
                    result.copy(generation = nextGeneration).also { ready ->
                        checkpoint = PlaybackCheckpoint(ready.blogUrl, ready.postId, ready.videoId,
                            ready.startPositionMs, ready.video.duration * 1_000L, ready.startPlayWhenReady, nextGeneration)
                        saveCheckpoint()
                    }
                } else result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlayerUiState.Failed(tr(UiText.player_view_model_001))
            }
        }
    }

    /**
     * Fetch the post again for fresh media links -- Boosty's signed URLs expire --
     * and continue at [positionMs]. Automatic recovery is rate-limited so a
     * broken video cannot reload forever; [force] is for the viewer's own retry.
     *
     * @return false when an automatic refresh was tried too recently.
     */
    fun refresh(positionMs: Long, force: Boolean = false): Boolean {
        val (blogUrl, postId) = current ?: return false
        val now = now()
        val last = lastRefreshAt
        if (!force && last != null && now - last < REFRESH_COOLDOWN_MS) return false
        lastRefreshAt = now
        load(blogUrl, postId, resumeAtMs = positionMs.coerceAtLeast(0))
        return true
    }

    /**
     * The next video to the right in the author's row: the following post in the
     * author's own list that can actually be played. Null at the end of the list.
     */
    suspend fun nextVideo(playback: PlayerUiState.Ready): Post? =
        (adjacentVideo(playback, VideoDirection.Next) as? AdjacentVideoResult.Found)?.post

    /** Both directions use the author's existing order, skipping text/locked/processing posts. */
    suspend fun adjacentVideo(playback: PlayerUiState.Ready, direction: VideoDirection): AdjacentVideoResult {
        val blogUrl = playback.blogUrl.ifBlank { current?.first.orEmpty() }
        if (blogUrl.isBlank()) return AdjacentVideoResult.Unavailable
        return try {
            var cursor: String? = null
            var passedCurrent = false
            var previous: Post? = null
            val visited = mutableSetOf<String>()
            repeat(NEXT_SEARCH_PAGES) {
                val page = blogRepository.postsPage(blogUrl, cursor)
                for (post in page.items) {
                    if (post.id == playback.postId) {
                        if (direction == VideoDirection.Previous) return previous?.let(AdjacentVideoResult::Found)
                            ?: AdjacentVideoResult.Boundary
                        passedCurrent = true
                    } else if (post.isPlayable) {
                        if (passedCurrent) return AdjacentVideoResult.Found(post)
                        previous = post
                    }
                }
                cursor = page.nextCursor.takeUnless { page.isLast }
                    ?: return if (passedCurrent) AdjacentVideoResult.Boundary else AdjacentVideoResult.Unavailable
                if (!visited.add(requireNotNull(cursor))) return AdjacentVideoResult.Unavailable
            }
            AdjacentVideoResult.Unavailable
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AdjacentVideoResult.Unavailable
        }
    }

    fun playNext(next: Post) = load(next.blogUrl, next.id)

    /** After a failed load: the post in the player, which may already be a next video. */
    fun retry() {
        val (blogUrl, postId) = current ?: return
        load(blogUrl, postId, requestedResumeMs)
    }

    private suspend fun resolve(blogUrl: String, postId: String, resumeAtMs: Long?,
        exact: PlaybackCheckpoint? = null): PlayerUiState {
        // Always re-fetch the post rather than passing it in through navigation.
        // Media URLs are short-lived and a post object cached on the home screen
        // minutes ago may already carry dead links.
        val post = blogRepository.post(blogUrl, postId)

        if (!post.hasVideo) return PlayerUiState.Publication(post)

        if (!post.hasAccess) return PlayerUiState.Locked(post.title)
        if (post.isWaitingVideo) {
            return PlayerUiState.Unplayable(post.title, tr(UiText.player_view_model_002))
        }

        val video = post.primaryVideo
            // External embeds may be typed "video" without playable URLs.
            // They still have a readable body and must not become an error.
            ?: return PlayerUiState.Publication(post)

        val candidates = playbackCandidates(video)
        if (candidates.isEmpty()) {
            return PlayerUiState.Unplayable(post.title, tr(UiText.player_view_model_003))
        }

        val resolvedVideoId = video.id.ifBlank { video.vid.orEmpty() }

        // Prefer this device's own resume point; fall back to the position
        // Boosty itself recorded from the user's other devices.
        val localResume = progressRepository.resumePositionMs(postId, resolvedVideoId)
        val boostyResumeMs = (video.timeCode ?: 0).coerceAtLeast(0) * 1000L
        val savedMs = if (localResume > 0) localResume else boostyResumeMs
        val matching = exact?.takeIf { it.blog == blogUrl && it.post == postId && it.video == resolvedVideoId }
        val startMs = resumeAtMs ?: matching?.positionMs ?: resumeStartMs(savedMs, video.duration * 1000L)
        currentCoroutineContext().ensureActive()

        return PlayerUiState.Ready(
            postId = postId,
            videoId = resolvedVideoId,
            title = post.title,
            video = video,
            candidates = candidates,
            startPositionMs = startMs,
            blogUrl = post.blogUrl.ifBlank { blogUrl },
            blogTitle = post.blogTitle,
            previewUrl = video.previewUrl ?: post.thumbnailUrl,
            startPlayWhenReady = matching?.playWhenReady ?: true,
        )
    }

    internal fun sessionStart(playback: PlayerUiState.Ready): Pair<Long, Boolean> =
        checkpoint?.takeIf { it.matches(playback) && it.generation == playback.generation }
            ?.let { it.positionMs to it.playWhenReady } ?: (playback.startPositionMs to playback.startPlayWhenReady)

    fun onProgress(playback: PlayerUiState.Ready, positionMs: Long, durationMs: Long,
        playWhenReady: Boolean = true) {
        if (playback.postId.isBlank() || playback.videoId.isBlank()) return
        // A disposed rendition must not overwrite a newer restart of the same video.
        if (current == (playback.blogUrl to playback.postId) && playback.generation != generation) return
        if (playback.generation == generation) {
            checkpoint = PlaybackCheckpoint(playback.blogUrl, playback.postId, playback.videoId,
                positionMs.coerceAtLeast(0), durationMs.coerceAtLeast(0), playWhenReady, generation)
            saveCheckpoint()
        }
        val progress = WatchProgress(
            postId = playback.postId, videoId = playback.videoId,
            positionMs = positionMs, durationMs = durationMs, updatedAt = now(),
            blogUrl = playback.blogUrl, title = playback.title, blogTitle = playback.blogTitle,
            previewUrl = playback.previewUrl,
        )
        // Enter the non-cancellable save before navigation can clear this ViewModel.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { progressWrites.withLock { progressRepository.save(progress) } }
        }
    }

    fun onPlaybackEnded(playback: PlayerUiState.Ready) {
        if (playback.postId.isBlank() || playback.videoId.isBlank()) return
        if (current == (playback.blogUrl to playback.postId) && playback.generation != generation) return
        checkpoint?.takeIf { it.matches(playback) && playback.generation == generation }?.let {
            checkpoint = it.copy(playWhenReady = false); saveCheckpoint()
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                progressWrites.withLock { progressRepository.clear(playback.postId, playback.videoId) }
            }
        }
    }

    private fun saveCheckpoint() {
        if (!persistSession) { clearSavedCheckpoint(); return }
        checkpoint?.let { value ->
            savedState[SESSION_BLOG] = value.blog; savedState[SESSION_POST] = value.post
            savedState[SESSION_VIDEO] = value.video; savedState[SESSION_POSITION] = value.positionMs
            savedState[SESSION_DURATION] = value.durationMs; savedState[SESSION_PLAY] = value.playWhenReady
        }
    }
    private fun readCheckpoint(): PlaybackCheckpoint? {
        val blog = savedState.get<String>(SESSION_BLOG)?.takeIf { it.isNotBlank() } ?: return null
        val post = savedState.get<String>(SESSION_POST)?.takeIf { it.isNotBlank() } ?: return null
        val video = savedState.get<String>(SESSION_VIDEO)?.takeIf { it.isNotBlank() } ?: return null
        val position = savedState.get<Long>(SESSION_POSITION)?.takeIf { it >= 0 } ?: return null
        return PlaybackCheckpoint(blog, post, video, position,
            savedState.get<Long>(SESSION_DURATION)?.coerceAtLeast(0) ?: 0,
            savedState.get<Boolean>(SESSION_PLAY) ?: false)
    }
    private fun clearSavedCheckpoint() {
        listOf(SESSION_BLOG, SESSION_POST, SESSION_VIDEO, SESSION_POSITION, SESSION_DURATION, SESSION_PLAY)
            .forEach { savedState.remove<Any>(it) }
    }

    private companion object {
        /** One automatic link refresh per two minutes; a second failure is shown. */
        const val REFRESH_COOLDOWN_MS = 120_000L
        /** How far into the author's list to look for the next video. */
        const val NEXT_SEARCH_PAGES = 5
        const val SESSION_BLOG = "playback_session_blog"
        const val SESSION_POST = "playback_session_post"
        const val SESSION_VIDEO = "playback_session_video"
        const val SESSION_POSITION = "playback_session_position"
        const val SESSION_DURATION = "playback_session_duration"
        const val SESSION_PLAY = "playback_session_play"
    }
}
