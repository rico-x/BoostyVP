package io.github.ricox.boostyvp.core.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        Column(
            Modifier.width(520.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Настройки плеера", style = MaterialTheme.typography.headlineSmall)
            Text("Сохраняются автоматически. Применяются к следующему видео. OK меняет значение.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TvActionButton("Назад: ${options.seekBackSeconds} с", {
                onChange(options.copy(seekBackSeconds = PlayerOptions.BACK_STEPS.nextChoice(options.seekBackSeconds)))
            }, Modifier.fillMaxWidth())
            TvActionButton("Вперёд: ${options.seekForwardSeconds} с", {
                onChange(options.copy(seekForwardSeconds = PlayerOptions.FORWARD_STEPS.nextChoice(options.seekForwardSeconds)))
            }, Modifier.fillMaxWidth())
            val timeout = if (options.controllerSeconds == 0) "не скрывать" else "${options.controllerSeconds} с"
            TvActionButton("Панель управления: $timeout", {
                onChange(options.copy(controllerSeconds = PlayerOptions.CONTROLLER_TIMES.nextChoice(options.controllerSeconds)))
            }, Modifier.fillMaxWidth())
            TvActionButton("Запоминать скорость: ${if (options.rememberSpeed) "да" else "нет"}", {
                onChange(options.copy(rememberSpeed = !options.rememberSpeed))
            }, Modifier.fillMaxWidth())
            TvActionButton("Готово", onDismiss, Modifier.fillMaxWidth(), primary = true)
        }
    }
}
