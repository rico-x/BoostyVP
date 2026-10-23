package io.github.ricox.boostyvp.core.ui.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import io.github.ricox.boostyvp.core.ui.theme.LocalAccentText
import kotlin.math.roundToInt

/** A single remote target; different controls communicate boolean/category/ordinal values. */
@Composable
internal fun VisualSettingRow(setting: VisualSetting, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    var focused by remember { mutableStateOf(false) }
    val s = setting
    Button(onClick = { s.onSelect((s.index + 1) % s.values.size) },
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("interface_${s.key}")
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
                    if (event.type == KeyEventType.KeyDown) {
                        val next = (s.index + if (event.key == Key.DirectionLeft) -1 else 1).coerceIn(s.values.indices)
                        if (next != s.index) s.onSelect(next)
                    }
                    true
                } else false
            }.semantics {
                stateDescription = s.values[s.index]
                if (s.kind == SettingKind.Toggle) toggleableState =
                    if (s.index == 1) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
                else if (s.kind == SettingKind.Steps) {
                    progressBarRangeInfo = ProgressBarRangeInfo(s.index.toFloat(), 0f..s.values.lastIndex.toFloat(),
                        steps = (s.values.size - 2).coerceAtLeast(0))
                    setProgress { value -> if (!value.isFinite()) false else {
                        s.onSelect(value.roundToInt().coerceIn(s.values.indices)); true
                    } }
                }
            },
        shape = ButtonDefaults.shape(shape),
        colors = ButtonDefaults.colors(containerColor = Color.Transparent, contentColor = colors.onSurface,
            focusedContainerColor = colors.surfaceVariant, focusedContentColor = colors.onSurface),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, colors.onSurface), shape = shape)),
        scale = ButtonDefaults.scale(focusedScale = 1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(s.values[s.index], style = MaterialTheme.typography.labelLarge,
                    color = if (focused) LocalAccentText.current else colors.onSurfaceVariant)
                if (s.kind == SettingKind.Toggle) Box(Modifier.width(30.dp).height(16.dp)
                    .background(if (s.index == 1) colors.primary else colors.border, CircleShape)) {
                    Box(Modifier.padding(2.dp).size(12.dp).align(if (s.index == 1) Alignment.CenterEnd else Alignment.CenterStart)
                        .background(if (s.index == 1) colors.onPrimary else colors.onSurface, CircleShape))
                }
            }
            if (s.kind == SettingKind.Steps) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                s.values.indices.forEach { index -> Box(Modifier.weight(1f).height(2.dp).background(
                    if (index <= s.index) LocalAccentText.current else colors.border, shape)) }
            }
            if (s.kind == SettingKind.Color) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentColor.entries.forEachIndexed { index, color ->
                    Box(Modifier.size(if (index == s.index) 20.dp else 14.dp).background(Color(color.argb), CircleShape)
                        .semantics { contentDescription = color.label; selected = index == s.index })
                }
            }
        }
    }
}
