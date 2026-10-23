package io.github.ricox.boostyvp.feature.home

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
) {
    val refresh = posts.loadState.refresh
    when {
        refresh is LoadState.Error && posts.itemCount == 0 -> {
            ErrorState(
                title = "Не удалось загрузить новые публикации",
                detail = refresh.error.message,
                onRetry = { posts.retry() },
            )
            return
        }
        posts.itemCount == 0 && refresh is LoadState.Loading -> {
            LoadingState()
            return
        }
        posts.itemCount == 0 -> {
            EmptyState(
                title = "Новых публикаций пока нет",
                detail = "Свежие публикации ваших авторов появятся здесь по дате.",
            )
            return
        }
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = LocalCardSize.current.widthDp.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(start = 24.dp, end = Overscan.Horizontal, top = 10.dp,
            bottom = Overscan.Vertical),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            count = posts.itemCount,
            key = { index -> posts.peek(index)?.id ?: "placeholder_$index" },
        ) { index ->
            val post = posts[index]
            if (post != null) PostCard(post = post, onClick = { onPostClick(post) })
        }
        if (posts.loadState.append is LoadState.Error) {
            item(key = "new_append_error", span = { GridItemSpan(maxLineSpan) }) {
                ErrorState(
                    detail = "Не удалось загрузить следующие публикации.",
                    onRetry = { posts.retry() },
                    modifier = Modifier.height(180.dp),
                )
            }
        }
    }
}
