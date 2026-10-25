package io.github.ricox.boostyvp.core.ui.component

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.ContentBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Full data is fetched through the authenticated repository; never follows media URLs. */
val LocalPostResolver = staticCompositionLocalOf<(suspend (Post) -> Post)?> { null }

@Composable
fun PostDetailsDialog(initial: Post, onOpen: () -> Unit, onAuthor: (() -> Unit)?, onDismiss: () -> Unit) {
    val resolver = LocalPostResolver.current
    var post by remember(initial.id) { mutableStateOf(initial) }
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(resolver != null) }
    var failed by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val first = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(initial.id, attempt) {
        if (resolver != null) {
            loading = true; failed = false
            try { post = resolver(initial) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
            finally { loading = false }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(760.dp).heightIn(max = 480.dp).background(MaterialTheme.colorScheme.surface)
            .padding(24.dp).testTag("post_details"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr(UiText.post_actions_dialog_019), style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f, fill = false).verticalScroll(scroll).focusRequester(first)
                .focusable().testTag("post_details_body").onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionDown || event.key == Key.DirectionUp)) {
                        val direction = if (event.key == Key.DirectionDown) 1 else -1
                        val target = (scroll.value + direction * 120).coerceIn(0, scroll.maxValue)
                        if (target == scroll.value) false else { scope.launch { scroll.scrollTo(target) }; true }
                    } else false
                }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(post.title.ifBlank { tr(UiText.post_actions_dialog_002) }, style = MaterialTheme.typography.titleLarge)
                Text(post.blogTitle.ifBlank { post.blogUrl }, style = MaterialTheme.typography.titleMedium)
                if (post.publishedAt > 0) Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT,
                    io.github.ricox.boostyvp.localization.Language.locale).format(Date(post.publishedAt * 1000)))
                Text(when { post.isWaitingVideo -> tr(UiText.post_details_dialog_001); !post.hasAccess -> tr(UiText.post_details_dialog_002);
                    post.hasVideo -> tr(UiText.post_details_dialog_003); else -> tr(UiText.post_details_dialog_004) })
                post.videos.firstOrNull()?.let { video ->
                    if (video.duration > 0) Text(tr(UiText.post_details_dialog_005, video.duration / 60, (video.duration % 60).toString().padStart(2, '0')))
                    if (video.width != null && video.height != null) Text(tr(UiText.post_details_dialog_006, video.width, video.height))
                }
                Text(tr(UiText.post_details_dialog_007, post.commentCount))
                if (loading) Text(tr(UiText.post_details_dialog_008))
                if (failed) Text(tr(UiText.post_details_dialog_009))
                val blocks = if (post.hasAccess) post.blocks else post.teaser
                @Suppress("DEPRECATION")
                val plain = blocks.filterIsInstance<ContentBlock.Text>().joinToString("\n\n") {
                    android.text.Html.fromHtml(it.text).toString().trim()
                }.take(16_000)
                if (plain.isNotBlank()) {
                    Text(plain, style = MaterialTheme.typography.bodyLarge)
                } else if (!loading) Text(tr(UiText.post_details_dialog_010), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(if (post.hasVideo) tr(UiText.post_actions_dialog_003) else tr(UiText.post_details_dialog_011), onOpen, Modifier.weight(1f).testTag("post_details_open"), enabled = !loading && post.hasAccess && !post.isWaitingVideo)
                TvActionButton(tr(UiText.language_settings_dialog_005), onDismiss, Modifier.weight(1f).testTag("post_details_back"))
            }
            if (failed || onAuthor != null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (failed) TvActionButton(tr(UiText.post_details_dialog_012), { attempt++ }, Modifier.weight(1f))
                if (onAuthor != null) TvActionButton(tr(UiText.post_actions_dialog_006), onAuthor, Modifier.weight(1f))
            }
        }
        LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
    }
}
