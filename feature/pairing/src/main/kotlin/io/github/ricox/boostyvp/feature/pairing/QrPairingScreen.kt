package io.github.ricox.boostyvp.feature.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.ui.component.BackButton
import io.github.ricox.boostyvp.core.ui.component.BoostyVPBrand
import io.github.ricox.boostyvp.core.ui.component.ErrorState
import io.github.ricox.boostyvp.core.ui.component.LoadingState
import io.github.ricox.boostyvp.core.ui.component.ScreenBackdrop
import io.github.ricox.boostyvp.core.ui.component.ScreenHeading
import io.github.ricox.boostyvp.core.ui.component.StatusChip
import io.github.ricox.boostyvp.core.ui.theme.Overscan

@Composable
fun QrPairingScreen(onPaired: () -> Unit, onBack: (() -> Unit)? = null,
    viewModel: QrPairingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state is QrPairingState.Success) onPaired() }
    when (val current = state) {
        is QrPairingState.Starting, is QrPairingState.Receiving, is QrPairingState.Success -> LoadingState()
        is QrPairingState.Failed -> ErrorState(
            title = "Не удалось подключиться",
            detail = listOfNotNull(current.message, current.detail).joinToString("\n\n"),
            onRetry = viewModel::begin,
        )
        is QrPairingState.Waiting -> QrPairingContent(current.offer.url, onBack)
    }
}

@Composable
fun QrPairingContent(url: String, onBack: (() -> Unit)? = null) {
    val qr = remember(url) { generateQrBitmap(url) }
    val colors = MaterialTheme.colorScheme
    ScreenBackdrop {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Overscan.Horizontal, vertical = Overscan.Vertical),
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                BoostyVPBrand()
                if (onBack != null) BackButton(onBack)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.background(Color.White, RoundedCornerShape(24.dp)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (qr != null) Image(qr, "QR-код для подключения к ТВ",
                        Modifier.size(240.dp).clip(RoundedCornerShape(8.dp)))
                    Text("Наведите камеру телефона", style = MaterialTheme.typography.labelLarge,
                        color = colors.onPrimary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    ScreenHeading("Продолжите на своём устройстве",
                        "Отсканируйте QR-код, войдите в Boosty в браузере и подтвердите подключение к ТВ.")
                    Column(
                        Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))
                            .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Или откройте в браузере", style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant)
                        Text(url, style = MaterialTheme.typography.titleLarge, color = colors.primary)
                    }
                    StatusChip("Телефон и ТВ — в одной сети")
                    Text("Код работает, пока открыт этот экран.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
