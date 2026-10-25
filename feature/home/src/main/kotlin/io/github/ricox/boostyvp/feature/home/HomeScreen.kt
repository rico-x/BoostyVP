package io.github.ricox.boostyvp.feature.home

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.filter
import kotlinx.coroutines.flow.map
import io.github.ricox.boostyvp.core.ui.component.LocalLibraryControls
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.appearance.InterfaceSettingsDialog
import io.github.ricox.boostyvp.core.ui.appearance.LocalInterfaceOptions
import io.github.ricox.boostyvp.core.ui.appearance.LocalMenuOptions
import io.github.ricox.boostyvp.core.ui.appearance.BrowseDimensions
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import io.github.ricox.boostyvp.core.ui.component.EmptyState
import io.github.ricox.boostyvp.core.ui.component.ErrorState
import io.github.ricox.boostyvp.core.ui.component.LoadingState
import io.github.ricox.boostyvp.core.ui.component.PostRow
import io.github.ricox.boostyvp.core.ui.component.PostActionsDialog
import io.github.ricox.boostyvp.core.ui.component.TvActionButton
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
import kotlinx.coroutines.flow.flowOf

@Composable
fun HomeScreen(
    onPostClick: (Post) -> Unit,
    onBlogClick: (String) -> Unit,
    onSignOut: () -> Unit,
    signingOut: Boolean = false,
    signOutError: String? = null,
    viewModel: HomeViewModel = hiltViewModel(),
    newFeedViewModel: NewFeedViewModel = hiltViewModel(),
    historyViewModel: HistoryViewModel = hiltViewModel(),
    onPostFromStart: (Post) -> Unit = onPostClick,
    onUnifiedSettings: (() -> Unit)? = null,
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
        newPosts = newFeedViewModel.posts, continueWatching = continueWatching,
        history = historyViewModel.state, onHistoryRetry = historyViewModel::retry,
        onRemoveHistory = { historyViewModel.remove(it.id) }, onPostFromStart = onPostFromStart,
        onRetryAuthor = viewModel::retryAuthor, onUnifiedSettings = onUnifiedSettings)
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
    history: Flow<HistoryUiState> = flowOf(HistoryUiState.Content(emptyList())),
    onHistoryRetry: () -> Unit = {},
    onRemoveHistory: (suspend (Post) -> Boolean)? = null,
    onPostFromStart: (Post) -> Unit = onPostClick,
    onRetryAuthor: (String) -> Unit = {},
    onUnifiedSettings: (() -> Unit)? = null,
) {
    val menuFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    var section by rememberSaveable { mutableStateOf(HomeSection.Subscriptions) }
    var focusMenu by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(true) }
    val interfaceOptions = LocalInterfaceOptions.current
    val menuOptions = LocalMenuOptions.current
    LaunchedEffect(menuOptions.hidden) {
        if (section.name.lowercase() in menuOptions.hidden) { section = HomeSection.Subscriptions; focusMenu = true }
    }
    // Survives section switches and the player dialog, like the author grid.
    val newGridState = rememberLazyGridState()
    val historyGridState = rememberLazyGridState()
    val laterGridState = rememberLazyGridState()
    val libraryControls = LocalLibraryControls.current
    val watched = libraryControls.library.watched
    var onlyUnwatched by rememberSaveable { mutableStateOf(false) }
    val filteredPosts = remember(newPosts, watched, onlyUnwatched) {
        if (!onlyUnwatched) newPosts else newPosts.map { data -> data.filter { it.id !in watched } }
    }
    val shownState = if (onlyUnwatched && state is HomeUiState.Content)
        state.copy(rows = state.rows.map { row -> row.copy(posts = row.posts.filter { it.id !in watched }) }) else state
    val historyState = if (section == HomeSection.History)
        history.collectAsStateWithLifecycle(initialValue = HistoryUiState.Loading).value else HistoryUiState.Loading
    var actionPost by remember { mutableStateOf<Post?>(null) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    // A normal author destination removes Home from composition (unlike the
    // player dialog). Re-enter with a real remote focus on the restored section.
    var enteredBefore by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (enteredBefore) focusMenu = true
        enteredBefore = true
    }
    // Collected only while "Новое" is shown: Home itself never requests the feed.
    val newItems = if (section == HomeSection.New) filteredPosts.collectAsLazyPagingItems() else null
    val personalSection = section == HomeSection.Later || section == HomeSection.Favorites || section == HomeSection.Videos
    val hasContent = if (section == HomeSection.Search || section == HomeSection.Queue) true else if (personalSection) libraryControls.failed ||
        (when(section) { HomeSection.Later -> libraryControls.library.later.isNotEmpty()
            HomeSection.Videos -> libraryControls.library.favorites.isNotEmpty()
            else -> libraryControls.library.authors.isNotEmpty() })
        else if (section == HomeSection.History) historyState is HistoryUiState.Failed ||
        (historyState as? HistoryUiState.Content)?.entries?.isNotEmpty() == true
        else if (newItems != null) newItems.itemCount > 0
        else (shownState as? HomeUiState.Content)?.let { content ->
            continueWatching.isNotEmpty() || content.rows.any { it.posts.isNotEmpty() }
        } == true

    // Back from "Новое" returns to the subscriptions, the screen Back exits from.
    BackHandler(enabled = section != HomeSection.Subscriptions && actionPost == null) {
        section = HomeSection.Subscriptions
        focusMenu = true
    }
    // After recomposition [menuFocus] is attached to the newly selected button.
    LaunchedEffect(section, focusMenu, windowFocused, actionPost) {
        if (focusMenu && windowFocused && actionPost == null) {
            withFrameNanos { }
            focusMenu = false
            menuFocus.requestFocus()
        }
    }

    ScreenBackdrop {
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.zIndex(1f)) {
            HomeSidebar(
                section = section,
                onSectionClick = { target ->
                    if (target != section) section = target
                    else if (hasContent) contentFocus.requestFocus()
                },
                menuFocus = menuFocus, contentFocus = contentFocus, hasContent = hasContent,
                onRefresh = { if (personalSection) libraryControls.retry()
                    else if (section == HomeSection.History) onHistoryRetry()
                    else if (newItems != null) newItems.refresh() else onRefresh() },
                onSettings = onPlayerSettings, onInterfaceSettings = onInterfaceSettings, onSignOut = onSignOut,
                onUnifiedSettings = onUnifiedSettings,
                expanded = !interfaceOptions.autoCollapseSidebar || menuExpanded,
                onMenuFocused = { menuExpanded = true },
                onlyUnwatched = onlyUnwatched,
                onToggleUnwatched = if (section == HomeSection.Subscriptions || section == HomeSection.New)
                    ({ onlyUnwatched = !onlyUnwatched }) else null,
            )
          }
          // Reading space belongs to the catalog, not the sidebar. Keeping the
          // menu full-height leaves every destination reachable with History added.
          Row(Modifier.fillMaxSize().padding(bottom = if (interfaceOptions.fullTitle) focusedTitleSpace() else 0.dp)) {
            Spacer(Modifier.width(BrowseDimensions.contentSidebarWidth(interfaceOptions, menuOptions,
                expanded = menuExpanded).dp))
            Column(Modifier.weight(1f).testTag("catalog_viewport")) {
                Column(Modifier.padding(start = 24.dp, end = Overscan.Horizontal,
                    top = Overscan.Vertical, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val (title, subtitle) = when (section) {
                        HomeSection.Subscriptions -> tr(UiText.interface_preview_008) to tr(UiText.home_screen_001)
                        HomeSection.New -> tr(UiText.menu_settings_dialog_002) to tr(UiText.home_screen_002)
                        HomeSection.History -> tr(UiText.menu_settings_dialog_003) to tr(UiText.home_screen_003)
                        HomeSection.Later -> tr(UiText.library_privacy_dialog_005) to tr(UiText.home_screen_004)
                        HomeSection.Favorites -> tr(UiText.library_privacy_dialog_007) to tr(UiText.home_screen_005)
                        HomeSection.Videos -> tr(UiText.library_privacy_dialog_006) to tr(UiText.home_screen_006)
                        HomeSection.Search -> tr(UiText.menu_settings_dialog_004) to tr(UiText.home_screen_007)
                        HomeSection.Queue -> tr(UiText.home_screen_008) to tr(UiText.home_screen_009)
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (onlyUnwatched && (section == HomeSection.Subscriptions || section == HomeSection.New))
                        Text(tr(UiText.home_screen_010), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    if (section == HomeSection.Subscriptions && state is HomeUiState.Content && state.refreshFailed)
                        Text(tr(UiText.home_screen_011), style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.weight(1f).focusRequester(contentFocus)
                    .onFocusChanged { if (it.hasFocus) menuExpanded = false }
                    .focusProperties {
                        exit = { if (it == FocusDirection.Left) menuFocus else FocusRequester.Default }
                    }.focusGroup()) {
                    if (section == HomeSection.Search) {
                        CatalogSearchContent(onPostClick, { actionPost = it })
                    } else if (section == HomeSection.Queue) {
                        QueueContent(onPostClick)
                    } else if (personalSection) {
                        PersonalLibraryContent(section, laterGridState, onPostClick, { actionPost = it }, onBlogClick,
                            onRemoved = { focusMenu = true })
                    } else if (section == HomeSection.History) {
                        HistoryContent(historyState, historyGridState, onPostClick, { actionPost = it }, onHistoryRetry)
                    } else if (newItems != null) {
                        NewFeedContent(newItems, newGridState, onPostClick, { actionPost = it }, onlyUnwatched)
                    } else if (onlyUnwatched && shownState is HomeUiState.Content &&
                        shownState.rows.all { it.posts.isEmpty() && !it.loading && !it.failed } && continueWatching.none { it.id !in watched }) {
                        EmptyState(title = tr(UiText.blog_screen_005), detail = tr(UiText.home_screen_012))
                    } else when (val state = shownState) {
                        is HomeUiState.Loading -> LoadingState()
                        is HomeUiState.Error -> ErrorState(detail = state.message, onRetry = onRefresh)
                        is HomeUiState.NoSubscriptions -> EmptyState(
                            title = tr(UiText.home_screen_013),
                            detail = tr(UiText.home_screen_014))
                        is HomeUiState.Content -> HomeContent(state,
                            if (onlyUnwatched) continueWatching.filter { it.id !in watched } else continueWatching,
                            onPostClick, onBlogClick, menuFocus, onRetryAuthor) { actionPost = it }
                    }
                }
            }
          }
        }
    }
    actionPost?.let { post ->
        val storedVideo = continueWatching.any { it.id == post.id } ||
            (historyState as? HistoryUiState.Content)?.entries?.any { it.postId == post.id } == true
        PostActionsDialog(post, onOpen = { onPostClick(post) }, onDismiss = { actionPost = null },
            onFromStart = if (post.isPlayable || storedVideo || ((section == HomeSection.Later || section == HomeSection.Videos) && post.hasVideo)) ({ onPostFromStart(post) }) else null,
            onAuthor = post.blogUrl.takeIf { it.isNotBlank() }?.let { url -> { onBlogClick(url) } },
            onRemoveHistory = if (section == HomeSection.History && onRemoveHistory != null)
                ({ onRemoveHistory(post) }) else null,
            onRemoved = { actionPost = null; focusMenu = true },
            onLibraryChanged = { actionPost = null; focusMenu = true })
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Content,
    continueWatching: List<Post>,
    onPostClick: (Post) -> Unit,
    onBlogClick: (String) -> Unit,
    menuFocus: FocusRequester,
    onRetryAuthor: (String) -> Unit,
    onPostLongClick: (Post) -> Unit,
) {
    val rows = state.rows.filter { it.posts.isNotEmpty() || it.loading || it.failed }
    if (rows.isEmpty() && continueWatching.isEmpty()) {
        EmptyState(title = tr(UiText.home_screen_015), detail = tr(UiText.home_screen_016))
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
                title = tr(UiText.tv_channel_preferences_001), posts = continueWatching, onPostClick = onPostClick,
                horizontalPadding = 24.dp, titleModifier = Modifier.testTag("continue_watching"),
                menuFocus = menuFocus,
                compactSingle = true,
                onPostLongClick = onPostLongClick,
            )
        }
        items(items = rows, key = { it.blog.url }) { row ->
            if (row.posts.isEmpty()) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(row.blog.title.ifBlank { row.blog.url }, style = MaterialTheme.typography.titleMedium)
                    if (row.loading) Text(tr(UiText.home_screen_017), style = MaterialTheme.typography.bodySmall)
                    else if (row.failed) TvActionButton(tr(UiText.home_screen_018), { onRetryAuthor(row.blog.url) })
                }
            } else {
            PostRow(
                title = row.blog.title.ifBlank { row.blog.url }, authorName = row.blog.ownerName,
                avatarUrl = row.blog.avatarUrl, posts = row.posts, onPostClick = onPostClick,
                onTitleClick = { onBlogClick(row.blog.url) },
                horizontalPadding = 24.dp,
                menuFocus = menuFocus,
                onPostLongClick = onPostLongClick,
            )
            if (row.failed) TvActionButton(tr(UiText.home_screen_019, row.blog.title), { onRetryAuthor(row.blog.url) }, Modifier.padding(start = 24.dp))
            }
        }
    }
}
