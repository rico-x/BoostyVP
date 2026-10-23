package io.github.ricox.boostyvp.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import io.github.ricox.boostyvp.core.domain.auth.SessionDecoder
import io.github.ricox.boostyvp.core.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data object Working : PairingUiState
    data class Error(val message: String) : PairingUiState
    data object Success : PairingUiState
}

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val decoder: SessionDecoder,
) : ViewModel() {

    private val _state = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    /**
     * Accepts the raw `auth` value copied out of boosty.to plus the `_clientId`
     * value, and stores the resulting session.
     *
     * The auth value is accepted in whatever form the user managed to copy:
     * URL-encoded (as it appears in the cookie) or already-decoded JSON (as it
     * appears in localStorage). Asking a person reading DevTools on a TV to get
     * the encoding right is not realistic.
     */
    fun submitManualToken(rawAuth: String, clientId: String) {
        if (_state.value == PairingUiState.Working || _state.value == PairingUiState.Success) return
        _state.value = PairingUiState.Working
        viewModelScope.launch {
            _state.value = try {
                val session = parseAuth(rawAuth.trim(), clientId.trim())
                sessionManager.signIn(session)
                PairingUiState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PairingUiState.Error(e.message ?: "Could not read that token")
            }
        }
    }

    internal fun parseAuth(rawAuth: String, clientId: String): Session =
        decoder.decode(rawAuth, clientId)
}
