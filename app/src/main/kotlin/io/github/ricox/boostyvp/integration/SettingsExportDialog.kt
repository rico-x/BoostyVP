package io.github.ricox.boostyvp.integration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.ui.component.TvActionButton
import io.github.ricox.boostyvp.localization.UiText
import io.github.ricox.boostyvp.localization.tr

@Composable
internal fun SettingsExportDialog(
    apps: List<ExportApp>,
    busy: Boolean,
    error: String,
    onPick: (ExportApp) -> Unit,
    onBrowserTransfer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(Modifier.width(600.dp).heightIn(max = 480.dp).background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()).padding(24.dp).testTag("settings_export_apps"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr(UiText.export_choose_app), style = MaterialTheme.typography.headlineSmall)
            Text(tr(if (apps.isEmpty()) UiText.export_no_apps else UiText.export_choose_location),
                style = MaterialTheme.typography.bodyMedium)
            apps.forEachIndexed { index, app ->
                TvActionButton(app.label, { onPick(app) }, Modifier.fillMaxWidth()
                    .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                    .testTag("export_app_${app.packageName}"), enabled = !busy)
            }
            if (busy) Text(tr(UiText.export_preparing), style = MaterialTheme.typography.bodySmall)
            if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("settings_export_error"))
            TvActionButton(tr(UiText.settings_hub_dialog_025), onBrowserTransfer, Modifier.fillMaxWidth()
                .then(if (apps.isEmpty()) Modifier.focusRequester(first) else Modifier), enabled = !busy)
            TvActionButton(tr(UiText.language_settings_dialog_005), onDismiss,
                Modifier.fillMaxWidth().testTag("settings_export_back"), enabled = !busy)
        }
        LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
    }
}
