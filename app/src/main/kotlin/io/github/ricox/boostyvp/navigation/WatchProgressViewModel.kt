package io.github.ricox.boostyvp.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.repository.PlaybackProgressRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Feeds the progress bars of every card from the signed-in account's library. */
@HiltViewModel
class WatchProgressViewModel @Inject constructor(progress: PlaybackProgressRepository) : ViewModel() {
    val byPost: StateFlow<Map<String, Float>> = progress.progressByPost()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}
