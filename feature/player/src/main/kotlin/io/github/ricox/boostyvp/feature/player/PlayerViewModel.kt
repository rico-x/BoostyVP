package io.github.ricox.boostyvp.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.domain.repository.PlaybackProgressRepository
import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.VideoSource
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

    data class Ready(
        val postId: String,
        val videoId: String,
        val title: String,
        val video: ContentBlock.Video,
        val candidates: List<VideoSource>,
        val startPositionMs: Long,
    ) : PlayerUiState

    /** Subscription tier does not cover this post. */
    data class Locked(val title: String) : PlayerUiState

    data class Unplayable(val title: String, val reason: String) : PlayerUiState

    data class Failed(val message: String) : PlayerUiState
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val blogRepository: BlogRepository,
    private val progressRepository: PlaybackProgressRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun load(blogUrl: String, postId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = PlayerUiState.Loading
            _state.value = try {
                resolve(blogUrl, postId).also { currentCoroutineContext().ensureActive() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlayerUiState.Failed(e.message ?: "Не удалось загрузить видео")
            }
        }
    }

    private suspend fun resolve(blogUrl: String, postId: String): PlayerUiState {
        // Always re-fetch the post rather than passing it in through navigation.
        // Media URLs are short-lived and a post object cached on the home screen
        // minutes ago may already carry dead links.
        val post = blogRepository.post(blogUrl, postId)

        if (!post.hasAccess) return PlayerUiState.Locked(post.title)
        if (post.isWaitingVideo) {
            return PlayerUiState.Unplayable(post.title, "Автор уже загрузил видео. Оно появится здесь, когда Boosty закончит обработку.")
        }

        val video = post.primaryVideo
            ?: return PlayerUiState.Unplayable(post.title, "В этой публикации пока нет доступного видео.")

        val candidates = playbackCandidates(video)
        if (candidates.isEmpty()) {
            return PlayerUiState.Unplayable(post.title, "Не удалось получить ссылку на видео. Попробуйте открыть его позже.")
        }

        val resolvedVideoId = video.id.ifBlank { video.vid.orEmpty() }

        // Prefer this device's own resume point; fall back to the position
        // Boosty itself recorded from the user's other devices.
        val localResume = progressRepository.resumePositionMs(postId, resolvedVideoId)
        val boostyResumeMs = (video.timeCode ?: 0).coerceAtLeast(0) * 1000L
        val startMs = if (localResume > 0) localResume else boostyResumeMs
        currentCoroutineContext().ensureActive()

        return PlayerUiState.Ready(
            postId = postId,
            videoId = resolvedVideoId,
            title = post.title,
            video = video,
            candidates = candidates,
            startPositionMs = startMs,
        )
    }

    fun onProgress(playback: PlayerUiState.Ready, positionMs: Long, durationMs: Long) {
        if (playback.postId.isBlank() || playback.videoId.isBlank()) return
        viewModelScope.launch {
            progressRepository.save(playback.postId, playback.videoId, positionMs, durationMs)
        }
    }

    fun onPlaybackEnded(playback: PlayerUiState.Ready) {
        if (playback.postId.isBlank() || playback.videoId.isBlank()) return
        viewModelScope.launch { progressRepository.clear(playback.postId, playback.videoId) }
    }
}
