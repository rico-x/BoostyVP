package io.github.ricox.boostyvp.core.ui.component

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.ui.theme.LocalAccentText

@Composable
fun ScreenBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxSize().background(colors.background), content = content,
    )
}

@Composable
fun BoostyVPBrand(modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.PlayArrow, contentDescription = null,
            tint = colors.primary, modifier = Modifier.size(28.dp))
        Text("BoostyVP", style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
        if (!compact) Text("TV", style = MaterialTheme.typography.labelMedium, color = colors.primary,
            modifier = Modifier.background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = ButtonDefaults.shape(shape),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) colors.primary else colors.surfaceVariant,
            contentColor = if (primary) colors.onPrimary else colors.onSurface,
            focusedContainerColor = colors.surfaceVariant,
            focusedContentColor = colors.onSurface,
            pressedContainerColor = colors.surfaceVariant,
            pressedContentColor = colors.onSurface,
            disabledContainerColor = colors.surfaceVariant,
            disabledContentColor = colors.onSurfaceVariant,
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, colors.onSurface), inset = 3.dp, shape = shape),
        ),
        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null,
            modifier = Modifier.padding(end = 10.dp).size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ScreenHeading(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvActionButton(tr(UiText.language_settings_dialog_005), onClick, modifier, icon = Icons.AutoMirrored.Filled.ArrowBack)
}

@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = LocalAccentText.current, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = LocalAccentText.current)
    }
}

