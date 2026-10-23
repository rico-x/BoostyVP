package io.github.ricox.boostyvp.core.ui.component

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
) {
    var readingTitle by remember(post.id) { mutableStateOf(false) }
    var removing by remember(post.id) { mutableStateOf(false) }
    var error by remember(post.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val remove = remember { FocusRequester() }
    if (readingTitle) {
        FullTitleDialog(post.title.ifBlank { "Без названия" }) { readingTitle = false }
        return
    }
    Dialog(onDismissRequest = { if (!removing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(540.dp).heightIn(max = 480.dp).testTag("post_actions")
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .verticalScroll(rememberScrollState()).padding(24.dp)
            .onPreviewKeyEvent {
                // The held OK that opened the menu must not activate its first action.
                it.nativeKeyEvent.repeatCount > 0 && (it.key == Key.DirectionCenter || it.key == Key.Enter ||
                    it.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)
            }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(post.title.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleLarge,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(post.blogTitle.ifBlank { post.blogUrl }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TvActionButton(if (post.hasVideo) "Открыть видео" else "Открыть публикацию", {
                onDismiss(); onOpen()
            }, Modifier.fillMaxWidth().focusRequester(first).testTag("post_action_open"), enabled = !removing)
            if (onFromStart != null) TvActionButton("Смотреть сначала", { onDismiss(); onFromStart() },
                Modifier.fillMaxWidth().testTag("post_action_restart"), enabled = !removing)
            if (onAuthor != null) TvActionButton("Открыть автора", { onDismiss(); onAuthor() },
                Modifier.fillMaxWidth().testTag("post_action_author"), enabled = !removing)
            TvActionButton("Полное название", { readingTitle = true },
                Modifier.fillMaxWidth().testTag("post_action_title"), enabled = !removing)
            if (onRemoveHistory != null) TvActionButton(if (removing) "Удаление…" else "Убрать из истории", {
                removing = true
                error = null
                scope.launch {
                    val success = try { onRemoveHistory() }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { false }
                    removing = false
                    if (success) onRemoved() else error = "Не удалось убрать запись. Попробуйте ещё раз."
                }
            }, Modifier.fillMaxWidth().focusRequester(remove).testTag("post_action_remove"), enabled = !removing)
            if (onRemoveHistory != null) Text("Удаляется только запись истории. Позиция продолжения сохраняется.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("post_action_error")) }
            TvActionButton("Отмена", onDismiss, Modifier.fillMaxWidth().testTag("post_action_cancel"), enabled = !removing)
        }
        LaunchedEffect(removing) {
            if (!removing) { withFrameNanos { }; if (error != null) remove.requestFocus() else first.requestFocus() }
        }
    }
}
