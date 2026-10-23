package io.github.ricox.boostyvp.feature.blog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.ui.component.AuthorAvatar
import io.github.ricox.boostyvp.core.ui.component.BackButton
import io.github.ricox.boostyvp.core.ui.component.StatusChip
import java.text.NumberFormat

@Composable
internal fun BlogHeader(blog: Blog?, blogUrl: String, onBack: () -> Unit) {
    val name = blog?.ownerName?.takeIf { it.isNotBlank() }
        ?: blog?.title?.takeIf { it.isNotBlank() } ?: blogUrl
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Box(Modifier.fillMaxWidth().clip(shape).border(1.dp, colors.border.copy(alpha = 0.5f), shape)) {
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            listOf(colors.surfaceVariant, colors.surface, colors.primary.copy(alpha = 0.12f))))) {
            if (blog?.coverUrl != null) AsyncImage(blog.coverUrl, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
                colors.background.copy(alpha = 0.9f), colors.background.copy(alpha = 0.65f))))
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.8f)))))
        }
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBack)
                if (blog?.isSubscribed == true) StatusChip("Вы подписаны", icon = Icons.Default.Check)
            }
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                AuthorAvatar(name, blog?.avatarUrl, size = 88.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, style = MaterialTheme.typography.headlineLarge, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                    Text("@$blogUrl", style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                    val title = blog?.title.orEmpty()
                    if (title.isNotBlank() && title != name) Text(title,
                        style = MaterialTheme.typography.bodyLarge, color = colors.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (blog != null && blog.postCount > 0) Text(
                        "${NumberFormat.getIntegerInstance().format(blog.postCount)} публикаций",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
