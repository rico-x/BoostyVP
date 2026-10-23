package io.github.ricox.boostyvp.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import io.github.ricox.boostyvp.core.model.Post
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PostCard(post: Post, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val date = remember(post.publishedAt) {
        if (post.publishedAt > 0) SimpleDateFormat("d MMM", Locale.forLanguageTag("ru"))
            .format(Date(post.publishedAt * 1000L)) else ""
    }
    Card(
        onClick = onClick, modifier = modifier.width(280.dp),
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(containerColor = colors.surface, contentColor = colors.onSurface,
            focusedContainerColor = colors.surfaceVariant, focusedContentColor = colors.onSurface),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, colors.border.copy(alpha = 0.4f)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, colors.onSurface), inset = 3.dp, shape = shape),
        ),
        scale = CardDefaults.scale(focusedScale = 1.035f, pressedScale = 1f),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .background(Brush.linearGradient(listOf(colors.surfaceVariant, colors.background)))) {
                Icon(Icons.Default.PlayArrow, null, tint = colors.primary.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center).size(44.dp))
                if (post.thumbnailUrl != null) AsyncImage(
                    model = post.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))))
                when {
                    !post.hasAccess -> Badge(Icons.Default.Lock, "По подписке")
                    post.isWaitingVideo -> Badge(null, "Обрабатывается")
                    post.primaryVideo != null -> Badge(null, post.primaryVideo!!.duration.formatDuration())
                }
                if (post.isPinned) Text("Закреплено", style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimary,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                        .background(colors.primary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(post.title.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp, lineHeight = 24.sp),
                    minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(post.blogTitle, style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    if (date.isNotEmpty()) Text(date, style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.Badge(icon: ImageVector?, label: String) {
    if (label.isBlank()) return
    Row(Modifier.align(Alignment.BottomEnd).padding(10.dp)
        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
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
