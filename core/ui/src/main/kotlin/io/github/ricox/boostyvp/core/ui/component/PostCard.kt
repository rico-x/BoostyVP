package io.github.ricox.boostyvp.core.ui.component

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.appearance.LocalCardSize
import io.github.ricox.boostyvp.core.ui.appearance.LocalInterfaceOptions
import io.github.ricox.boostyvp.core.ui.appearance.CardStyle
import io.github.ricox.boostyvp.core.ui.appearance.FocusColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import io.github.ricox.boostyvp.core.ui.theme.LocalAccentText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun PostCard(post: Post, onClick: () -> Unit, modifier: Modifier = Modifier, inAuthorRow: Boolean = false,
    previewFocused: Boolean = false, horizontal: Boolean = false, onLongClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val size = LocalCardSize.current
    val options = LocalInterfaceOptions.current
    val textSize = options.textSize
    val compact = size.widthDp < 216
    val metadataStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = options.metadataSp.sp, lineHeight = (options.metadataSp + 4).sp)
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    var titleOverflow by remember(post.id, post.title) { mutableStateOf(false) }
    var titleReader by remember(post.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    var heldKey by remember(post.id) { mutableStateOf<Int?>(null) }
    var longPressDelivered by remember(post.id) { mutableStateOf(false) }
    var longPressJob by remember(post.id) { mutableStateOf<Job?>(null) }
    DisposableEffect(post.id) {
        onDispose {
            longPressJob?.cancel()
            longPressJob = null
            heldKey = null
        }
    }
    val highlighted = focused || previewFocused
    val zoom by animateFloatAsState(options.focusScale(highlighted),
        animationSpec = if (options.animateFocus) tween(options.focusAnimationMillis) else snap(), label = "cardFocus")
    val light = options.cardStyle == CardStyle.Light
    val frame = if (options.focusColor == FocusColor.Accent) LocalAccentText.current else colors.onSurface
    val focusFrame by animateColorAsState(if (highlighted) frame else if (light) Color.Transparent else colors.border.copy(alpha = 0.4f),
        animationSpec = if (options.animateFocus) tween(options.focusAnimationMillis) else snap(), label = "cardFocusFrame")
    val measurer = rememberTextMeasurer()
    val date = remember(post.publishedAt, io.github.ricox.boostyvp.localization.Language.code) {
        if (post.publishedAt > 0) SimpleDateFormat("d MMM", io.github.ricox.boostyvp.localization.Language.locale)
            .format(Date(post.publishedAt * 1000L)) else ""
    }
    // Own the focus transition so "no animation" really snaps instead of retaining
    // the TV Card's internal scale animation. Keep one stable clickable focus node.
    Column(modifier.width(if (horizontal) 380.dp else size.widthDp.dp).onFocusChanged {
        focused = it.isFocused
        if (!it.isFocused) {
            longPressJob?.cancel()
            longPressJob = null
            heldKey = null
        }
    }
        .onPreviewKeyEvent {
            val native = it.nativeKeyEvent
            val ok = native.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                native.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                native.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
            if (ok && onLongClick != null) {
                // combinedClickable handles touch/accessibility, but not held remote OK
                // on every TV. Own both halves so release cannot also start playback.
                if (it.type == KeyEventType.KeyDown) {
                    if (native.repeatCount == 0 && heldKey == null) {
                        heldKey = native.keyCode
                        longPressDelivered = false
                        longPressJob = scope.launch {
                            delay(longPressTimeout)
                            if (heldKey == native.keyCode && !longPressDelivered) {
                                longPressDelivered = true
                                currentLongClick?.invoke()
                            }
                        }
                    } else if (heldKey == native.keyCode && native.isLongPress && !longPressDelivered) {
                        longPressJob?.cancel()
                        longPressDelivered = true
                        currentLongClick?.invoke()
                    }
                } else if (it.type == KeyEventType.KeyUp && heldKey == native.keyCode) {
                    longPressJob?.cancel()
                    heldKey = null
                    if (!longPressDelivered && !native.isCanceled) currentClick()
                }
                true
            } else if (native.keyCode == android.view.KeyEvent.KEYCODE_MENU && onLongClick != null) {
                if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) onLongClick()
                true
            } else if (it.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_INFO && options.fullTitle && titleOverflow) {
                if (it.type == KeyEventType.KeyDown) titleReader = true
                true
            } else false
        }
        .scale(zoom).clip(shape)
        .background(if (light) Color.Transparent else if (highlighted) colors.surfaceVariant else colors.surface)
        .border(if (highlighted) options.focusWidth.dp else 1.dp,
            focusFrame, shape)
        .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            role = Role.Button, onLongClickLabel = tr(UiText.post_card_001), onLongClick = onLongClick, onClick = onClick)) {
        @Composable fun Thumbnail(thumbnailModifier: Modifier) {
            Box(thumbnailModifier.aspectRatio(16f / 9f).clip(if (light || horizontal) shape else RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(Brush.linearGradient(listOf(colors.surfaceVariant, colors.background)))) {
                if (post.hasVideo) Icon(Icons.Default.PlayArrow, null, tint = colors.primary.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center).size(if (compact) 28.dp else 44.dp))
                else Text(tr(UiText.post_card_002), color = colors.onSurfaceVariant,
                    style = metadataStyle, modifier = Modifier.align(Alignment.Center))
                if (post.thumbnailUrl != null) AsyncImage(
                    model = post.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))))
                when {
                    !post.hasAccess -> Badge(Icons.Default.Lock, tr(UiText.post_card_003))
                    post.isWaitingVideo -> Badge(null, tr(UiText.post_card_004))
                    post.primaryVideo != null && options.showDuration -> Badge(null, post.primaryVideo!!.duration.formatDuration())
                    !post.hasVideo -> Badge(null, tr(UiText.post_card_005))
                }
                val watched = LocalWatchProgress.current[post.id]
                if (watched != null && watched > 0f) {
                    WatchProgressBar(watched, Modifier.align(Alignment.BottomStart))
                }
                if (post.isPinned) Text(tr(UiText.post_card_006), style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopStart).padding(if (compact) 4.dp else 10.dp)
                        .background(colors.primary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp))
                if (post.id in LocalLibraryControls.current.library.watched) Text(tr(UiText.post_card_007),
                    style = MaterialTheme.typography.labelSmall, color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .semantics { contentDescription = tr(UiText.post_card_007) })
            }
        }
        @Composable fun Caption(captionModifier: Modifier) {
            Column(captionModifier.padding((size.paddingDp + options.density.cardPaddingDeltaDp).dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                Text(post.title.ifBlank { tr(UiText.post_actions_dialog_002) }, style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = textSize.titleSp.sp, lineHeight = (textSize.titleSp + 6).sp),
                    minLines = options.titleLines, maxLines = options.titleLines, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { titleOverflow = it.hasVisualOverflow })
                val showAuthor = options.showsAuthor(inAuthorRow)
                if (showAuthor || (options.showDate && date.isNotEmpty())) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                      val gap = if (compact) 4.dp else 8.dp
                      val authorWidth = measurer.measure(post.blogTitle, metadataStyle, maxLines = 1).size.width
                      val dateWidth = measurer.measure(date, metadataStyle, maxLines = 1).size.width
                      val stack = showAuthor && options.showDate && date.isNotEmpty() &&
                          authorWidth + dateWidth + with(androidx.compose.ui.platform.LocalDensity.current) { gap.toPx() } > constraints.maxWidth
                      if (stack) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                          Text(post.blogTitle, style = metadataStyle, color = colors.onSurfaceVariant,
                              maxLines = 1, overflow = TextOverflow.Ellipsis)
                          Text(date, style = metadataStyle, color = colors.onSurfaceVariant, maxLines = 1)
                      } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (showAuthor) Text(post.blogTitle, style = metadataStyle,
                            color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        if (options.showDate && date.isNotEmpty()) Text(date, style = metadataStyle,
                            color = colors.onSurfaceVariant)
                    }
                    }
                }
            }
        }
        if (horizontal) Row(verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(Modifier.width(124.dp).padding(8.dp))
            Caption(Modifier.weight(1f))
        } else Column {
            Thumbnail(Modifier.fillMaxWidth())
            Caption(Modifier.fillMaxWidth())
        }
    }
    if (focused && titleOverflow && options.fullTitle && !previewFocused) FocusedTitle(post.title.ifBlank { tr(UiText.post_actions_dialog_002) })
    if (titleReader) FullTitleDialog(post.title) { titleReader = false }
}

@Composable
private fun BoxScope.Badge(icon: ImageVector?, label: String) {
    if (label.isBlank()) return
    val compact = LocalCardSize.current.widthDp < 216
    val metadataSp = LocalInterfaceOptions.current.metadataSp
    Row(Modifier.align(Alignment.BottomEnd).padding(if (compact) 4.dp else 10.dp)
        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
        .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = if (compact) 2.dp else 4.dp)
        .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (compact) 10.dp else 14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(
            fontSize = metadataSp.sp, lineHeight = (metadataSp + 2).sp),
            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Seconds to h:mm:ss / m:ss. */
fun Int.formatDuration(): String {
    if (this <= 0) return ""
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
