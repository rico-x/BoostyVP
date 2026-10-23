package io.github.ricox.boostyvp.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.appearance.InterfaceSettingsDialog
import io.github.ricox.boostyvp.core.ui.appearance.LocalInterfaceOptions
import io.github.ricox.boostyvp.core.ui.appearance.BrowseDimensions
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import io.github.ricox.boostyvp.core.ui.component.EmptyState
import io.github.ricox.boostyvp.core.ui.component.ErrorState
import io.github.ricox.boostyvp.core.ui.component.LoadingState
import io.github.ricox.boostyvp.core.ui.component.PostRow
import io.github.ricox.boostyvp.core.ui.component.focusedTitleSpace
import io.github.ricox.boostyvp.core.ui.component.ScreenBackdrop
import io.github.ricox.boostyvp.core.ui.component.SignOutDialog
import io.github.ricox.boostyvp.core.ui.theme.Overscan
import io.github.ricox.boostyvp.core.ui.player.PlayerPreferences
import io.github.ricox.boostyvp.core.ui.player.PlayerSettingsDialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun HomeScreen(
    onPostClick: (Post) -> Unit,
    onBlogClick: (String) -> Unit,
    onSignOut: () -> Unit,
    signingOut: Boolean = false,
    signOutError: String? = null,
    viewModel: HomeViewModel = hiltViewModel(),
    newFeedViewModel: NewFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    var showSignOut by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val preferences = remember(context) { PlayerPreferences(context) }
    var options by remember { mutableStateOf(preferences.load()) }
    var showPlayerSettings by rememberSaveable { mutableStateOf(false) }
    var showInterfaceSettings by rememberSaveable { mutableStateOf(false) }
    HomeLayout(state, onPostClick, onBlogClick, { showSignOut = true }, viewModel::refresh,
        onPlayerSettings = { options = preferences.load(); showPlayerSettings = true },
        onInterfaceSettings = { showInterfaceSettings = true },
        newPosts = newFeedViewModel.posts, continueWatching = continueWatching)
    if (showInterfaceSettings) InterfaceSettingsDialog(onDismiss = { showInterfaceSettings = false })
    if (showPlayerSettings) PlayerSettingsDialog(options, onChange = {
        options = it
        preferences.save(it)
    }, onDismiss = { showPlayerSettings = false })
    if (showSignOut) SignOutDialog(
        onConfirm = onSignOut, onDismiss = { showSignOut = false },
        inProgress = signingOut, error = signOutError,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeLayout(
    state: HomeUiState,
    onPostClick: (Post) -> Unit,
    onBlogClick: (String) -> Unit,
    onSignOut: () -> Unit,
    onRefresh: () -> Unit,
    onPlayerSettings: () -> Unit = {},
    newPosts: Flow<PagingData<Post>> = emptyFlow(),
    continueWatching: List<Post> = emptyList(),
    onInterfaceSettings: () -> Unit = {},
) {
    val menuFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    var section by rememberSaveable { mutableStateOf(HomeSection.Subscriptions) }
    var focusMenu by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(true) }
    val interfaceOptions = LocalInterfaceOptions.current
    // Survives section switches and the player dialog, like the author grid.
    val newGridState = rememberLazyGridState()
    // Collected only while "Новое" is shown: Home itself never requests the feed.
    val newItems = if (section == HomeSection.New) newPosts.collectAsLazyPagingItems() else null
    val hasContent = if (newItems != null) newItems.itemCount > 0
        else (state as? HomeUiState.Content)?.let { content ->
            continueWatching.isNotEmpty() || content.rows.any { it.posts.isNotEmpty() }
        } == true

    // Back from "Новое" returns to the subscriptions, the screen Back exits from.
    BackHandler(enabled = section != HomeSection.Subscriptions) {
        section = HomeSection.Subscriptions
        focusMenu = true
    }
    // After recomposition [menuFocus] is attached to the newly selected button.
    LaunchedEffect(section, focusMenu) {
        if (focusMenu) {
            focusMenu = false
            menuFocus.requestFocus()
        }
    }

    ScreenBackdrop {
        Box(Modifier.fillMaxSize().padding(bottom = if (interfaceOptions.fullTitle) focusedTitleSpace() else 0.dp)) {
          Box(Modifier.zIndex(1f)) {
            HomeSidebar(
                section = section,
                onSectionClick = { target ->
                    if (target != section) section = target
                    else if (hasContent) contentFocus.requestFocus()
                },
                menuFocus = menuFocus, contentFocus = contentFocus, hasContent = hasContent,
                onRefresh = { if (newItems != null) newItems.refresh() else onRefresh() },
                onSettings = onPlayerSettings, onInterfaceSettings = onInterfaceSettings, onSignOut = onSignOut,
                expanded = !interfaceOptions.autoCollapseSidebar || menuExpanded,
                onMenuFocused = { menuExpanded = true },
            )
          }
          Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.width(BrowseDimensions.contentSidebarWidth(interfaceOptions).dp))
            Column(Modifier.weight(1f).testTag("catalog_viewport")) {
                Column(Modifier.padding(start = 24.dp, end = Overscan.Horizontal,
                    top = Overscan.Vertical, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val (title, subtitle) = when (section) {
                        HomeSection.Subscriptions -> "Ваши подписки" to "Новые публикации по авторам"
                        HomeSection.New -> "Новое" to "Свежие публикации всех подписок по дате"
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.weight(1f).focusRequester(contentFocus)
                    .onFocusChanged { if (it.hasFocus) menuExpanded = false }
                    .focusProperties {
                        exit = { if (it == FocusDirection.Left) menuFocus else FocusRequester.Default }
                    }.focusGroup()) {
                    if (newItems != null) {
                        NewFeedContent(newItems, newGridState, onPostClick)
                    } else when (state) {
                        is HomeUiState.Loading -> LoadingState()
                        is HomeUiState.Error -> ErrorState(detail = state.message, onRetry = onRefresh)
                        is HomeUiState.NoSubscriptions -> EmptyState(
                            title = "Здесь будут ваши авторы",
                            detail = "Подпишитесь на авторов на boosty.to — их публикации появятся на вашем ТВ.")
                        is HomeUiState.Content -> HomeContent(state, continueWatching, onPostClick, onBlogClick, menuFocus)
                    }
                }
            }
          }
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Content,
    continueWatching: List<Post>,
    onPostClick: (Post) -> Unit,
    onBlogClick: (String) -> Unit,
    menuFocus: FocusRequester,
) {
    val rows = state.rows.filter { it.posts.isNotEmpty() }
    if (rows.isEmpty() && continueWatching.isEmpty()) {
        EmptyState(title = "Публикации ещё впереди", detail = "Новые публикации ваших авторов появятся здесь.")
        return
    }
    val listState = rememberLazyListState()
    val density = LocalInterfaceOptions.current.density
    LazyColumn(
        state = listState, modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(density.rowGapDp.dp),
        contentPadding = PaddingValues(bottom = Overscan.Vertical),
    ) {
        if (continueWatching.isNotEmpty()) item(key = "continue_watching") {
            PostRow(
                title = "Продолжить просмотр", posts = continueWatching, onPostClick = onPostClick,
                horizontalPadding = 24.dp, titleModifier = Modifier.testTag("continue_watching"),
                menuFocus = menuFocus,
                compactSingle = true,
            )
        }
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
