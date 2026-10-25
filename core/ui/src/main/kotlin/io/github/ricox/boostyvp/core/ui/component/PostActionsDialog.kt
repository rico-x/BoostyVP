package io.github.ricox.boostyvp.core.ui.component

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.SavedPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** App navigation keeps the catalog alive while the post's comments are read. */
val LocalPostComments = staticCompositionLocalOf<((Post) -> Unit)?> { null }

/** Only real actions; no future/library placeholders. Short OK on the card is unchanged. */
@Composable
fun PostActionsDialog(
    post: Post,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onFromStart: (() -> Unit)? = null,
    onAuthor: (() -> Unit)? = null,
    onRemoveHistory: (suspend () -> Boolean)? = null,
    onRemoved: () -> Unit = onDismiss,
    onLibraryChanged: () -> Unit = onDismiss,
) {
    val controls = LocalLibraryControls.current
    val catalog = LocalCatalogControls.current
    val openComments = LocalPostComments.current
    val opener = remember(post.id) { OpeningOkGuard() }
    var readingTitle by remember(post.id) { mutableStateOf(false) }
    var showingDetails by remember(post.id) { mutableStateOf(false) }
    var removing by remember(post.id) { mutableStateOf(false) }
    var error by remember(post.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val remove = remember { FocusRequester() }
    fun mutate(action: suspend () -> Unit) {
        removing = true
        error = null
        scope.launch {
            try {
                action()
                onLibraryChanged()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = tr(UiText.post_actions_dialog_001) }
            finally { removing = false }
        }
    }
    if (readingTitle) {
        FullTitleDialog(post.title.ifBlank { tr(UiText.post_actions_dialog_002) }) { readingTitle = false }
        return
    }
    if (showingDetails) {
        PostDetailsDialog(post, onOpen = { onDismiss(); onOpen() },
            onAuthor = onAuthor?.let { action -> { onDismiss(); action() } },
            onDismiss = { showingDetails = false })
        return
    }
    Dialog(onDismissRequest = { if (!removing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(Modifier.width(540.dp).heightIn(max = 480.dp).testTag("post_actions")
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .verticalScroll(rememberScrollState()).padding(24.dp)
            .onPreviewKeyEvent {
                val native = it.nativeKeyEvent
                val ok = it.key == Key.DirectionCenter || it.key == Key.Enter ||
                    native.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                ok && opener.consume(it.type == KeyEventType.KeyDown, native.repeatCount)
            }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(post.title.ifBlank { tr(UiText.post_actions_dialog_002) }, style = MaterialTheme.typography.titleLarge,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(post.blogTitle.ifBlank { post.blogUrl }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TvActionButton(if (post.hasVideo) tr(UiText.post_actions_dialog_003) else tr(UiText.post_actions_dialog_004), {
                onDismiss(); onOpen()
            }, Modifier.fillMaxWidth().focusRequester(first).testTag("post_action_open"), enabled = !removing)
            if (onFromStart != null) TvActionButton(tr(UiText.post_actions_dialog_005), { onDismiss(); onFromStart() },
                Modifier.fillMaxWidth().testTag("post_action_restart"), enabled = !removing)
            if (post.hasVideo && openComments != null) TvActionButton(tr(UiText.video_comments), {
                onDismiss(); openComments(post)
            }, Modifier.fillMaxWidth().testTag("post_action_comments"), enabled = !removing)
            if (onAuthor != null) TvActionButton(tr(UiText.post_actions_dialog_006), { onDismiss(); onAuthor() },
                Modifier.fillMaxWidth().testTag("post_action_author"), enabled = !removing)
            if (catalog.ready && post.hasVideo && post.hasAccess && !post.isWaitingVideo) {
                val queued = catalog.queue.any { it.postId == post.id }
                val full = catalog.queue.size >= 100 && !queued
                TvActionButton(if (queued) tr(UiText.post_actions_dialog_007) else if (full) tr(UiText.post_actions_dialog_008) else tr(UiText.post_actions_dialog_009), {
                    if (queued) catalog.remove(post.id) else catalog.enqueue(SavedPost.from(post))
                    onLibraryChanged()
                }, Modifier.fillMaxWidth().testTag("post_action_queue"), enabled = !removing && !full)
            }
            if (controls.ready) {
                if (post.hasVideo) controls.setFavoritePost?.let { set ->
                    val favorite = controls.library.favorites.any { it.postId == post.id }
                    TvActionButton(if (favorite) tr(UiText.post_actions_dialog_010) else tr(UiText.post_actions_dialog_011),
                        { mutate { set(SavedPost.from(post), !favorite) } },
                        Modifier.fillMaxWidth().testTag("post_action_favorite_video"), enabled = !removing)
                }
                controls.setLater?.let { set ->
                    val saved = controls.library.later.any { it.postId == post.id }
                    TvActionButton(if (saved) tr(UiText.post_actions_dialog_012) else tr(UiText.library_privacy_dialog_005),
                        { mutate { set(SavedPost.from(post), !saved) } },
                        Modifier.fillMaxWidth().testTag("post_action_later"), enabled = !removing)
                }
                if (post.blogUrl.isNotBlank()) controls.setAuthor?.let { set ->
                    val favorite = controls.library.authors.any { it.blogUrl == post.blogUrl }
                    TvActionButton(if (favorite) tr(UiText.post_actions_dialog_013) else tr(UiText.post_actions_dialog_014),
                        { mutate { set(post.blogUrl, post.blogTitle, !favorite) } },
                        Modifier.fillMaxWidth().testTag("post_action_favorite"), enabled = !removing)
                }
                controls.setWatched?.let { set ->
                    val watched = post.id in controls.library.watched
                    TvActionButton(if (watched) tr(UiText.post_actions_dialog_015) else tr(UiText.post_actions_dialog_016),
                        { mutate { set(post.id, !watched) } },
                        Modifier.fillMaxWidth().testTag("post_action_watched"), enabled = !removing)
                }
            } else if (controls.failed) {
                TvActionButton(tr(UiText.post_actions_dialog_017), controls.retry, Modifier.fillMaxWidth())
            }
            TvActionButton(tr(UiText.post_actions_dialog_018), { readingTitle = true },
                Modifier.fillMaxWidth().testTag("post_action_title"), enabled = !removing)
            TvActionButton(tr(UiText.post_actions_dialog_019), { showingDetails = true },
                Modifier.fillMaxWidth().testTag("post_action_details"), enabled = !removing)
            if (onRemoveHistory != null) TvActionButton(if (removing) tr(UiText.post_actions_dialog_020) else tr(UiText.post_actions_dialog_021), {
                removing = true
                error = null
                scope.launch {
                    val success = try { onRemoveHistory() }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { false }
                    removing = false
                    if (success) onRemoved() else error = tr(UiText.post_actions_dialog_022)
                }
            }, Modifier.fillMaxWidth().focusRequester(remove).testTag("post_action_remove"), enabled = !removing)
            if (onRemoveHistory != null) Text(tr(UiText.post_actions_dialog_023),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("post_action_error")) }
            TvActionButton(tr(UiText.library_privacy_dialog_012), onDismiss, Modifier.fillMaxWidth().testTag("post_action_cancel"), enabled = !removing)
        }
        LaunchedEffect(removing) {
            if (!removing) { withFrameNanos { }; if (error != null && onRemoveHistory != null) remove.requestFocus() else first.requestFocus() }
        }
    }
}
