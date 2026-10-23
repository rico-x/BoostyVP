package io.github.ricox.boostyvp.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.domain.repository.PlaybackProgressRepository
import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.VideoSource
import io.github.ricox.boostyvp.core.model.WatchProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    ) : PlayerUiState

    /** Subscription tier does not cover this post. */
    data class Locked(val title: String) : PlayerUiState

    data class Unplayable(val title: String, val reason: String) : PlayerUiState

    data class Failed(val message: String) : PlayerUiState
}

@HiltViewModel
class PlayerViewModel(
    private val blogRepository: BlogRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val now: () -> Long,
) : ViewModel() {
    @Inject constructor(blogRepository: BlogRepository, progressRepository: PlaybackProgressRepository) :
        this(blogRepository, progressRepository, System::currentTimeMillis)


    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    /** The post now in the player; [refresh] and [playNext] work from it. */
    private var current: Pair<String, String>? = null
    private var requestedResumeMs: Long? = null
    private var lastRefreshAt: Long? = null

    /** @param resumeAtMs continue here instead of the saved resume point, e.g. after [refresh]. */
    fun load(blogUrl: String, postId: String, resumeAtMs: Long? = null) {
        current = blogUrl to postId
        requestedResumeMs = resumeAtMs
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = PlayerUiState.Loading
            _state.value = try {
                resolve(blogUrl, postId, resumeAtMs).also { currentCoroutineContext().ensureActive() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlayerUiState.Failed("Не удалось загрузить публикацию. Проверьте соединение и повторите попытку.")
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
    suspend fun nextVideo(playback: PlayerUiState.Ready): Post? {
        val blogUrl = playback.blogUrl.ifBlank { current?.first.orEmpty() }
        if (blogUrl.isBlank()) return null
        return try {
            var cursor: String? = null
            var passedCurrent = false
            repeat(NEXT_SEARCH_PAGES) {
                val page = blogRepository.postsPage(blogUrl, cursor)
                for (post in page.items) {
                    if (passedCurrent && post.isPlayable) return post
                    if (post.id == playback.postId) passedCurrent = true
                }
                cursor = page.nextCursor.takeUnless { page.isLast } ?: return null
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun playNext(next: Post) = load(next.blogUrl, next.id)

    /** After a failed load: the post in the player, which may already be a next video. */
    fun retry() {
        val (blogUrl, postId) = current ?: return
        load(blogUrl, postId, requestedResumeMs)
    }

    private suspend fun resolve(blogUrl: String, postId: String, resumeAtMs: Long?): PlayerUiState {
        // Always re-fetch the post rather than passing it in through navigation.
        // Media URLs are short-lived and a post object cached on the home screen
        // minutes ago may already carry dead links.
        val post = blogRepository.post(blogUrl, postId)

        if (!post.hasVideo) return PlayerUiState.Publication(post)

        if (!post.hasAccess) return PlayerUiState.Locked(post.title)
        if (post.isWaitingVideo) {
            return PlayerUiState.Unplayable(post.title, "Автор уже загрузил видео. Оно появится здесь, когда Boosty закончит обработку.")
        }

        val video = post.primaryVideo
            // External embeds may be typed "video" without playable URLs.
            // They still have a readable body and must not become an error.
            ?: return PlayerUiState.Publication(post)

        val candidates = playbackCandidates(video)
        if (candidates.isEmpty()) {
            return PlayerUiState.Unplayable(post.title, "Не удалось получить ссылку на видео. Попробуйте открыть его позже.")
        }

        val resolvedVideoId = video.id.ifBlank { video.vid.orEmpty() }

        // Prefer this device's own resume point; fall back to the position
        // Boosty itself recorded from the user's other devices.
        val localResume = progressRepository.resumePositionMs(postId, resolvedVideoId)
        val boostyResumeMs = (video.timeCode ?: 0).coerceAtLeast(0) * 1000L
        val savedMs = if (localResume > 0) localResume else boostyResumeMs
        val startMs = resumeAtMs ?: resumeStartMs(savedMs, video.duration * 1000L)
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
        )
    }

    fun onProgress(playback: PlayerUiState.Ready, positionMs: Long, durationMs: Long) {
        if (playback.postId.isBlank() || playback.videoId.isBlank()) return
        val progress = WatchProgress(
            postId = playback.postId, videoId = playback.videoId,
            positionMs = positionMs, durationMs = durationMs, updatedAt = now(),
            blogUrl = playback.blogUrl, title = playback.title, blogTitle = playback.blogTitle,
            previewUrl = playback.previewUrl,
        )
        viewModelScope.launch { progressRepository.save(progress) }
    }

    fun onPlaybackEnded(playback: PlayerUiState.Ready) {
        if (playback.postId.isBlank() || playback.videoId.isBlank()) return
        viewModelScope.launch { progressRepository.clear(playback.postId, playback.videoId) }
    }

    private companion object {
        /** One automatic link refresh per two minutes; a second failure is shown. */
        const val REFRESH_COOLDOWN_MS = 120_000L
        /** How far into the author's list to look for the next video. */
        const val NEXT_SEARCH_PAGES = 5
    }
}
