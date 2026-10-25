package io.github.ricox.boostyvp.feature.home

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.appearance.LocalCardSize
import io.github.ricox.boostyvp.core.ui.appearance.LocalInterfaceOptions
import io.github.ricox.boostyvp.core.ui.component.EmptyState
import io.github.ricox.boostyvp.core.ui.component.ErrorState
import io.github.ricox.boostyvp.core.ui.component.LoadingState
import io.github.ricox.boostyvp.core.ui.component.PostCard
import io.github.ricox.boostyvp.core.ui.theme.Overscan

/** The "Новое" section: one date-ordered grid across all subscriptions. */
@Composable
internal fun NewFeedContent(
    posts: LazyPagingItems<Post>,
    gridState: LazyGridState,
    onPostClick: (Post) -> Unit,
    onPostLongClick: (Post) -> Unit,
    onlyUnwatched: Boolean = false,
) {
    val refresh = posts.loadState.refresh
    val density = LocalInterfaceOptions.current.density
    when {
        refresh is LoadState.Error && posts.itemCount == 0 -> {
            ErrorState(
                title = tr(UiText.new_feed_content_001),
                detail = refresh.error.message,
                onRetry = { posts.retry() },
            )
            return
        }
        posts.itemCount == 0 && (refresh is LoadState.Loading || posts.loadState.append is LoadState.Loading) -> {
            LoadingState()
            return
        }
        posts.itemCount == 0 -> {
            EmptyState(
                title = if (onlyUnwatched) tr(UiText.blog_screen_005) else tr(UiText.new_feed_content_002),
                detail = if (onlyUnwatched) tr(UiText.home_screen_012) else tr(UiText.new_feed_content_003),
            )
            return
        }
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = LocalCardSize.current.widthDp.dp),
        horizontalArrangement = Arrangement.spacedBy(density.horizontalGapDp.dp),
        verticalArrangement = Arrangement.spacedBy(density.gridGapDp.dp),
        contentPadding = PaddingValues(start = 24.dp, end = Overscan.Horizontal, top = 10.dp,
            bottom = Overscan.Vertical),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            count = posts.itemCount,
            key = { index -> posts.peek(index)?.id ?: "placeholder_$index" },
        ) { index ->
            val post = posts[index]
            if (post != null) PostCard(post = post, onClick = { onPostClick(post) }, onLongClick = { onPostLongClick(post) })
        }
        if (posts.loadState.append is LoadState.Error) {
            item(key = "new_append_error", span = { GridItemSpan(maxLineSpan) }) {
                ErrorState(
                    detail = tr(UiText.blog_screen_009),
                    onRetry = { posts.retry() },
                    modifier = Modifier.height(180.dp),
                )
            }
        }
    }
}
