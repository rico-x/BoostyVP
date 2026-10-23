package io.github.ricox.boostyvp.core.ui.player

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
            TvActionButton("Запоминать скорость: ${yesNo(options.rememberSpeed)}", {
                onChange(options.copy(rememberSpeed = !options.rememberSpeed))
            }, Modifier.fillMaxWidth())
            TvActionButton("Скорость для каждого автора: ${yesNo(options.speedPerAuthor)}", {
                onChange(options.copy(speedPerAuthor = !options.speedPerAuthor))
            }, Modifier.fillMaxWidth())
            TvActionButton("Запоминать качество: ${yesNo(options.rememberQuality)}", {
                onChange(options.copy(rememberQuality = !options.rememberQuality))
            }, Modifier.fillMaxWidth())
            TvActionButton("В конце видео: ${options.endAction.label}", {
                onChange(options.copy(endAction = EndAction.entries.nextItem(options.endAction)))
            }, Modifier.fillMaxWidth())
            TvActionButton("Таймер сна: ${PlayerOptions.sleepLabel(options.sleepTimerMinutes)}", {
                onChange(options.copy(sleepTimerMinutes = PlayerOptions.SLEEP_TIMES.nextChoice(options.sleepTimerMinutes)))
            }, Modifier.fillMaxWidth())
            if (options.sleepTimerMinutes > 0) Text(
                "Пауза, если столько времени не нажимать кнопки пульта во время просмотра.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TvActionButton("Готово", onDismiss, Modifier.fillMaxWidth(), primary = true)
        }
    }
}

private fun yesNo(value: Boolean) = if (value) "да" else "нет"
