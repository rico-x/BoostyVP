package io.github.ricox.boostyvp.feature.home

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

data class HomeRow(val blog: Blog, val posts: List<Post>)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data object NoSubscriptions : HomeUiState
    data class Content(val rows: List<HomeRow>) : HomeUiState
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

    init { refresh() }

    fun refresh() {
        refreshJob?.cancel()
        _state.value = HomeUiState.Loading
        refreshJob = viewModelScope.launch {
            try {
                val feed = getHomeFeed()
                currentCoroutineContext().ensureActive()
                _state.value = if (feed.hasSubscriptions) {
                    HomeUiState.Content(feed.rows.map { HomeRow(it.blog, it.posts) })
                } else {
                    HomeUiState.NoSubscriptions
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = HomeUiState.Error(e.message ?: "Не удалось подключиться к Boosty")
            }
        }
    }
}
