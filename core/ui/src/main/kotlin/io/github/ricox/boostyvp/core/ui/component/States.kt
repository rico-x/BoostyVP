package io.github.ricox.boostyvp.core.ui.component

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val rotation by rememberInfiniteTransition(label = "loading").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "loading rotation",
    )
    ScreenBackdrop(modifier) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Canvas(Modifier.size(44.dp).progressSemantics()) {
                val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(track, style = stroke)
                drawArc(accent, rotation, 100f, useCenter = false, style = stroke)
            }
            Text(tr(UiText.states_001), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector = Icons.Default.Info,
) {
    CenteredMessage(title, modifier, detail, icon)
}

@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    title: String = tr(UiText.states_002),
    detail: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val retryFocus = remember { FocusRequester() }
    CenteredMessage(title, modifier, detail, Icons.Default.Warning) {
        if (onRetry != null) {
            TvActionButton(tr(UiText.states_003), onRetry,
                modifier = Modifier.focusRequester(retryFocus), icon = Icons.Default.Refresh, primary = true)
            LaunchedEffect(Unit) { retryFocus.requestFocus() }
        }
    }
}

@Composable
private fun CenteredMessage(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector,
    action: @Composable () -> Unit = {},
) {
    ScreenBackdrop(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 640.dp))
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 580.dp))
            action()
        }
    }
}
