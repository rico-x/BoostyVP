package io.github.ricox.boostyvp.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun SignOutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    inProgress: Boolean = false,
    error: String? = null,
) {
    val cancelFocus = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = { if (!inProgress) onDismiss() }) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState())
                .background(colors.surface, shape).border(1.dp, colors.border, shape).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, null,
                tint = colors.primary, modifier = Modifier.size(32.dp))
            Text("Выйти из профиля?", style = MaterialTheme.typography.headlineSmall)
            Text("Ваши подписки останутся в Boosty. Для просмотра на ТВ потребуется войти снова.",
                style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            if (error != null) Text(error, color = colors.error)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvActionButton("Остаться", onDismiss,
                    modifier = Modifier.weight(1f).focusRequester(cancelFocus), enabled = !inProgress)
                TvActionButton(if (inProgress) "Выходим…" else "Выйти", onConfirm,
                    modifier = Modifier.weight(1f), enabled = !inProgress)
            }
        }
        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    }
}
