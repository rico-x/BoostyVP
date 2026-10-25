package io.github.ricox.boostyvp.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.repository.PersonalLibraryRepository
import io.github.ricox.boostyvp.core.model.PersonalLibrary
import io.github.ricox.boostyvp.core.ui.component.LibraryControls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PersonalLibraryViewModel @Inject constructor(private val repository: PersonalLibraryRepository) : ViewModel() {
    private val attempts = MutableStateFlow(0)
    val controls = attempts.flatMapLatest {
        repository.entries().map { controls(it) }
            .onStart { emit(LibraryControls()) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(LibraryControls(failed = true, retry = { attempts.value++ }))
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryControls())

    private fun controls(library: PersonalLibrary) = LibraryControls(library, ready = true, retry = { attempts.value++ },
        setLater = repository::setLater, setAuthor = repository::setFavoriteAuthor,
        setWatched = repository::setWatched, setFavoritePost = repository::setFavoritePost)
}
