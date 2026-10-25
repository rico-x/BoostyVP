package io.github.ricox.boostyvp.feature.home

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.appearance.*
import io.github.ricox.boostyvp.core.ui.component.*
import io.github.ricox.boostyvp.core.ui.theme.Overscan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.tv.material3.Text

@Composable
internal fun PersonalLibraryContent(section: HomeSection, grid: LazyGridState,
    onPostClick: (Post) -> Unit, onActions: (Post) -> Unit, onAuthorClick: (String) -> Unit,
    onRemoved: () -> Unit) {
    val controls = LocalLibraryControls.current
    if (controls.failed) {
        ErrorState(title = tr(UiText.personal_library_content_001), detail = tr(UiText.personal_library_content_002), onRetry = controls.retry)
        return
    }
    if (!controls.ready) { LoadingState(); return }
    if (section == HomeSection.Favorites) {
        FavoriteAuthors(onAuthorClick, onRemoved)
        return
    }
    val saved = if (section == HomeSection.Videos) controls.library.favorites else controls.library.later
    val posts = remember(saved) { saved.map { it.toPost() } }
    if (posts.isEmpty()) {
        EmptyState(title = tr(UiText.personal_library_content_003), detail = if (section == HomeSection.Videos)
            tr(UiText.personal_library_content_004)
            else tr(UiText.personal_library_content_005))
        return
    }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastFocused by remember { mutableStateOf<String?>(null) }
    val focusedWindow = LocalWindowInfo.current.isWindowFocused
    val ids = posts.map { it.id }
    LaunchedEffect(ids, focusedWindow) {
        val id = lastFocused ?: return@LaunchedEffect
        if (!focusedWindow) return@LaunchedEffect
        val index = ids.indexOf(id)
        if (index < 0) { lastFocused = null; return@LaunchedEffect }
        if (grid.layoutInfo.visibleItemsInfo.none { it.key == id }) grid.scrollToItem(index)
        withFrameNanos { }
        requesters[id]?.requestFocus()
    }
    val density = LocalInterfaceOptions.current.density
    LazyVerticalGrid(GridCells.Adaptive(LocalCardSize.current.widthDp.dp), state = grid,
        horizontalArrangement = Arrangement.spacedBy(density.horizontalGapDp.dp),
        verticalArrangement = Arrangement.spacedBy(density.gridGapDp.dp),
        contentPadding = PaddingValues(start = 24.dp, end = Overscan.Horizontal, top = 10.dp, bottom = Overscan.Vertical),
        modifier = Modifier.fillMaxSize().testTag("later_grid")) {
        items(posts, key = { it.id }) { post ->
            val requester = remember(post.id) { FocusRequester() }
            DisposableEffect(post.id, requester) {
                requesters[post.id] = requester
                onDispose { requesters.remove(post.id) }
            }
            PostCard(post, { onPostClick(post) }, Modifier.focusRequester(requester)
                .onFocusChanged { if (it.isFocused) lastFocused = post.id }.testTag("later_post_${post.id}"),
                onLongClick = { onActions(post) })
        }
    }
}

@Composable
private fun FavoriteAuthors(onOpen: (String) -> Unit, onRemoved: () -> Unit) {
    val controls = LocalLibraryControls.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val authors = controls.library.authors
    if (authors.isEmpty()) {
        EmptyState(title = tr(UiText.personal_library_content_006), detail = tr(UiText.personal_library_content_007))
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("favorite_authors"),
        contentPadding = PaddingValues(start = 24.dp, end = Overscan.Horizontal, bottom = Overscan.Vertical),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (error) item { Text(tr(UiText.personal_library_content_008)) }
        items(authors, key = { it.blogUrl }) { author ->
            val focus = remember { FocusRequester() }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(author.title.ifBlank { author.blogUrl }, { onOpen(author.blogUrl) },
                    Modifier.weight(1f).focusRequester(focus).testTag("favorite_author_${author.blogUrl}"), enabled = !busy)
                TvActionButton(tr(UiText.catalog_search_content_011), {
                    busy = true; error = false
                    // The author button survives until the write succeeds. If it was the
                    // last author, the sidebar remains available to the remote.
                    focus.requestFocus()
                    scope.launch {
                        try { controls.setAuthor?.invoke(author.blogUrl, author.title, false); onRemoved() }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = true }
                        finally { busy = false }
                    }
                }, Modifier.testTag("favorite_remove_${author.blogUrl}"), enabled = !busy)
            }
        }
    }
}
