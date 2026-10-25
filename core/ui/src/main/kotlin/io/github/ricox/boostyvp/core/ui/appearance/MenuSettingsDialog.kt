package io.github.ricox.boostyvp.core.ui.appearance

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.ui.component.TvActionButton

fun menuLabel(id: String): String = when(id) {
    "subscriptions" -> tr(UiText.menu_settings_dialog_001); "new" -> tr(UiText.menu_settings_dialog_002); "history" -> tr(UiText.menu_settings_dialog_003); "later" -> tr(UiText.library_privacy_dialog_005)
    "favorites" -> tr(UiText.library_privacy_dialog_007); "videos" -> tr(UiText.library_privacy_dialog_006); "search" -> tr(UiText.menu_settings_dialog_004); else -> tr(UiText.menu_settings_dialog_005)
}
@Composable
fun MenuSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { MenuPreferences(context) }
    val options = LocalMenuOptions.current
    val first = remember { FocusRequester() }
    val densityLabels = listOf(tr(UiText.menu_settings_dialog_006), tr(UiText.menu_settings_dialog_007), tr(UiText.menu_settings_dialog_008), tr(UiText.menu_settings_dialog_009))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(Modifier.width(760.dp).heightIn(max = 480.dp).background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()).padding(24.dp).testTag("menu_settings"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(UiText.settings_hub_dialog_012), style = MaterialTheme.typography.headlineSmall)
            Text(tr(UiText.menu_settings_dialog_010), style = MaterialTheme.typography.bodySmall)
            TvActionButton(tr(UiText.menu_settings_dialog_011, densityLabels[options.density.ordinal]), {
                prefs.save(options.copy(density = MenuDensity.entries[(options.density.ordinal + 1) % MenuDensity.entries.size]))
            }, Modifier.fillMaxWidth().focusRequester(first).testTag("menu_density"))
            TvActionButton(tr(UiText.menu_settings_dialog_012, options.textSp), { val sizes = listOf(12, 14, 16, 18); prefs.save(options.copy(textSp = sizes[(sizes.indexOf(options.textSp) + 1) % sizes.size])) }, Modifier.fillMaxWidth().testTag("menu_text"))
            TvActionButton(tr(UiText.menu_settings_dialog_013, options.widthDp), { val widths = listOf(192, 224, 256); prefs.save(options.copy(widthDp = widths[(widths.indexOf(options.widthDp) + 1) % widths.size])) }, Modifier.fillMaxWidth().testTag("menu_width"))
            Text(tr(UiText.menu_settings_dialog_014), fontSize = options.textSp.sp, modifier = Modifier.padding(8.dp).testTag("menu_preview"))
            Text(tr(UiText.menu_settings_dialog_015), style = MaterialTheme.typography.titleMedium)
            options.order.forEach { id -> key(id) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvActionButton("${if (id in options.hidden) "□" else "✓"} ${menuLabel(id)}", {
                        prefs.save(options.copy(hidden = if (id in options.hidden) options.hidden - id else options.hidden + id))
                    }, Modifier.weight(1f).testTag("menu_visibility_$id"), enabled = id != "subscriptions")
                    TvActionButton("↑", { prefs.save(options.move(id, -1)) }, Modifier.width(64.dp)
                        .semantics { contentDescription = tr(UiText.menu_settings_dialog_016, menuLabel(id)) }.testTag("menu_up_$id"), enabled = options.order.first() != id)
                    TvActionButton("↓", { prefs.save(options.move(id, 1)) }, Modifier.width(64.dp)
                        .semantics { contentDescription = tr(UiText.menu_settings_dialog_017, menuLabel(id)) }.testTag("menu_down_$id"), enabled = options.order.last() != id)
                }
            } }
            TvActionButton(tr(UiText.menu_settings_dialog_018, if (options.refresh) tr(UiText.library_privacy_dialog_015) else tr(UiText.library_privacy_dialog_016)), { prefs.save(options.copy(refresh = !options.refresh)) }, Modifier.fillMaxWidth())
            TvActionButton(tr(UiText.menu_settings_dialog_019, if (options.filter) tr(UiText.library_privacy_dialog_015) else tr(UiText.library_privacy_dialog_016)), { prefs.save(options.copy(filter = !options.filter)) }, Modifier.fillMaxWidth())
            TvActionButton(tr(UiText.menu_settings_dialog_020, if (options.signOut) tr(UiText.library_privacy_dialog_015) else tr(UiText.library_privacy_dialog_016)), { prefs.save(options.copy(signOut = !options.signOut)) }, Modifier.fillMaxWidth())
            Text(tr(UiText.menu_settings_dialog_021), style = MaterialTheme.typography.bodySmall)
            TvActionButton(tr(UiText.menu_settings_dialog_022), { prefs.save(MenuOptions()) }, Modifier.fillMaxWidth().testTag("menu_reset"))
            TvActionButton(tr(UiText.language_settings_dialog_005), onDismiss, Modifier.fillMaxWidth())
        }
        LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
    }
}
