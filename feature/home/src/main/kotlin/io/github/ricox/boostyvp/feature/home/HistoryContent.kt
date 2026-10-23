package io.github.ricox.boostyvp.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.appearance.LocalCardSize
import io.github.ricox.boostyvp.core.ui.appearance.LocalInterfaceOptions
import io.github.ricox.boostyvp.core.ui.component.*
import io.github.ricox.boostyvp.core.ui.theme.Overscan

@Composable
internal fun HistoryContent(
    state: HistoryUiState,
    gridState: LazyGridState,
    onPostClick: (Post) -> Unit,
    onPostLongClick: (Post) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        HistoryUiState.Loading -> { LoadingState(); return }
        HistoryUiState.Failed -> { ErrorState(title = "Не удалось открыть историю",
            detail = "Попробуйте ещё раз. Записи не удалены.", onRetry = onRetry); return }
        is HistoryUiState.Content -> if (state.entries.isEmpty()) {
            EmptyState(title = "История пока пуста", detail = "Здесь появятся видео, которые вы смотрите на этом ТВ.\nИстория хранится отдельно для каждого аккаунта.")
            return
        }
    }
    val entries = (state as HistoryUiState.Content).entries
    val posts = remember(entries) { entries.map { it.toCardPost() } }
    val fractions = remember(entries) { entries.associate { it.postId to it.fraction } }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastFocused by remember { mutableStateOf<String?>(null) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val ids = posts.map { it.id }
    // A just-watched card may move to the front while its player covers the grid.
    // Restore by post key, not its old index or a focus node recycled off screen.
    LaunchedEffect(ids, windowFocused) {
        val id = lastFocused ?: return@LaunchedEffect
        if (!windowFocused) return@LaunchedEffect
        val index = ids.indexOf(id)
        if (index < 0) { lastFocused = null; return@LaunchedEffect }
        if (gridState.layoutInfo.visibleItemsInfo.none { it.key == id }) gridState.scrollToItem(index)
        withFrameNanos { }
        requesters[id]?.requestFocus()
    }
    val density = LocalInterfaceOptions.current.density
    CompositionLocalProvider(LocalWatchProgress provides fractions) {
        LazyVerticalGrid(state = gridState, columns = GridCells.Adaptive(LocalCardSize.current.widthDp.dp),
            horizontalArrangement = Arrangement.spacedBy(density.horizontalGapDp.dp),
            verticalArrangement = Arrangement.spacedBy(density.gridGapDp.dp),
            contentPadding = PaddingValues(start = 24.dp, end = Overscan.Horizontal, top = 10.dp, bottom = Overscan.Vertical),
            modifier = Modifier.fillMaxSize().testTag("history_grid")) {
            items(posts, key = { it.id }) { post ->
                val requester = remember(post.id) { FocusRequester() }
                DisposableEffect(post.id, requester) {
                    requesters[post.id] = requester
                    onDispose { if (requesters[post.id] === requester) requesters.remove(post.id) }
                }
                PostCard(post, { onPostClick(post) },
                    Modifier.focusRequester(requester).onFocusChanged { if (it.isFocused) lastFocused = post.id }
                        .testTag("history_post_${post.id}"),
                    onLongClick = { onPostLongClick(post) })
            }
        }
    }
}
