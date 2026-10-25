package io.github.ricox.boostyvp.feature.home

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.repository.PlaybackProgressRepository
import io.github.ricox.boostyvp.core.domain.usecase.GetHomeFeed
import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.model.Post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeRow(val blog: Blog, val posts: List<Post>, val loading: Boolean = false, val failed: Boolean = false)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data object NoSubscriptions : HomeUiState
    data class Content(val rows: List<HomeRow>, val refreshing: Boolean = false, val refreshFailed: Boolean = false) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeFeed: GetHomeFeed,
    progress: PlaybackProgressRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Read from the local library of the signed-in account; Boosty is not asked. */
    val continueWatching: StateFlow<List<Post>> = progress.continueWatching()
        .map { entries -> entries.map { it.toCardPost() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var refreshJob: Job? = null
    private val authorJobs = mutableMapOf<String, Job>()
    private var generation = 0

    init { refresh() }

    fun refresh() {
        generation++
        authorJobs.values.forEach(Job::cancel); authorJobs.clear()
        refreshJob?.cancel()
        if (_state.value !is HomeUiState.Content) _state.value = HomeUiState.Loading
        refreshJob = viewModelScope.launch {
            try {
                val feed = getHomeFeed { update ->
                    if (update.hasSubscriptions) {
                        val old = (_state.value as? HomeUiState.Content)?.rows.orEmpty().associateBy { it.blog.url }
                        _state.value = HomeUiState.Content(update.rows.map {
                            HomeRow(it.blog, if (it.loading || it.failed) old[it.blog.url]?.posts.orEmpty() else it.posts, it.loading, it.failed)
                        }, update.refreshing)
                    }
                }
                currentCoroutineContext().ensureActive()
                _state.value = if (feed.hasSubscriptions) {
                    val old = (_state.value as? HomeUiState.Content)?.rows.orEmpty().associateBy { it.blog.url }
                    HomeUiState.Content(feed.rows.map { HomeRow(it.blog, if (it.failed) old[it.blog.url]?.posts.orEmpty() else it.posts, it.loading, it.failed) })
                } else {
                    HomeUiState.NoSubscriptions
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val cached = _state.value as? HomeUiState.Content
                _state.value = cached?.copy(refreshing = false, refreshFailed = true)
                    ?: HomeUiState.Error(tr(UiText.home_view_model_001))
            }
        }
    }

    fun retryAuthor(url: String) {
        val content = _state.value as? HomeUiState.Content ?: return
        if (content.refreshing) return
        val row = content.rows.find { it.blog.url == url } ?: return
        if (row.loading) return
        val requestGeneration = generation
        fun replace(value: HomeRow) {
            if (requestGeneration != generation) return
            val current = _state.value as? HomeUiState.Content ?: return
            _state.value = current.copy(rows = current.rows.map { if (it.blog.url == url) value else it })
        }
        replace(row.copy(loading = true, failed = false))
        authorJobs[url] = viewModelScope.launch {
            try { val loaded = getHomeFeed.author(row.blog); replace(HomeRow(loaded.blog, loaded.posts)) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { replace(row.copy(loading = false, failed = true)) }
            finally { if (requestGeneration == generation) authorJobs.remove(url) }
        }
    }
}
