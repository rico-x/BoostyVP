package io.github.ricox.boostyvp.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.repository.FeedRepository
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.paging.CursorPagingSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Every subscription's posts in publication order, from Boosty's own feed.
 *
 * Cold until the "Новое" section first collects it, so opening Home does not
 * cost an extra request. Cached in the ViewModel: returning from the player or
 * switching sections shows the loaded pages again instead of refetching.
 */
@HiltViewModel
class NewFeedViewModel @Inject constructor(feeds: FeedRepository) : ViewModel() {
    val posts: Flow<PagingData<Post>> = Pager(
        config = PagingConfig(pageSize = 20, prefetchDistance = 10, enablePlaceholders = false),
        pagingSourceFactory = { CursorPagingSource { feeds.feedPage(it) } },
    ).flow.cachedIn(viewModelScope)
}
