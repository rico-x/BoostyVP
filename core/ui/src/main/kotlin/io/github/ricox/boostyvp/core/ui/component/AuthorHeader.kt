package io.github.ricox.boostyvp.core.ui.component

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.ui.component.AuthorAvatar
import io.github.ricox.boostyvp.core.ui.component.BackButton
import io.github.ricox.boostyvp.core.ui.component.StatusChip
import io.github.ricox.boostyvp.core.ui.appearance.AuthorHeaderSize
import io.github.ricox.boostyvp.core.ui.appearance.LocalInterfaceOptions
import io.github.ricox.boostyvp.core.ui.theme.LocalAccentText
import androidx.compose.ui.focus.focusProperties
import java.text.NumberFormat

@Composable
fun AuthorHeader(blog: Blog?, blogUrl: String, onBack: () -> Unit, interactive: Boolean = true) {
    val name = blog?.ownerName?.takeIf { it.isNotBlank() }
        ?: blog?.title?.takeIf { it.isNotBlank() } ?: blogUrl
    val colors = MaterialTheme.colorScheme
    val density = LocalInterfaceOptions.current.authorHeader
    if (density != AuthorHeaderSize.Spacious) {
        CompactBlogHeader(blog, blogUrl, name, density, onBack, interactive)
        return
    }
    val shape = RoundedCornerShape(24.dp)
    Box(Modifier.fillMaxWidth().testTag("author_header").clip(shape).border(1.dp, colors.border.copy(alpha = 0.5f), shape)) {
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
                HeaderBack(onBack, interactive)
                if (blog?.isSubscribed == true) StatusChip(tr(UiText.author_header_001), icon = Icons.Default.Check)
            }
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                AuthorAvatar(name, blog?.avatarUrl, size = 88.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, style = MaterialTheme.typography.headlineLarge, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                    Text("@$blogUrl", style = MaterialTheme.typography.bodyMedium, color = LocalAccentText.current)
                    val title = blog?.title.orEmpty()
                    if (title.isNotBlank() && title != name) Text(title,
                        style = MaterialTheme.typography.bodyLarge, color = colors.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (blog != null && blog.postCount > 0) Text(
                        tr(UiText.author_header_002, NumberFormat.getIntegerInstance(io.github.ricox.boostyvp.localization.Language.locale).format(blog.postCount)),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CompactBlogHeader(blog: Blog?, url: String, name: String, size: AuthorHeaderSize, onBack: () -> Unit, interactive: Boolean) {
    val colors = MaterialTheme.colorScheme
    val reduced = size == AuthorHeaderSize.Reduced
    val minimal = size == AuthorHeaderSize.Minimal
    val count = blog?.postCount?.takeIf { it > 0 }?.let {
        tr(UiText.author_header_003, NumberFormat.getIntegerInstance(io.github.ricox.boostyvp.localization.Language.locale).format(it))
    }.orEmpty()
    Column(Modifier.fillMaxWidth().testTag("author_header")
        .background(colors.surface, RoundedCornerShape(16.dp))
        .padding(size.paddingDp.dp), verticalArrangement = Arrangement.spacedBy(if (reduced) 12.dp else 4.dp)) {
        if (reduced) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            HeaderBack(onBack, interactive)
            if (blog?.isSubscribed == true) StatusChip(tr(UiText.author_header_001), icon = Icons.Default.Check)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(if (minimal) 8.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (!reduced) HeaderBack(onBack, interactive)
            AuthorAvatar(name, blog?.avatarUrl, size = size.avatarDp.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (minimal) 0.dp else 4.dp)) {
                Text(name, style = if (reduced) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    maxLines = if (reduced) 2 else 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() })
                Text("@$url$count", style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (reduced && !blog?.title.isNullOrBlank() && blog?.title != name) Text(blog!!.title,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!reduced && blog?.isSubscribed == true) StatusChip(tr(UiText.author_header_001), icon = Icons.Default.Check)
        }
    }
}

@Composable
private fun HeaderBack(onBack: () -> Unit, interactive: Boolean) {
    BackButton(onBack, Modifier.focusProperties { canFocus = interactive })
}
