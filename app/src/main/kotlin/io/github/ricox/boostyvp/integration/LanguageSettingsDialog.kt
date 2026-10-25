package io.github.ricox.boostyvp.integration

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.ui.appearance.LanguagePreferences
import io.github.ricox.boostyvp.core.ui.component.TvActionButton

@Composable
fun LanguageSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    val selected = LanguagePreferences.selected(context)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(640.dp).background(MaterialTheme.colorScheme.surface).padding(24.dp)
            .testTag("language_settings"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr(UiText.language_settings_dialog_001), style = MaterialTheme.typography.headlineSmall)
            Text(tr(UiText.language_settings_dialog_002), style = MaterialTheme.typography.bodySmall)
            listOf("system" to tr(UiText.language_settings_dialog_003), "ru" to tr(UiText.language_settings_dialog_004), "en" to "English").forEachIndexed { index, (id, label) ->
                TvActionButton("${if (selected == id) "✓ " else ""}$label", { if (selected != id) LanguagePreferences.change(context, id) },
                    Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(first) else Modifier).testTag("language_$id"))
            }
            TvActionButton(tr(UiText.language_settings_dialog_005), onDismiss, Modifier.fillMaxWidth())
        }
        LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
    }
}
