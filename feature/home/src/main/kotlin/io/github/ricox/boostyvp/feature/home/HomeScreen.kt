package io.github.ricox.boostyvp.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.component.EmptyState
import io.github.ricox.boostyvp.core.ui.component.ErrorState
import io.github.ricox.boostyvp.core.ui.component.LoadingState
import io.github.ricox.boostyvp.core.ui.component.PostRow
import io.github.ricox.boostyvp.core.ui.component.ScreenBackdrop
import io.github.ricox.boostyvp.core.ui.component.SignOutDialog
import io.github.ricox.boostyvp.core.ui.theme.Overscan
import io.github.ricox.boostyvp.core.ui.player.PlayerPreferences
import io.github.ricox.boostyvp.core.ui.player.PlayerSettingsDialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun HomeScreen(
    onPostClick: (Post) -> Unit,
    onBlogClick: (String) -> Unit,
    onSignOut: () -> Unit,
    signingOut: Boolean = false,
    signOutError: String? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSignOut by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val preferences = remember(context) { PlayerPreferences(context) }
    var options by remember { mutableStateOf(preferences.load()) }
    var showPlayerSettings by rememberSaveable { mutableStateOf(false) }
    HomeLayout(state, onPostClick, onBlogClick, { showSignOut = true }, viewModel::refresh,
        onPlayerSettings = { options = preferences.load(); showPlayerSettings = true })
    if (showPlayerSettings) PlayerSettingsDialog(options, onChange = {
        options = it
        preferences.save(it)
    }, onDismiss = { showPlayerSettings = false })
    if (showSignOut) SignOutDialog(
        onConfirm = onSignOut, onDismiss = { showSignOut = false },
        inProgress = signingOut, error = signOutError,
    )
}

@Composable
fun HomeLayout(
    state: HomeUiState,
    onPostClick: (Post) -> Unit,
    onBlogClick: (String) -> Unit,
    onSignOut: () -> Unit,
    onRefresh: () -> Unit,
    onPlayerSettings: () -> Unit = {},
) {
    val menuFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val hasContent = (state as? HomeUiState.Content)?.rows?.any { it.posts.isNotEmpty() } == true
    ScreenBackdrop {
        Row(Modifier.fillMaxSize()) {
            HomeSidebar(menuFocus, contentFocus, hasContent, onRefresh, onPlayerSettings, onSignOut)
            Column(Modifier.weight(1f)) {
                Column(Modifier.padding(start = 24.dp, end = Overscan.Horizontal,
                    top = Overscan.Vertical, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ваши подписки", style = MaterialTheme.typography.headlineSmall)
                    Text("Новые публикации по авторам", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // This focus target stays attached even after the first author
                // heading has been recycled by LazyColumn.
                Box(Modifier.weight(1f).focusRequester(contentFocus).focusGroup()) {
                    when (state) {
                        is HomeUiState.Loading -> LoadingState()
                        is HomeUiState.Error -> ErrorState(detail = state.message, onRetry = onRefresh)
                        is HomeUiState.NoSubscriptions -> EmptyState(
                            title = "Здесь будут ваши авторы",
                            detail = "Подпишитесь на авторов на boosty.to — их публикации появятся на вашем ТВ.",
                        )
                        is HomeUiState.Content -> HomeContent(state, onPostClick, onBlogClick, menuFocus)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Content,
    onPostClick: (Post) -> Unit,
    onBlogClick: (String) -> Unit,
    menuFocus: FocusRequester,
) {
    val rows = state.rows.filter { it.posts.isNotEmpty() }
    if (rows.isEmpty()) {
        EmptyState(title = "Публикации ещё впереди", detail = "Новые публикации ваших авторов появятся здесь.")
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState, modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = Overscan.Vertical),
    ) {
        items(items = rows, key = { it.blog.url }) { row ->
            PostRow(
                title = row.blog.title.ifBlank { row.blog.url }, authorName = row.blog.ownerName,
                avatarUrl = row.blog.avatarUrl, posts = row.posts, onPostClick = onPostClick,
                onTitleClick = { onBlogClick(row.blog.url) },
                horizontalPadding = 24.dp,
                menuFocus = menuFocus,
            )
        }
    }
}
