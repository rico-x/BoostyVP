package io.github.ricox.boostyvp.core.ui.component

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

/** INFO is read-only; OK on the card keeps its existing playback action. */
@Composable
internal fun FullTitleDialog(title: String, onDismiss: () -> Unit) {
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.widthIn(max = 800.dp).fillMaxWidth().heightIn(max = 440.dp)
            .testTag("full_title_dialog").background(MaterialTheme.colorScheme.surface).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(tr(UiText.full_title_dialog_001), style = MaterialTheme.typography.bodySmall)
            Text(title, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f, fill = false).verticalScroll(scroll).testTag("full_title_text").focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.DirectionDown || event.key == Key.DirectionUp) {
                            if (event.type == KeyEventType.KeyDown) scope.launch {
                                scroll.scrollTo((scroll.value + if (event.key == Key.DirectionDown) 120 else -120).coerceIn(0, scroll.maxValue))
                            }
                            true
                        } else false
                    }.focusable())
        }
        LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus() }
    }
}
