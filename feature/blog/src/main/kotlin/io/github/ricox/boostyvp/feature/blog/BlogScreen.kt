package io.github.ricox.boostyvp.feature.blog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.component.EmptyState
import io.github.ricox.boostyvp.core.ui.component.ErrorState
import io.github.ricox.boostyvp.core.ui.component.LoadingState
import io.github.ricox.boostyvp.core.ui.component.ScreenBackdrop
import io.github.ricox.boostyvp.core.ui.component.ScreenHeading
import io.github.ricox.boostyvp.core.ui.component.PostCard
import io.github.ricox.boostyvp.core.ui.theme.Overscan

@Composable
fun BlogScreen(
    blogUrl: String,
    onPostClick: (Post) -> Unit,
    onBack: () -> Unit,
    viewModel: BlogViewModel = hiltViewModel(),
) {
    LaunchedEffect(blogUrl) { viewModel.load(blogUrl) }

    val blog by viewModel.blog.collectAsStateWithLifecycle()
    val headerError by viewModel.error.collectAsStateWithLifecycle()
    val posts = viewModel.posts.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()

    val refreshState = posts.loadState.refresh

    // Paging failures used to vanish: only the header request's error was ever
    // shown, so a 401 or a dead connection on the post list left a blank grid
    // and no explanation. Paging's own error state is checked first now, and
    // retried through retry() rather than by re-running the whole load.
    if (refreshState is LoadState.Error) {
        ErrorState(
            title = "Не удалось загрузить видео",
            detail = refreshState.error.message,
            onRetry = { posts.retry() },
        )
        return
    }

    val message = headerError
    if (message != null && posts.itemCount == 0) {
        ErrorState(detail = message, onRetry = viewModel::retryBlog)
        return
    }

    if (refreshState is LoadState.Loading && posts.itemCount == 0 && blog == null) {
        LoadingState()
        return
    }

    ScreenBackdrop {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 216.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(
                start = Overscan.Horizontal,
                end = Overscan.Horizontal,
                top = Overscan.Vertical,
                bottom = Overscan.Vertical,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            // The header scrolls with the posts, leaving the entire TV viewport
            // available to the grid when navigating down with the remote.
            item(key = "blog_header", span = { GridItemSpan(maxLineSpan) }) {
                BlogHeader(blog = blog, blogUrl = blogUrl, onBack = onBack)
            }
            item(key = "videos_heading", span = { GridItemSpan(maxLineSpan) }) {
                ScreenHeading("Видео автора")
            }
            if (posts.itemCount == 0) {
                item(key = "blog_empty", span = { GridItemSpan(maxLineSpan) }) {
                    if (refreshState is LoadState.Loading) {
                        LoadingState(modifier = Modifier.height(180.dp))
                    } else {
                        EmptyState(
                            title = "Пока нет видео",
                            detail = "Здесь появятся видеопубликации автора.",
                            modifier = Modifier.height(180.dp),
                        )
                    }
                }
            }
            items(
                count = posts.itemCount,
                key = { index -> posts.peek(index)?.id ?: "placeholder_$index" },
            ) { index ->
                val post = posts[index]
                if (post != null) {
                    PostCard(post = post, onClick = { onPostClick(post) })
                }
            }
            if (posts.loadState.append is LoadState.Error) {
                item(key = "blog_append_error", span = { GridItemSpan(maxLineSpan) }) {
                    ErrorState(
                        detail = "Не удалось загрузить следующие публикации.",
                        onRetry = { posts.retry() },
                        modifier = Modifier.height(180.dp),
                    )
                }
            }
        }
    }
}
