package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.PostComment
import io.github.ricox.boostyvp.core.ui.component.BackButton
import io.github.ricox.boostyvp.core.ui.component.ScreenBackdrop
import io.github.ricox.boostyvp.core.ui.component.TvActionButton
import io.github.ricox.boostyvp.core.ui.theme.Overscan
import io.github.ricox.boostyvp.core.ui.theme.LocalAccentText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PostReaderScreen(post: Post, onClose: () -> Unit, viewModel: PostCommentsViewModel = hiltViewModel(),
    commentsOnly: Boolean = false) {
    LaunchedEffect(post, commentsOnly) { viewModel.bind(post, initiallyExpanded = commentsOnly) }
    val comments by viewModel.state.collectAsStateWithLifecycle()
    PostReaderLayout(post, comments, viewModel::toggle, viewModel::loadMore, viewModel::toggleReplies, onClose,
        commentsOnly = commentsOnly)
}

/** A persistent reading focus scrolls long paragraphs as well as short ones.
 * Right/OK enters visible reply actions; Left returns to reading. No WebView,
 * embedded scripts or automatically opened links are used for post content.
 */
@Composable
fun PostReaderLayout(
    post: Post,
    comments: PostCommentsState,
    onToggleComments: () -> Unit,
    onLoadMore: (Long?) -> Unit,
    onToggleReplies: (Long) -> Unit,
    onClose: () -> Unit,
    list: LazyListState = rememberLazyListState(),
    commentsOnly: Boolean = false,
) {
    val parts = remember(post, commentsOnly) {
        if (commentsOnly) emptyList() else readerParts(if (post.hasAccess) post.blocks else post.teaser)
    }
    val showComments = comments.expanded && post.hasAccess && !post.commentsDenied
    val scope = rememberCoroutineScope()
    val reading = remember { FocusRequester() }
    val back = remember { FocusRequester() }
    val toggle = remember { FocusRequester() }
    val replyFocus = remember { mutableMapOf<String, FocusRequester>() }
    var readingFocused by remember { mutableStateOf(false) }
    var articlePosition by remember { mutableStateOf(0 to 0) }
    // Title, optional lock notice, parts/empty body, comments anchor.
    val anchor = if (commentsOnly) 1 else 1 + (if (post.hasAccess) 0 else 1) + parts.size.coerceAtLeast(1)
    LaunchedEffect(post.id) { reading.requestFocus() }
    LaunchedEffect(showComments) {
        if (showComments) {
            articlePosition = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
            list.scrollToItem(if (commentsOnly) 0 else anchor)
        } else {
            list.scrollToItem(articlePosition.first, articlePosition.second)
        }
        reading.requestFocus()
    }
    ScreenBackdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = Overscan.Horizontal, vertical = Overscan.Vertical),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BackButton(onClose, Modifier.focusRequester(back).focusProperties { down = reading })
                Text(tr(UiText.post_reader_screen_001),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(top = 12.dp))
                val count = if (post.commentCount > 0) " (${post.commentCount})" else ""
                TvActionButton(tr(UiText.post_reader_screen_004, count, if (showComments) tr(UiText.post_reader_screen_002) else tr(UiText.post_reader_screen_003)),
                    onToggleComments,
                    Modifier.testTag("comments_toggle").focusRequester(toggle).focusProperties { down = reading },
                    enabled = post.hasAccess && !post.commentsDenied)
            }
            LazyColumn(
                state = list, contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth()
                    .align(Alignment.CenterHorizontally).testTag("post_reader")
                    .focusRequester(reading).onFocusChanged { readingFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (!readingFocused) false else when (event.key) {
                            Key.DirectionDown, Key.DirectionUp, Key.PageDown, Key.PageUp -> {
                                if (event.type == KeyEventType.KeyDown) {
                                    val down = event.key == Key.DirectionDown || event.key == Key.PageDown
                                    val page = event.key == Key.PageDown || event.key == Key.PageUp
                                    if (!down && !list.canScrollBackward) back.requestFocus()
                                    else scope.launch {
                                        val viewport = list.layoutInfo.viewportEndOffset - list.layoutInfo.viewportStartOffset
                                        list.scrollBy((if (page) viewport * 0.85f else viewport * 0.22f) * if (down) 1 else -1)
                                    }
                                }
                                true
                            }
                            Key.DirectionLeft -> {
                                if (event.type == KeyEventType.KeyDown) back.requestFocus()
                                true
                            }
                            Key.DirectionRight, Key.DirectionCenter, Key.Enter -> {
                                if (event.type == KeyEventType.KeyDown) {
                                    val action = list.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { replyFocus[it.key.toString()] }
                                    (action ?: if (post.hasAccess && !post.commentsDenied) toggle else back).requestFocus()
                                }
                                true
                            }
                            else -> false
                        }
                    }.focusable(),
            ) {
                item(key = "post_title") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(post.title.ifBlank { tr(UiText.post_card_002) }, style = MaterialTheme.typography.headlineSmall)
                        Text(listOf(post.blogTitle.ifBlank { post.blogUrl }, readerDate(post.publishedAt))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!commentsOnly && !post.hasAccess) item(key = "locked_preview") {
                    ReaderText(tr(UiText.post_reader_screen_005))
                }
                if (!commentsOnly && parts.isEmpty()) item(key = "empty_body") { ReaderText(tr(UiText.post_reader_screen_006)) }
                itemsIndexed(parts, key = { index, _ -> "body_$index" }) { _, part -> ReaderPartView(part) }
                item(key = "comments_anchor") {
                    Text(when {
                        post.commentsDenied -> tr(UiText.post_reader_screen_007)
                        !post.hasAccess -> tr(UiText.post_reader_screen_008)
                        showComments -> tr(UiText.post_reader_screen_009)
                        else -> tr(UiText.post_reader_screen_010)
                    }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showComments) {
                    if (comments.root.loaded && comments.root.items.isEmpty() && comments.root.nextCursor == null)
                        item(key = "no_comments") { ReaderText(tr(UiText.post_reader_screen_011)) }
                    for (comment in comments.root.items) {
                        item(key = "comment_${comment.id}") { CommentBody(comment) }
                        if (comment.replyCount > 0) {
                            val key = "replies_${comment.id}"
                            item(key = key) {
                                val requester = remember { FocusRequester() }.also { replyFocus[key] = it }
                                DisposableEffect(key, requester) { onDispose {
                                    if (replyFocus[key] === requester) replyFocus.remove(key)
                                } }
                                TvActionButton(tr(UiText.post_reader_screen_012, comment.replyCount, if (comment.intId in comments.expandedReplies) tr(UiText.post_reader_screen_002) else tr(UiText.post_reader_screen_003)),
                                    { onToggleReplies(comment.intId) }, Modifier.testTag(key)
                                        .focusRequester(requester).focusProperties { left = reading })
                            }
                            if (comment.intId in comments.expandedReplies) {
                                val replies = comments.replies[comment.intId] ?: CommentListState()
                                for (reply in replies.items) item(key = "reply_${comment.id}_${reply.id}") {
                                    Column(Modifier.padding(start = 24.dp)) { CommentBody(reply) }
                                }
                                item(key = "reply_more_${comment.id}") {
                                    CommentLoadAction(replies, { onLoadMore(comment.intId) }, reading,
                                        "reply_more_${comment.id}", replyFocus)
                                }
                            }
                        }
                    }
                    item(key = "comments_more") { CommentLoadAction(comments.root, { onLoadMore(null) },
                        reading, "comments_more", replyFocus) }
                }
            }
        }
    }
}

@Composable
private fun CommentLoadAction(state: CommentListState, onLoad: () -> Unit, reading: FocusRequester,
    key: String, focus: MutableMap<String, FocusRequester>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.loading) ReaderText(tr(UiText.post_reader_screen_013))
        state.error?.let { ReaderText(it) }
        if (!state.loading && state.canLoadMore) {
            val requester = remember { FocusRequester() }.also { focus[key] = it }
            DisposableEffect(key, requester) { onDispose {
                if (focus[key] === requester) focus.remove(key)
            } }
            TvActionButton(if (state.error != null) tr(UiText.post_details_dialog_012) else tr(UiText.post_reader_screen_014), {
                // The button disappears while loading; keep remote input alive.
                reading.requestFocus()
                onLoad()
            },
                Modifier.testTag(key).focusRequester(requester).focusProperties { left = reading })
        } else focus.remove(key)
    }
}

@Composable
private fun CommentBody(comment: PostComment) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${comment.authorName.ifBlank { tr(UiText.post_reader_screen_015) }} · ${readerDate(comment.createdAt)}",
            style = MaterialTheme.typography.titleMedium, color = LocalAccentText.current)
        val parts = remember(comment) { readerParts(comment.blocks) }
        if (parts.isEmpty()) ReaderText(tr(UiText.post_reader_screen_016))
        for (part in parts) ReaderPartView(part)
    }
}

@Composable
private fun ReaderPartView(part: ReaderPart) {
    when (part) {
        is ReaderPart.Paragraph -> ReaderText(part.text, part.heading)
        is ReaderPart.Image -> BoxWithConstraints(Modifier.fillMaxWidth().testTag("reader_image"),
            contentAlignment = Alignment.Center) {
            // Compute compatible bounds before measuring: fillMaxWidth + heightIn +
            // aspectRatio can force an oversized child to draw across adjacent paragraphs.
            val imageWidth = minOf(maxWidth, 280.dp * part.ratio)
            AsyncImage(part.url, tr(UiText.post_reader_screen_017), contentScale = ContentScale.Fit,
                modifier = Modifier.size(imageWidth, imageWidth / part.ratio).testTag("reader_image_content"))
        }
    }
}

@Composable
private fun ReaderText(text: String, heading: Boolean = false) {
    Text(text, style = if (heading) MaterialTheme.typography.titleLarge
        else MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 29.sp))
}

private fun readerDate(seconds: Long): String = if (seconds > 0)
    SimpleDateFormat("d MMM yyyy", io.github.ricox.boostyvp.localization.Language.locale).format(Date(seconds * 1000)) else ""
