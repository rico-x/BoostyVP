package io.github.ricox.boostyvp.feature.home

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Border
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.ui.component.BoostyVPBrand
import io.github.ricox.boostyvp.core.ui.appearance.BrowseDimensions
import io.github.ricox.boostyvp.core.ui.appearance.LocalMenuOptions
import io.github.ricox.boostyvp.core.ui.appearance.menuLabel
import io.github.ricox.boostyvp.core.ui.theme.Overscan

/** Content areas of Home, switched from the sidebar. */
enum class HomeSection { Subscriptions, New, History, Later, Favorites, Videos, Search, Queue }

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
    expanded: Boolean = true,
    onMenuFocused: () -> Unit = {},
    onlyUnwatched: Boolean = false,
    onToggleUnwatched: (() -> Unit)? = null,
    onUnifiedSettings: (() -> Unit)? = null,
) {
    val menu = LocalMenuOptions.current
    val destination = Modifier.focusProperties { if (hasContent) right = contentFocus }
    Column(
        Modifier.width((if (expanded) menu.widthDp else BrowseDimensions.CollapsedSidebarDp).dp)
            .fillMaxHeight().background(MaterialTheme.colorScheme.background)
            .padding(start = Overscan.Horizontal, end = 16.dp, top = Overscan.Vertical, bottom = Overscan.Vertical)
            .testTag("home_sidebar")
            .semantics { stateDescription = if (expanded) tr(UiText.home_sidebar_001) else tr(UiText.home_sidebar_002) }
            .onFocusChanged { if (it.hasFocus) onMenuFocused() }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(menu.density.gapDp.dp),
    ) {
        if (expanded) BoostyVPBrand(compact = true)
        else Icon(Icons.Default.PlayArrow, "BoostyVP", tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 10.dp).size(28.dp))
        Spacer(Modifier.height(if (menu.density.ordinal < 2) 8.dp else 24.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(menu.density.gapDp.dp)) {
        listOf(
            Triple(HomeSection.Subscriptions, tr(UiText.menu_settings_dialog_001), Icons.Default.Home),
            Triple(HomeSection.New, tr(UiText.menu_settings_dialog_002), Icons.Default.DateRange),
            Triple(HomeSection.History, tr(UiText.menu_settings_dialog_003), Icons.AutoMirrored.Filled.List),
            Triple(HomeSection.Later, tr(UiText.library_privacy_dialog_005), Icons.Default.PlayArrow),
            Triple(HomeSection.Favorites, tr(UiText.home_sidebar_003), Icons.Default.Star),
            Triple(HomeSection.Videos, tr(UiText.library_privacy_dialog_006), Icons.Default.PlayArrow),
            Triple(HomeSection.Search, tr(UiText.menu_settings_dialog_004), Icons.Default.Search),
            Triple(HomeSection.Queue, tr(UiText.menu_settings_dialog_005), Icons.AutoMirrored.Filled.List),
        ).filter { it.first.name.lowercase() !in menu.hidden }
            .sortedBy { menu.order.indexOf(it.first.name.lowercase()) }.forEach { (target, _, icon) ->
            val label = menuLabel(target.name.lowercase())
            val selected = target == section
            SidebarButton(label, icon, { onSectionClick(target) },
                destination.then(if (selected) Modifier.focusRequester(menuFocus) else Modifier)
                    .testTag(when (target) {
                        HomeSection.Subscriptions -> "sidebar_subscriptions"
                        HomeSection.New -> "sidebar_new"
                        HomeSection.History -> "sidebar_history"
                        HomeSection.Later -> "sidebar_later"
                        HomeSection.Favorites -> "sidebar_favorites"
                        HomeSection.Videos -> "sidebar_favorite_videos"
                        HomeSection.Search -> "sidebar_search"
                        HomeSection.Queue -> "sidebar_queue"
                    }),
                isSelected = selected, expanded = expanded)
        }
        if (menu.refresh) SidebarButton(tr(UiText.home_sidebar_004), Icons.Default.Refresh, onRefresh, destination, expanded = expanded)
        if (onToggleUnwatched != null && menu.filter) SidebarButton(tr(UiText.home_sidebar_005),
            Icons.Default.Check, onToggleUnwatched, destination.testTag("sidebar_unwatched")
                .semantics { stateDescription = if (onlyUnwatched) tr(UiText.home_sidebar_006) else tr(UiText.home_sidebar_007) },
            expanded = expanded, isSelected = onlyUnwatched)
        if (onUnifiedSettings != null) SidebarButton(tr(UiText.settings_hub_dialog_009), Icons.Default.Settings, onUnifiedSettings,
            destination.testTag("sidebar_settings"), expanded = expanded)
        else {
            SidebarButton(tr(UiText.settings_hub_dialog_010), Icons.Default.Settings, onSettings, destination, expanded = expanded)
            SidebarButton(tr(UiText.settings_hub_dialog_011), Icons.Default.Menu, onInterfaceSettings,
                destination.testTag("sidebar_interface"), expanded = expanded)
        }
        }
        if (menu.signOut) SidebarButton(tr(UiText.sign_out_dialog_005), Icons.AutoMirrored.Filled.ExitToApp, onSignOut, destination, expanded = expanded)
    }
}

@Composable
private fun SidebarButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    expanded: Boolean = true,
) {
    val menu = LocalMenuOptions.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    // Button's inner Row has a hard 40 dp default minimum. Use the same native
    // TV Surface/DPAD handling with our own content minimum, so Tight/Compact
    // really differ while wrapped large text can still grow vertically.
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { role = Role.Button; selected = isSelected; contentDescription = label },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) colors.surfaceVariant else Color.Transparent,
            contentColor = colors.onSurface,
            focusedContainerColor = colors.surfaceVariant,
            focusedContentColor = colors.onSurface,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, colors.onSurface), shape = shape)),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = menu.density.heightDp.dp)
            .padding(horizontal = 10.dp, vertical = if (menu.density.ordinal < 2) 3.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (isSelected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
        if (expanded) Text(label, style = MaterialTheme.typography.labelMedium, fontSize = menu.textSp.sp,
            lineHeight = (menu.textSp + 3).sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
        }
    }
}
