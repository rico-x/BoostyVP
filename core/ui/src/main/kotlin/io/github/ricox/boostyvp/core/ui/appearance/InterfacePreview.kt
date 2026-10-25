package io.github.ricox.boostyvp.core.ui.appearance

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.model.*
import io.github.ricox.boostyvp.core.ui.component.AuthorHeader
import io.github.ricox.boostyvp.core.ui.component.PostCard
import io.github.ricox.boostyvp.core.ui.theme.LocalAccentText

@Composable
internal fun InterfacePreview(section: InterfaceSection, modifier: Modifier = Modifier) {
    val o = LocalInterfaceOptions.current
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(when (section) {
            InterfaceSection.Catalog -> tr(UiText.interface_preview_001)
            InterfaceSection.Text -> tr(UiText.interface_preview_002)
            InterfaceSection.Focus -> tr(UiText.interface_preview_003)
            InterfaceSection.Author -> tr(UiText.interface_preview_004)
            InterfaceSection.Theme -> tr(UiText.interface_preview_005)
            InterfaceSection.Player -> tr(UiText.interface_preview_006)
        }, style = MaterialTheme.typography.titleMedium)
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds(), contentAlignment = Alignment.Center) {
            when (section) {
                InterfaceSection.Catalog, InterfaceSection.Text -> LazyRow(userScrollEnabled = false,
                    horizontalArrangement = Arrangement.spacedBy(o.density.horizontalGapDp.dp)) {
                    items(2) { index -> PostCard(PreviewPost.copy(id = "preview_$index"), {},
                        Modifier.focusProperties { canFocus = false }.then(if (index == 0) Modifier.testTag("interface_preview") else Modifier),
                        inAuthorRow = section == InterfaceSection.Catalog) }
                }
                InterfaceSection.Focus -> FocusPreview(o)
                InterfaceSection.Author -> ScaledPreview("interface_author_preview") {
                    LazyVerticalGrid(columns = GridCells.Adaptive(LocalCardSize.current.widthDp.dp),
                        modifier = Modifier.fillMaxSize().background(colors.background),
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 27.dp),
                        horizontalArrangement = Arrangement.spacedBy(o.density.horizontalGapDp.dp),
                        verticalArrangement = Arrangement.spacedBy(o.density.gridGapDp.dp),
                        userScrollEnabled = false) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AuthorHeader(PreviewBlog, "author", {}, interactive = false)
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(tr(UiText.interface_preview_007), style = MaterialTheme.typography.titleLarge)
                        }
                        items(8) { index ->
                            PostCard(PreviewPost.copy(id = "a$index"), {},
                                Modifier.focusProperties { canFocus = false }, inAuthorRow = true)
                        }
                    }
                }
                InterfaceSection.Theme -> ScaledPreview("interface_theme_preview") {
                    Row(Modifier.fillMaxSize().background(colors.background)) {
                        Column(Modifier.width(112.dp).fillMaxHeight().background(colors.surface).padding(32.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Icon(Icons.Default.PlayArrow, null, tint = LocalAccentText.current)
                            Icon(Icons.Default.Home, null, tint = LocalAccentText.current)
                            Icon(Icons.Default.Settings, null, tint = colors.onSurface)
                        }
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Text(tr(UiText.interface_preview_008), style = MaterialTheme.typography.headlineSmall)
                            Text(tr(UiText.interface_preview_009), style = MaterialTheme.typography.titleMedium)
                            LazyRow(userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(o.density.horizontalGapDp.dp)) {
                                items(4) { index -> PostCard(PreviewPost.copy(id = "t$index"), {},
                                    Modifier.focusProperties { canFocus = false }, previewFocused = index == 0) }
                            }
                        }
                    }
                }
                InterfaceSection.Player -> ScaledPreview("interface_player_preview") { PlayerPanelPreview(o) }
            }
        }
        val fontScale = LocalContext.current.resources.configuration.fontScale
        Text(when (section) {
            InterfaceSection.Catalog -> tr(UiText.interface_preview_010)
            InterfaceSection.Text -> tr(UiText.interface_preview_011, o.textSize.titleSp, o.metadataSp, (fontScale * 100).toInt())
            InterfaceSection.Focus -> tr(UiText.focus_preview_hint)
            InterfaceSection.Author -> tr(UiText.interface_preview_013, o.authorHeader.label)
            InterfaceSection.Theme -> tr(UiText.interface_preview_014)
            InterfaceSection.Player -> tr(UiText.interface_preview_015, o.playerLayout.label, o.playerText.label.lowercase())
        }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** Animate the real card between unfocused/focused; never take remote focus. */
@Composable
private fun FocusPreview(options: InterfaceOptions) {
    var selected by remember { mutableStateOf(false) }
    LaunchedEffect(options.focusZoom, options.animateFocus, options.focusColor, options.focusWidth) {
        while (isActive) {
            selected = false; delay(900)
            selected = true; delay(1_800)
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr(if (selected) UiText.focus_preview_selected else UiText.focus_preview_idle),
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("interface_focus_phase"))
        // Scale only to fit the demonstration area, reserving room for 102% zoom.
        // Card layout, border, colors and animation remain the real implementation.
        Layout(content = {
            PostCard(PreviewPost, {}, Modifier.focusProperties { canFocus = false }
                .testTag("interface_focus_preview"), previewFocused = selected)
        }, modifier = Modifier.fillMaxWidth().weight(1f)) { children, constraints ->
            val card = children.single().measure(Constraints(maxWidth = constraints.maxWidth))
            val scale = minOf(1f, constraints.maxWidth / (card.width * 1.04f),
                constraints.maxHeight / (card.height * 1.04f))
            layout(constraints.maxWidth, constraints.maxHeight) {
                card.placeWithLayer((constraints.maxWidth - card.width) / 2,
                    (constraints.maxHeight - card.height) / 2) {
                    scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin.Center
                }
            }
        }
        if (options.fullTitle) Box(Modifier.fillMaxWidth().height(48.dp)) {
            if (selected) Text(PreviewPost.title, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.testTag("interface_focus_full_title"))
        }
        Text(tr(UiText.focus_preview_scale, 100, (options.focusScale(true) * 100).roundToInt()),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("interface_focus_scale"))
        Text(tr(UiText.focus_preview_transition, options.focusAnimationMillis),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("interface_focus_duration"))
        Text(tr(UiText.focus_preview_outline, options.focusColor.label, options.focusWidth),
            style = MaterialTheme.typography.bodySmall)
    }
}

/** Measure the actual 960×540 layout, then scale its drawing; never fetches user data. */
@Composable
private fun ScaledPreview(tag: String, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val sourceWidth = with(density) { 960.dp.roundToPx() }
    val sourceHeight = with(density) { 540.dp.roundToPx() }
    Layout(content, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clipToBounds()
        .border(1.dp, MaterialTheme.colorScheme.border, RoundedCornerShape(8.dp)).testTag(tag)) { measurables, constraints ->
        val children = measurables.map { it.measure(Constraints.fixed(sourceWidth, sourceHeight)) }
        val scale = constraints.maxWidth.toFloat() / sourceWidth
        layout(constraints.maxWidth, constraints.maxHeight) {
            children.forEach { child -> child.placeWithLayer(0, 0) {
                scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f)
            } }
        }
    }
}

@Composable
private fun PlayerPanelPreview(o: InterfaceOptions) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Text(tr(UiText.interface_preview_016), style = MaterialTheme.typography.titleLarge.copy(fontSize = PlayerPanelMetrics.TitleSp.sp),
            modifier = Modifier.padding(start = 48.dp, top = 32.dp))
        Column(Modifier.align(Alignment.TopEnd).padding(end = 48.dp, top = 32.dp), horizontalAlignment = Alignment.End) {
            if (o.playerClock) Text("21:30", fontSize = PlayerPanelMetrics.ClockSp.sp)
            if (o.playerEndTime) Text(tr(UiText.interface_preview_017), fontSize = PlayerPanelMetrics.EndTimeSp.sp, color = colors.onSurfaceVariant)
        }
        Row(Modifier.align(if (o.playerLayout == PlayerPanelLayout.Lower) Alignment.BottomCenter else Alignment.Center)
            .then(if (o.playerLayout == PlayerPanelLayout.Lower) Modifier.padding(bottom = (PlayerPanelMetrics.LowerTransportMarginDp + 24).dp) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { index ->
                Box(Modifier.size(52.dp).background(colors.surface, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(36.dp)) {
                        val w = size.width; val h = size.height
                        if (index == 1) {
                            drawRect(colors.onSurface, Offset(w * 0.2f, h * 0.15f), Size(w * 0.2f, h * 0.7f))
                            drawRect(colors.onSurface, Offset(w * 0.6f, h * 0.15f), Size(w * 0.2f, h * 0.7f))
                        } else repeat(2) { triangle ->
                            val x = w * (0.1f + triangle * 0.4f)
                            drawPath(Path().apply {
                                moveTo(x + if (index == 0) w * 0.35f else 0f, h * 0.2f)
                                lineTo(x + if (index == 0) 0f else w * 0.35f, h * 0.5f)
                                lineTo(x + if (index == 0) w * 0.35f else 0f, h * 0.8f); close()
                            }, colors.onSurface)
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(start = 48.dp, end = 48.dp,
            bottom = (PlayerPanelMetrics.ProgressBottomDp + 24).dp).fillMaxWidth()
            .height(if (o.playerThickSeekBar) 6.dp else 4.dp).background(colors.border)) {
            Box(Modifier.fillMaxWidth(0.3f).fillMaxHeight().background(colors.primary))
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(horizontal = 48.dp, vertical = 27.dp)
            .fillMaxWidth().height(60.dp).background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("12:34 · 45:00", fontSize = o.playerText.timeSp.sp)
            Text(tr(UiText.interface_preview_018), fontSize = 14.sp)
        }
    }
}

private val PreviewBlog get() = Blog("author", tr(UiText.interface_preview_019), tr(UiText.interface_preview_009), null, null, 0, 178, true, false, null)
private val PreviewPost get() = Post("preview", 0, tr(UiText.interface_preview_020),
    "author", tr(UiText.interface_preview_021), 1_700_000_000, true, false, false, false, null,
    listOf(ContentBlock.Video("preview", null, null, 754, 1920, 1080, null, null, null,
        listOf(VideoSource(VideoSource.Kind.Progressive, "about:blank", 1080, "preview")), true)), emptyList())
