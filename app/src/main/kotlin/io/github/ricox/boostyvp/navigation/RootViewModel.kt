package io.github.ricox.boostyvp.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import io.github.ricox.boostyvp.core.domain.auth.WebLoginSessionCleaner
import io.github.ricox.boostyvp.core.model.AuthState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val webLoginSessionCleaner: WebLoginSessionCleaner,
) : ViewModel() {
    data class SignOutState(val inProgress: Boolean = false, val error: String? = null)

    private val _signOutState = MutableStateFlow(SignOutState())
    val signOutState = _signOutState.asStateFlow()

    fun signOut() {
        if (_signOutState.value.inProgress) return
        _signOutState.value = SignOutState(inProgress = true)
        viewModelScope.launch {
            try {
                webLoginSessionCleaner.clear()
                sessionManager.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _signOutState.value = SignOutState(error = "Не удалось выйти. Попробуйте ещё раз.")
            } finally {
                _signOutState.value = _signOutState.value.copy(inProgress = false)
            }
        }
    }

    /**
     * Starts as [AuthState.Unknown] rather than SignedOut. Defaulting to
     * SignedOut would flash the pairing screen on every cold start before the
     * token store has been read.
     */
    val authState: StateFlow<AuthState> = sessionManager.authState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthState.Unknown,
    )
}
