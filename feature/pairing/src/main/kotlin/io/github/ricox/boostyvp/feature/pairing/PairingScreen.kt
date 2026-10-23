package io.github.ricox.boostyvp.feature.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.ui.component.tvFocusScale
import io.github.ricox.boostyvp.core.ui.theme.Overscan

/**
 * Manual session import for development and pairing fallback.
 */
@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var auth by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is PairingUiState.Success) onPaired()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Overscan.Horizontal, vertical = Overscan.Vertical),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Connect your Boosty account", style = MaterialTheme.typography.headlineMedium)

            Text(
                text = "On a computer, sign in at boosty.to, then open DevTools " +
                    "(F12) → Application → Storage and copy the \"auth\" value and " +
                    "the \"_clientId\" value.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TokenField(label = "auth", value = auth, onValueChange = { auth = it })
            TokenField(label = "_clientId", value = clientId, onValueChange = { clientId = it })

            val current = state
            if (current is PairingUiState.Error) {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = { viewModel.submitManualToken(auth, clientId) },
                enabled = state !is PairingUiState.Working,
                modifier = Modifier.tvFocusScale(focusedScale = 1.05f),
            ) {
                Text(if (state is PairingUiState.Working) "Checking…" else "Connect")
            }
        }
    }
}

@Composable
private fun TokenField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            )
        }
    }
}
