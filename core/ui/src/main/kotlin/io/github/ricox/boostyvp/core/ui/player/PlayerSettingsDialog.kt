package io.github.ricox.boostyvp.core.ui.player

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.ui.component.TvActionButton

@Composable
fun PlayerSettingsDialog(
    options: PlayerOptions,
    onChange: (PlayerOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Scrolls on a 540 dp tall TV screen: the focused row is brought into view.
        Column(
            Modifier.width(520.dp).heightIn(max = 500.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(tr(UiText.player_settings_dialog_001), style = MaterialTheme.typography.headlineSmall)
            Text(tr(UiText.player_settings_dialog_002),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TvActionButton(tr(UiText.player_settings_dialog_003, options.seekBackSeconds), {
                onChange(options.copy(seekBackSeconds = PlayerOptions.BACK_STEPS.nextChoice(options.seekBackSeconds)))
            }, Modifier.fillMaxWidth())
            TvActionButton(tr(UiText.player_settings_dialog_004, options.seekForwardSeconds), {
                onChange(options.copy(seekForwardSeconds = PlayerOptions.FORWARD_STEPS.nextChoice(options.seekForwardSeconds)))
            }, Modifier.fillMaxWidth())
            val timeout = if (options.controllerSeconds == 0) tr(UiText.player_settings_dialog_005) else tr(UiText.player_settings_dialog_006, options.controllerSeconds)
            TvActionButton(tr(UiText.player_settings_dialog_007, timeout), {
                onChange(options.copy(controllerSeconds = PlayerOptions.CONTROLLER_TIMES.nextChoice(options.controllerSeconds)))
            }, Modifier.fillMaxWidth())
            TvActionButton(tr(UiText.player_settings_dialog_008, yesNo(options.rememberSpeed)), {
                onChange(options.copy(rememberSpeed = !options.rememberSpeed))
            }, Modifier.fillMaxWidth())
            TvActionButton(tr(UiText.player_settings_dialog_009, yesNo(options.speedPerAuthor)), {
                onChange(options.copy(speedPerAuthor = !options.speedPerAuthor))
            }, Modifier.fillMaxWidth())
            TvActionButton(tr(UiText.player_settings_dialog_010, yesNo(options.rememberQuality)), {
                onChange(options.copy(rememberQuality = !options.rememberQuality))
            }, Modifier.fillMaxWidth())
            TvActionButton(tr(UiText.player_settings_dialog_011, options.endAction.label), {
                onChange(options.copy(endAction = EndAction.entries.nextItem(options.endAction)))
            }, Modifier.fillMaxWidth())
            TvActionButton(tr(UiText.player_settings_dialog_012, PlayerOptions.sleepLabel(options.sleepTimerMinutes)), {
                onChange(options.copy(sleepTimerMinutes = PlayerOptions.SLEEP_TIMES.nextChoice(options.sleepTimerMinutes)))
            }, Modifier.fillMaxWidth())
            if (options.sleepTimerMinutes > 0) Text(
                tr(UiText.player_settings_dialog_013),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(tr(UiText.player_settings_dialog_014), style = MaterialTheme.typography.titleMedium)
            TvActionButton("CH±: ${if (options.channelVideoNavigation) tr(UiText.player_settings_dialog_015) else tr(UiText.player_settings_dialog_016)}", {
                onChange(options.copy(channelVideoNavigation = !options.channelVideoNavigation))
            }, Modifier.fillMaxWidth())
            TvActionButton("FF/REW: ${if (options.transportVideoNavigation) tr(UiText.player_settings_dialog_015) else tr(UiText.player_settings_dialog_017)}", {
                onChange(options.copy(transportVideoNavigation = !options.transportVideoNavigation))
            }, Modifier.fillMaxWidth())
            TvActionButton(tr(UiText.player_settings_dialog_019, if (options.digitSeek) tr(UiText.player_settings_dialog_018) else tr(UiText.player_settings_dialog_016)), {
                onChange(options.copy(digitSeek = !options.digitSeek))
            }, Modifier.fillMaxWidth())
            TvActionButton(tr(UiText.player_settings_dialog_020, yesNo(options.doubleBackExit)), {
                onChange(options.copy(doubleBackExit = !options.doubleBackExit))
            }, Modifier.fillMaxWidth())
            TvActionButton(tr(UiText.player_settings_dialog_021, options.videoScale.label), {
                onChange(options.copy(videoScale = VideoScale.entries.nextItem(options.videoScale)))
            }, Modifier.fillMaxWidth())
            TvActionButton(tr(UiText.settings_close), onDismiss,
                Modifier.fillMaxWidth().testTag("player_settings_close"), primary = true)
        }
    }
}

private fun yesNo(value: Boolean) = if (value) tr(UiText.player_settings_dialog_022) else tr(UiText.player_settings_dialog_023)
