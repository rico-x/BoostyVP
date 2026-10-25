package io.github.ricox.boostyvp.feature.pairing

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.auth.PhoneLogin
import io.github.ricox.boostyvp.core.domain.auth.PhoneLoginException
import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

data class PhoneLoginState(
    val phone: String = "+7",
    val code: String = "",
    val codeRequested: Boolean = false,
    val resendIn: Int = 0,
    val busy: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
) {
    val canSubmit: Boolean get() = !busy && !success && normalizePhone(phone) != null &&
        (!codeRequested || code.length == 6 || (code.isEmpty() && resendIn == 0))
    val button: String get() = when {
        busy -> tr(UiText.phone_login_view_model_001)
        !codeRequested -> tr(UiText.phone_login_view_model_002)
        code.isEmpty() && resendIn == 0 -> tr(UiText.phone_login_view_model_003)
        else -> tr(UiText.phone_login_view_model_004)
    }
}

internal fun normalizePhone(value: String): String? {
    val digits = value.filter { it in '0'..'9' }
    if (digits.length !in 8..15) return null
    return "+" + if (!value.trim().startsWith('+') && digits.length == 11 && digits.startsWith('8')) "7" + digits.drop(1) else digits
}

@HiltViewModel
class PhoneLoginViewModel @Inject constructor(
    private val phoneLogin: PhoneLogin,
    private val sessions: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(PhoneLoginState())
    val state = _state.asStateFlow()
    private val deviceId = UUID.randomUUID().toString()
    private var verificationId: String? = null
    private var resendTimer: Job? = null

    fun changePhone(value: String) {
        if (_state.value.busy) return
        val cleaned = value.filter { it in '0'..'9' || it == '+' }.take(16)
        if (cleaned == _state.value.phone) return
        verificationId = null
        resendTimer?.cancel()
        _state.value = PhoneLoginState(phone = cleaned)
    }

    fun changeCode(value: String) {
        if (!_state.value.busy) _state.value = _state.value.copy(
            code = value.filter { it in '0'..'9' }.take(6), error = null)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val phone = normalizePhone(current.phone) ?: return
        _state.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val id = verificationId
                if (id == null || current.code.isEmpty()) {
                    verificationId = phoneLogin.requestCode(phone, deviceId).verificationId
                    _state.value = current.copy(busy = false, codeRequested = true, code = "", resendIn = 120,
                        error = null, notice = tr(UiText.phone_login_view_model_005))
                    resendTimer?.cancel()
                    resendTimer = launch {
                        while (_state.value.resendIn > 0) {
                            delay(1_000)
                            _state.value = _state.value.copy(resendIn = (_state.value.resendIn - 1).coerceAtLeast(0))
                        }
                    }
                } else {
                    val session = phoneLogin.confirmCode(phone, id, current.code, deviceId)
                    sessions.signIn(session)
                    verificationId = null
                    resendTimer?.cancel()
                    _state.value = PhoneLoginState(phone = "", success = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = when (e) {
                    is PhoneLoginException -> e.message
                    is IOException -> tr(UiText.phone_login_view_model_006)
                    else -> tr(UiText.phone_login_view_model_007)
                }
                _state.value = _state.value.copy(busy = false, error = message)
            }
        }
    }
}
