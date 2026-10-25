package io.github.ricox.boostyvp.core.ui.component

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.appearance.LocalInterfaceOptions
import io.github.ricox.boostyvp.core.ui.theme.LocalAccentText
import io.github.ricox.boostyvp.core.ui.theme.Overscan
import kotlinx.coroutines.launch

/** Vertical navigation starts each row at its first video; playback restores the selected video. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PostRow(
    title: String,
    posts: List<Post>,
    onPostClick: (Post) -> Unit,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    authorName: String? = null,
    avatarUrl: String? = null,
    horizontalPadding: Dp = Overscan.Horizontal,
    titleModifier: Modifier = Modifier,
    menuFocus: FocusRequester? = null,
    compactSingle: Boolean = false,
    onPostLongClick: ((Post) -> Unit)? = null,
) {
    if (posts.isEmpty()) return
    val rowState = rememberLazyListState()
    val firstPostFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val density = LocalInterfaceOptions.current.density
    val name = authorName?.takeIf { it.isNotBlank() } ?: title
    Column(modifier.fillMaxWidth()) {
        if (onTitleClick != null) {
            val shape = RoundedCornerShape(12.dp)
            Button(
                onClick = onTitleClick,
                modifier = titleModifier.padding(horizontal = horizontalPadding).fillMaxWidth()
                    .focusProperties { if (menuFocus != null) left = menuFocus },
                contentPadding = PaddingValues(density.headingPaddingDp.dp),
                shape = ButtonDefaults.shape(shape),
                colors = ButtonDefaults.colors(containerColor = Color.Transparent, contentColor = colors.onSurface,
                    focusedContainerColor = colors.surfaceVariant, focusedContentColor = colors.onSurface),
                border = ButtonDefaults.border(focusedBorder = Border(
                    BorderStroke(2.dp, colors.onSurface), shape = shape)),
                scale = ButtonDefaults.scale(focusedScale = 1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AuthorAvatar(name, avatarUrl, size = density.avatarDp.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (title.isNotBlank() && title != name) Text(title,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.75f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(tr(UiText.catalog_index_004), style = MaterialTheme.typography.labelLarge, color = LocalAccentText.current)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                        tint = LocalAccentText.current, modifier = Modifier.size(20.dp))
                }
            }
        } else {
            Text(title, style = MaterialTheme.typography.titleMedium,
                modifier = titleModifier.padding(start = horizontalPadding + 6.dp, top = 6.dp))
        }
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(density.horizontalGapDp.dp),
            contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding,
                top = density.rowPaddingDp.dp, bottom = density.rowPaddingDp.dp),
            modifier = Modifier
                .focusProperties {
                    enter = { direction ->
                        if (direction == FocusDirection.Down || direction == FocusDirection.Up) {
                            scope.launch {
                                // The first card may have been recycled after horizontal scrolling.
                                rowState.scrollToItem(0)
                                withFrameNanos { }
                                firstPostFocus.requestFocus()
                            }
                            FocusRequester.Cancel
                        } else {
                            // Explicit focus restoration from the player keeps the selected card.
                            FocusRequester.Default
                        }
                    }
                }
                .focusGroup(),
        ) {
            itemsIndexed(items = posts, key = { _, post -> post.id }) { index, post ->
                PostCard(
                    post,
                    onClick = { onPostClick(post) },
                    onLongClick = onPostLongClick?.let { action -> { action(post) } },
                    inAuthorRow = onTitleClick != null,
                    horizontal = compactSingle && posts.size == 1,
                    modifier = if (index == 0) Modifier.focusRequester(firstPostFocus)
                        .focusProperties { if (menuFocus != null) left = menuFocus } else Modifier,
                )
            }
        }
    }
}
