package io.github.ricox.boostyvp.feature.blog

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.appearance.LocalCardSize
import io.github.ricox.boostyvp.core.ui.appearance.LocalInterfaceOptions
import io.github.ricox.boostyvp.core.ui.component.EmptyState
import io.github.ricox.boostyvp.core.ui.component.ErrorState
import io.github.ricox.boostyvp.core.ui.component.LoadingState
import io.github.ricox.boostyvp.core.ui.component.ScreenBackdrop
import io.github.ricox.boostyvp.core.ui.component.ScreenHeading
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.ricox.boostyvp.core.ui.component.PostCard
import io.github.ricox.boostyvp.core.ui.component.PostActionsDialog
import io.github.ricox.boostyvp.core.ui.component.LocalLibraryControls
import io.github.ricox.boostyvp.core.ui.component.TvActionButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.testTag
import androidx.paging.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import io.github.ricox.boostyvp.core.ui.component.focusedTitleSpace
import io.github.ricox.boostyvp.core.ui.theme.Overscan

@Composable
fun BlogScreen(
    blogUrl: String,
    onPostClick: (Post) -> Unit,
    onBack: () -> Unit,
    viewModel: BlogViewModel = hiltViewModel(),
    onPostFromStart: (Post) -> Unit = onPostClick,
) {
    var actionPost by remember { mutableStateOf<Post?>(null) }
    LaunchedEffect(blogUrl) { viewModel.load(blogUrl) }

    val blog by viewModel.blog.collectAsStateWithLifecycle()
    val controls = LocalLibraryControls.current
    val watched = controls.library.watched
    var onlyUnwatched by rememberSaveable(blogUrl) { mutableStateOf(false) }
    var saving by remember(blogUrl) { mutableStateOf(false) }
    var saveError by remember(blogUrl) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val headerError by viewModel.error.collectAsStateWithLifecycle()
    val filteredPosts = remember(viewModel.posts, watched, onlyUnwatched) {
        if (!onlyUnwatched) viewModel.posts else viewModel.posts.map { data -> data.filter { it.id !in watched } }
    }
    val posts = filteredPosts.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    val density = LocalInterfaceOptions.current.density

    val refreshState = posts.loadState.refresh

    // Paging failures used to vanish: only the header request's error was ever
    // shown, so a 401 or a dead connection on the post list left a blank grid
    // and no explanation. Paging's own error state is checked first now, and
    // retried through retry() rather than by re-running the whole load.
    if (refreshState is LoadState.Error) {
        ErrorState(
            title = tr(UiText.blog_screen_001),
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
            columns = GridCells.Adaptive(minSize = LocalCardSize.current.widthDp.dp),
            horizontalArrangement = Arrangement.spacedBy(density.horizontalGapDp.dp),
            verticalArrangement = Arrangement.spacedBy(density.gridGapDp.dp),
            contentPadding = PaddingValues(
                start = Overscan.Horizontal,
                end = Overscan.Horizontal,
                top = Overscan.Vertical,
                bottom = Overscan.Vertical,
            ),
            modifier = Modifier.fillMaxSize().padding(bottom = if (LocalInterfaceOptions.current.fullTitle) focusedTitleSpace() else 0.dp),
        ) {
            // The header scrolls with the posts, leaving the entire TV viewport
            // available to the grid when navigating down with the remote.
            item(key = "blog_header", span = { GridItemSpan(maxLineSpan) }) {
                BlogHeader(blog = blog, blogUrl = blogUrl, onBack = onBack)
            }
            item(key = "videos_heading", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(UiText.interface_preview_007), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() })
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvActionButton(if (onlyUnwatched) tr(UiText.blog_screen_002) else tr(UiText.blog_screen_003),
                            { onlyUnwatched = !onlyUnwatched }, Modifier.testTag("blog_unwatched"))
                        if (controls.ready && controls.setAuthor != null) {
                            val favorite = controls.library.authors.any { it.blogUrl == blogUrl }
                            TvActionButton(if (favorite) tr(UiText.blog_screen_004) else tr(UiText.post_actions_dialog_014), {
                                saving = true; saveError = false
                                scope.launch {
                                    try { controls.setAuthor?.invoke(blogUrl, blog?.title.orEmpty(), !favorite) }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { saveError = true }
                                    finally { saving = false }
                                }
                            }, Modifier.testTag("blog_favorite"), enabled = !saving)
                        }
                    }
                    if (saveError) Text(tr(UiText.post_actions_dialog_001))
                }
            }
            if (posts.itemCount == 0) {
                item(key = "blog_empty", span = { GridItemSpan(maxLineSpan) }) {
                    if (refreshState is LoadState.Loading) {
                        LoadingState(modifier = Modifier.height(180.dp))
                    } else {
                        EmptyState(
                            title = if (onlyUnwatched) tr(UiText.blog_screen_005) else tr(UiText.blog_screen_006),
                            detail = if (onlyUnwatched) tr(UiText.blog_screen_007) else tr(UiText.blog_screen_008),
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
                    PostCard(post = post, onClick = { onPostClick(post) }, inAuthorRow = true,
                        onLongClick = { actionPost = post })
                }
            }
            if (posts.loadState.append is LoadState.Error) {
                item(key = "blog_append_error", span = { GridItemSpan(maxLineSpan) }) {
                    ErrorState(
                        detail = tr(UiText.blog_screen_009),
                        onRetry = { posts.retry() },
                        modifier = Modifier.height(180.dp),
                    )
                }
            }
        }
    }
    actionPost?.let { post ->
        PostActionsDialog(post, onOpen = { onPostClick(post) }, onDismiss = { actionPost = null },
            onFromStart = if (post.isPlayable) ({ onPostFromStart(post) }) else null)
    }
}
