package io.github.ricox.boostyvp.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.ui.component.BoostyVPBrand
import io.github.ricox.boostyvp.core.ui.theme.Overscan

/** Content areas of Home, switched from the sidebar. */
enum class HomeSection { Subscriptions, New }

/** [menuFocus] follows the selected section, so Left from content returns to it. */
@Composable
internal fun HomeSidebar(
    section: HomeSection,
    onSectionClick: (HomeSection) -> Unit,
    menuFocus: FocusRequester,
    contentFocus: FocusRequester,
    hasContent: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onInterfaceSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    val destination = Modifier.focusProperties { if (hasContent) right = contentFocus }
    Column(
        Modifier.width(192.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.18f))
            .padding(start = Overscan.Horizontal, end = 16.dp, top = Overscan.Vertical, bottom = Overscan.Vertical)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoostyVPBrand(compact = true)
        Spacer(Modifier.height(24.dp))
        listOf(
            Triple(HomeSection.Subscriptions, "Подписки", Icons.Default.Home),
            Triple(HomeSection.New, "Новое", Icons.Default.DateRange),
        ).forEach { (target, label, icon) ->
            val selected = target == section
            SidebarButton(label, icon, { onSectionClick(target) },
                destination.then(if (selected) Modifier.focusRequester(menuFocus) else Modifier)
                    .testTag(if (target == HomeSection.New) "sidebar_new" else "sidebar_subscriptions"),
                isSelected = selected)
        }
        SidebarButton("Обновить", Icons.Default.Refresh, onRefresh, destination)
        SidebarButton("Плеер", Icons.Default.Settings, onSettings, destination)
        SidebarButton("Интерфейс", Icons.Default.Menu, onInterfaceSettings,
            destination.testTag("sidebar_interface"))
        Spacer(Modifier.weight(1f))
        SidebarButton("Выйти", Icons.AutoMirrored.Filled.ExitToApp, onSignOut, destination)
    }
}

@Composable
private fun SidebarButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { selected = isSelected },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
        shape = ButtonDefaults.shape(shape),
        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ButtonDefaults.colors(
            containerColor = if (isSelected) colors.surfaceVariant else Color.Transparent,
            contentColor = colors.onSurface,
            focusedContainerColor = colors.surfaceVariant,
            focusedContentColor = colors.onSurface,
        ),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, colors.onSurface), shape = shape)),
    ) {
        Icon(icon, null, tint = if (isSelected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp).padding(end = 4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
