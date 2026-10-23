package io.github.ricox.boostyvp.feature.blog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.paging.CursorPagingSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BlogViewModel @Inject constructor(
    private val repository: BlogRepository,
) : ViewModel() {

    private val _blog = MutableStateFlow<Blog?>(null)
    val blog: StateFlow<Blog?> = _blog.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Drives the pager. Set by [load]; nothing is fetched until it is non-null. */
    private val target = MutableStateFlow<String?>(null)
    private var headerJob: Job? = null

    /**
     * ONE flow instance, created once at construction.
     *
     * The previous version assigned a new Flow to a `var` inside `load()` and
     * exposed it through a getter. That could never work: `collectAsLazyPagingItems`
     * remembers whichever Flow instance it saw during the first composition, and
     * `load()` runs afterwards from a LaunchedEffect. Nothing observed the field,
     * so no recomposition happened, and the screen collected the placeholder
     * `flowOf(PagingData.empty())` for the rest of its life.
     *
     * That is also why it hung on a spinner rather than showing an empty grid:
     * LazyPagingItems starts with refresh = LoadState.Loading, and the no-arg
     * PagingData.empty() carries null load states, which tells the presenter to
     * leave the existing ones alone. Refresh therefore stayed Loading forever.
     *
     * Deriving the pager from a StateFlow keeps the instance stable while still
     * reacting to the blog changing.
     */
    val posts: Flow<PagingData<Post>> = target
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { blogUrl ->
            Pager(
                config = PagingConfig(pageSize = 20, prefetchDistance = 10, enablePlaceholders = false),
                pagingSourceFactory = { CursorPagingSource { repository.postsPage(blogUrl, it) } },
            ).flow
        }
        .cachedIn(viewModelScope)

    fun load(blogUrl: String) {
        if (target.value == blogUrl && (_blog.value != null || headerJob?.isActive == true)) return
        if (target.value != blogUrl) _blog.value = null
        target.value = blogUrl
        loadHeader(blogUrl)
    }

    private fun loadHeader(blogUrl: String) {
        headerJob?.cancel()
        headerJob = viewModelScope.launch {
            _error.value = null
            try {
                _blog.value = repository.blog(blogUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "Could not load this channel"
            }
        }
    }

    /** Retry only the header request; the grid retries through Paging's own retry(). */
    fun retryBlog() {
        target.value?.let(::loadHeader)
    }
}
