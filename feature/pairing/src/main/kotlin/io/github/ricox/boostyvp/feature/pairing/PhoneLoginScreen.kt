package io.github.ricox.boostyvp.feature.pairing

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.ui.component.BackButton
import io.github.ricox.boostyvp.core.ui.component.BoostyVPBrand
import io.github.ricox.boostyvp.core.ui.component.ScreenBackdrop
import io.github.ricox.boostyvp.core.ui.component.ScreenHeading
import io.github.ricox.boostyvp.core.ui.component.StatusChip
import io.github.ricox.boostyvp.core.ui.component.TvActionButton
import io.github.ricox.boostyvp.core.ui.component.LoadingState
import io.github.ricox.boostyvp.core.ui.theme.Overscan

@Composable
fun PhoneLoginScreen(onSignedIn: () -> Unit, onBack: (() -> Unit)? = null,
    viewModel: PhoneLoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.success) { if (state.success) onSignedIn() }
    if (state.success) LoadingState() else PhoneLoginForm(
        state, viewModel::changePhone, viewModel::changeCode, viewModel::submit, onBack)
}

/** Native focus/IME controls; no embedded page or social-login controls. */
@Composable
fun PhoneLoginForm(
    state: PhoneLoginState,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val phoneFocus = remember { FocusRequester() }
    val codeFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { phoneFocus.requestFocus() }
    LaunchedEffect(state.codeRequested, state.busy) {
        if (state.codeRequested && !state.busy) codeFocus.requestFocus()
    }

    val colors = MaterialTheme.colorScheme
    ScreenBackdrop {
        Row(
            modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Overscan.Horizontal, vertical = Overscan.Vertical),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                BoostyVPBrand()
                ScreenHeading(tr(UiText.phone_login_screen_001),
                    tr(UiText.phone_login_screen_002))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusChip(if (state.codeRequested) tr(UiText.phone_login_screen_003) else tr(UiText.phone_login_screen_004))
                    Text(if (state.codeRequested) tr(UiText.phone_login_screen_005) else tr(UiText.phone_login_screen_006),
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                if (onBack != null) BackButton(onBack)
            }
            Column(
                modifier = Modifier.weight(1.15f).widthIn(max = 460.dp)
                    .background(colors.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(tr(UiText.phone_login_screen_007), style = MaterialTheme.typography.headlineSmall)
                PhoneField(tr(UiText.phone_login_screen_008), state.phone, onPhoneChange, "phone_number", phoneFocus,
                    enabled = !state.busy, type = KeyboardType.Phone, imeAction = ImeAction.Next,
                    onNext = { if (state.codeRequested) codeFocus.requestFocus() else if (state.canSubmit) submitFocus.requestFocus() })
                PhoneField(tr(UiText.phone_login_screen_009), state.code, onCodeChange, "phone_code", codeFocus,
                    enabled = state.codeRequested && !state.busy, type = KeyboardType.Number,
                    imeAction = ImeAction.Done, onPrevious = { phoneFocus.requestFocus() },
                    onNext = { if (state.canSubmit) submitFocus.requestFocus() },
                    onDone = { if (state.canSubmit) { keyboard?.hide(); onSubmit() } })
                state.notice?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.codeRequested && state.resendIn > 0) {
                    Text(tr(UiText.phone_login_screen_010, state.resendIn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TvActionButton(
                    text = state.button, primary = true,
                    onClick = { keyboard?.hide(); onSubmit() }, enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth().testTag("phone_submit").focusRequester(submitFocus)
                        .onFocusChanged { if (it.isFocused) keyboard?.hide() }
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) {
                                if (state.codeRequested) codeFocus.requestFocus() else phoneFocus.requestFocus()
                                true
                            } else false
                        },
                )
            }
        }
    }
}

@Composable
private fun PhoneField(
    label: String, value: String, onValueChange: (String) -> Unit, tag: String,
    focus: FocusRequester, enabled: Boolean, type: KeyboardType, imeAction: ImeAction,
    onNext: () -> Unit, onPrevious: (() -> Unit)? = null, onDone: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focused, enabled) {
        if (focused && enabled) {
            // Wait for BasicTextField's text input connection before showing IME.
            withFrameNanos { }
            keyboard?.show()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge,
            color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value, onValueChange = onValueChange, enabled = enabled, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = imeAction),
            keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone?.invoke() }),
            textStyle = MaterialTheme.typography.titleLarge.copy(color =
                if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().testTag(tag).focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) false else when (it.key) {
                        Key.DirectionDown -> { onNext(); true }
                        Key.DirectionUp -> { onPrevious?.invoke(); onPrevious != null }
                        Key.DirectionCenter, Key.NumPadEnter -> { keyboard?.show(); true }
                        else -> false
                    }
                }
                .heightIn(min = 52.dp)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                .border(if (focused) 2.dp else 1.dp,
                    if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.border,
                    RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
