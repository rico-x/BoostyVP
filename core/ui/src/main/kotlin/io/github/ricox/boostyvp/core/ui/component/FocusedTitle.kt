package io.github.ricox.boostyvp.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** A non-focusable static reading panel: never moves cards or takes remote keys. */
@Composable
internal fun FocusedTitle(title: String) {
    if (!LocalWindowInfo.current.isWindowFocused) return
    val margin = with(LocalDensity.current) { 27.dp.roundToPx() }
    val position = remember(margin) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset(
            ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0),
            (windowSize.height - popupContentSize.height - margin).coerceAtLeast(0))
    } }
    val width = (LocalConfiguration.current.screenWidthDp - 96).coerceAtLeast(160).dp
    val colors = MaterialTheme.colorScheme
    Popup(popupPositionProvider = position,
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Text(title, style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface, modifier = Modifier.testTag("focused_full_title")
                .widthIn(max = minOf(width, 720.dp))
                .background(colors.surface, RoundedCornerShape(12.dp))
                .border(1.dp, colors.border, RoundedCornerShape(12.dp)).padding(16.dp))
    }
}
