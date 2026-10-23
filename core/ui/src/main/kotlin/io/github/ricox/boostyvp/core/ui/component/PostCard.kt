package io.github.ricox.boostyvp.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.appearance.LocalCardSize
import io.github.ricox.boostyvp.core.ui.appearance.LocalInterfaceOptions
import io.github.ricox.boostyvp.core.ui.appearance.CardStyle
import io.github.ricox.boostyvp.core.ui.appearance.FocusColor
import androidx.compose.ui.semantics.Role
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PostCard(post: Post, onClick: () -> Unit, modifier: Modifier = Modifier, inAuthorRow: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val size = LocalCardSize.current
    val options = LocalInterfaceOptions.current
    val textSize = options.textSize
    val compact = size.widthDp < 216
    val metadataStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = textSize.metadataSp.sp, lineHeight = (textSize.metadataSp + 4).sp)
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(if (focused && options.focusZoom) 1.02f else 1f,
        animationSpec = if (options.animateFocus) tween(120) else snap(), label = "cardFocus")
    val light = options.cardStyle == CardStyle.Light
    val frame = if (options.focusColor == FocusColor.Accent) colors.primary else colors.onSurface
    val date = remember(post.publishedAt) {
        if (post.publishedAt > 0) SimpleDateFormat("d MMM", Locale.forLanguageTag("ru"))
            .format(Date(post.publishedAt * 1000L)) else ""
    }
    // Own the focus transition so "no animation" really snaps instead of retaining
    // the TV Card's internal scale animation. Keep one stable clickable focus node.
    Column(modifier.width(size.widthDp.dp).onFocusChanged { focused = it.isFocused }
        .scale(zoom).clip(shape)
        .background(if (light) Color.Transparent else if (focused) colors.surfaceVariant else colors.surface)
        .border(if (focused) options.focusWidth.dp else 1.dp,
            if (focused) frame else if (light) Color.Transparent else colors.border.copy(alpha = 0.4f), shape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            role = Role.Button, onClick = onClick)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .background(Brush.linearGradient(listOf(colors.surfaceVariant, colors.background)))) {
                if (post.hasVideo) Icon(Icons.Default.PlayArrow, null, tint = colors.primary.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center).size(if (compact) 28.dp else 44.dp))
                else Text("Публикация", color = colors.onSurfaceVariant,
                    style = metadataStyle, modifier = Modifier.align(Alignment.Center))
                if (post.thumbnailUrl != null) AsyncImage(
                    model = post.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))))
                when {
                    !post.hasAccess -> Badge(Icons.Default.Lock, "По подписке")
                    post.isWaitingVideo -> Badge(null, "Обрабатывается")
                    post.primaryVideo != null && options.showDuration -> Badge(null, post.primaryVideo!!.duration.formatDuration())
                    !post.hasVideo -> Badge(null, "Текст")
                }
                val watched = LocalWatchProgress.current[post.id]
                if (watched != null && watched > 0f) {
                    WatchProgressBar(watched, Modifier.align(Alignment.BottomStart))
                }
                if (post.isPinned) Text("Закреплено", style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopStart).padding(if (compact) 4.dp else 10.dp)
                        .background(colors.primary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Column(Modifier.padding((size.paddingDp + options.density.cardPaddingDeltaDp).dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                Text(post.title.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = textSize.titleSp.sp, lineHeight = (textSize.titleSp + 6).sp),
                    minLines = options.titleLines, maxLines = options.titleLines, overflow = TextOverflow.Ellipsis)
                val showAuthor = options.showsAuthor(inAuthorRow)
                if (showAuthor || (options.showDate && date.isNotEmpty())) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
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
    if (focused && options.fullTitle) FocusedTitle(post.title.ifBlank { "Без названия" })
}

@Composable
private fun BoxScope.Badge(icon: ImageVector?, label: String) {
    if (label.isBlank()) return
    val compact = LocalCardSize.current.widthDp < 216
    val textSize = LocalInterfaceOptions.current.textSize
    Row(Modifier.align(Alignment.BottomEnd).padding(if (compact) 4.dp else 10.dp)
        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
        .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = if (compact) 2.dp else 4.dp)
        .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (compact) 10.dp else 14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(
            fontSize = textSize.metadataSp.sp, lineHeight = (textSize.metadataSp + 2).sp),
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
