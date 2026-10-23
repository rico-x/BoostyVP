package io.github.ricox.boostyvp.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.repository.WatchHistoryRepository
import io.github.ricox.boostyvp.core.model.WatchProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Content(val entries: List<WatchProgress>) : HistoryUiState
    data object Failed : HistoryUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(private val history: WatchHistoryRepository) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    val state = refresh.flatMapLatest {
        history.entries().map<List<WatchProgress>, HistoryUiState> { HistoryUiState.Content(it) }
            .onStart { emit(HistoryUiState.Loading) }
            .catch { emit(HistoryUiState.Failed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    fun retry() { refresh.value++ }

    suspend fun remove(postId: String): Boolean = try {
        history.removePost(postId)
        true
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { false }
}
