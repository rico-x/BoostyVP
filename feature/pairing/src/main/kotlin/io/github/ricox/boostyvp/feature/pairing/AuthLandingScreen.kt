package io.github.ricox.boostyvp.feature.pairing

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.ui.component.BoostyVPBrand
import io.github.ricox.boostyvp.core.ui.component.ScreenBackdrop
import io.github.ricox.boostyvp.core.ui.theme.Overscan

/** Two remote-friendly sign-in methods, with phone pairing focused initially. */
@Composable
fun AuthLandingScreen(
    onChoosePhonePairing: () -> Unit,
    onChooseOnDevice: () -> Unit,
) {
    val firstOption = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstOption.requestFocus() }

    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Overscan.Horizontal, vertical = Overscan.Vertical),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            BoostyVPBrand()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr(UiText.auth_landing_screen_001),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(tr(UiText.auth_landing_screen_002),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AuthOption(
                    title = tr(UiText.auth_landing_screen_003),
                    subtitle = tr(UiText.auth_landing_screen_004),
                    action = tr(UiText.auth_landing_screen_005),
                    icon = R.drawable.auth_phone,
                    recommended = true,
                    onClick = onChoosePhonePairing,
                    modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(firstOption),
                )
                AuthOption(
                    title = tr(UiText.auth_landing_screen_006),
                    subtitle = tr(UiText.auth_landing_screen_007),
                    action = tr(UiText.auth_landing_screen_008),
                    icon = R.drawable.auth_tv,
                    onClick = onChooseOnDevice,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }

            Text(tr(UiText.auth_landing_screen_009),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun AuthOption(
    title: String,
    subtitle: String,
    action: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    recommended: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val container = if (recommended) colors.primary else colors.surfaceVariant
    val content = if (recommended) colors.onPrimary else colors.onSurface

    Card(
        onClick = onClick,
        modifier = modifier.semantics { role = Role.Button },
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = container,
            contentColor = content,
            focusedContainerColor = container,
            focusedContentColor = content,
            pressedContainerColor = if (recommended) colors.primary.copy(alpha = 0.85f) else colors.surface,
            pressedContentColor = content,
        ),
        scale = CardDefaults.scale(focusedScale = 1.025f, pressedScale = 1f),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, content.copy(alpha = 0.12f)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, colors.onSurface), inset = 4.dp, shape = shape),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp).padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(40.dp))
                if (recommended) {
                    Text(tr(UiText.auth_landing_screen_010), style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .background(content.copy(alpha = 0.09f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, minLines = 2)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = if (recommended) 0.85f else 0.8f))
            Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                    modifier = Modifier.padding(start = 12.dp).size(20.dp))
            }
        }
    }
}
