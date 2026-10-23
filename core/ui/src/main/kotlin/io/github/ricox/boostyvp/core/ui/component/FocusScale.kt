package io.github.ricox.boostyvp.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag

/**
 * The single most important interaction on a TV: the focused item must be
 * unmistakable from three metres away. Scale alone reads better than a border
 * alone, and the two together are what every shipping TV UI uses.
 */
@Composable
fun Modifier.tvFocusScale(
    focusedScale: Float = 1.08f,
    onFocusChanged: (Boolean) -> Unit = {},
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "focusScale",
    )
    return this
        .onFocusChanged {
            focused = it.isFocused
            onFocusChanged(it.isFocused)
        }
        .scale(scale)
}

@Composable
fun rememberTvInteractionSource(): MutableInteractionSource =
    remember { MutableInteractionSource() }

fun Modifier.tvTestTag(tag: String): Modifier = this.testTag(tag)
