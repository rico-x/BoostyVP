package io.github.ricox.boostyvp.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.component.BoostyVPBrand
import io.github.ricox.boostyvp.core.ui.component.EmptyState
import io.github.ricox.boostyvp.core.ui.component.ErrorState
import io.github.ricox.boostyvp.core.ui.component.LoadingState
import io.github.ricox.boostyvp.core.ui.component.PostRow
import io.github.ricox.boostyvp.core.ui.component.ScreenBackdrop
import io.github.ricox.boostyvp.core.ui.component.ScreenHeading
import io.github.ricox.boostyvp.core.ui.component.SignOutDialog
import io.github.ricox.boostyvp.core.ui.component.StatusChip
import io.github.ricox.boostyvp.core.ui.component.TvActionButton
import io.github.ricox.boostyvp.core.ui.theme.Overscan

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
    HomeLayout(state, onPostClick, onBlogClick, { showSignOut = true }, viewModel::refresh)
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
) {
    ScreenBackdrop {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = Overscan.Horizontal, end = Overscan.Horizontal,
                    top = Overscan.Vertical, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    BoostyVPBrand()
                    StatusChip("Мои подписки")
                }
                TvActionButton("Выйти", onSignOut, icon = Icons.AutoMirrored.Filled.ExitToApp)
            }
            Box(Modifier.weight(1f)) {
                when (state) {
                    is HomeUiState.Loading -> LoadingState()
                    is HomeUiState.Error -> ErrorState(detail = state.message, onRetry = onRefresh)
                    is HomeUiState.NoSubscriptions -> EmptyState(
                        title = "Здесь будут ваши авторы",
                        detail = "Подпишитесь на авторов на boosty.to — их видео появятся на вашем ТВ.",
                    )
                    is HomeUiState.Content -> HomeContent(state, onPostClick, onBlogClick)
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
) {
    if (state.rows.isEmpty()) {
        EmptyState(title = "Видео ещё впереди", detail = "Новые видеопубликации ваших авторов появятся здесь.")
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState, modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = Overscan.Vertical),
    ) {
        item(key = "feed_heading") {
            ScreenHeading("Ваша лента", "Новые истории от любимых авторов.",
                Modifier.padding(horizontal = Overscan.Horizontal))
        }
        items(items = state.rows, key = { it.blog.url }) { row ->
            PostRow(
                title = row.blog.title.ifBlank { row.blog.url }, authorName = row.blog.ownerName,
                avatarUrl = row.blog.avatarUrl, posts = row.posts, onPostClick = onPostClick,
                onTitleClick = { onBlogClick(row.blog.url) },
            )
        }
    }
}
