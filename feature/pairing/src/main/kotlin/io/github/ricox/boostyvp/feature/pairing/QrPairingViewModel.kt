package io.github.ricox.boostyvp.feature.pairing

import io.github.ricox.boostyvp.core.domain.auth.pairing.PairingStartResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import io.github.ricox.boostyvp.core.domain.auth.pairing.PairingOffer
import io.github.ricox.boostyvp.core.domain.auth.pairing.PairingService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface QrPairingState {
    data object Starting : QrPairingState

    data class Waiting(val offer: PairingOffer) : QrPairingState

    data object Receiving : QrPairingState

    data object Success : QrPairingState

    /**
     * @param detail what the device actually reported. Shown on screen so a
     *   failure is diagnosable without a logcat session.
     */
    data class Failed(val message: String, val detail: String? = null) : QrPairingState

}

@HiltViewModel
class QrPairingViewModel @Inject constructor(
    factory: PairingService.Factory,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow<QrPairingState>(QrPairingState.Starting)
    val state: StateFlow<QrPairingState> = _state.asStateFlow()

    private val server = factory.create(viewModelScope)
    private var startJob: Job? = null

    init {
        begin()
    }

    fun begin() {
        if (startJob?.isActive == true || _state.value == QrPairingState.Receiving ||
            _state.value == QrPairingState.Success) return
        _state.value = QrPairingState.Starting
        startJob = viewModelScope.launch {
            // Catching Throwable and bounding the wait is defence in depth
            // against exactly the failure that produced a permanent spinner
            // here before: an Error (not an Exception) escaping start(), killing
            // this coroutine, and leaving the state on Starting with nothing
            // shown. Whatever goes wrong, the screen now says something.
            val result = try {
                withTimeoutOrNull(START_TIMEOUT_MILLIS) { startServer() }
                    ?: PairingStartResult.CouldNotBind(
                        "The pairing server did not start within " +
                            "${START_TIMEOUT_MILLIS / 1000} seconds.",
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                PairingStartResult.CouldNotBind(
                    "${t::class.java.simpleName}: ${t.message ?: "no detail"}",
                )
            }

            if (_state.value != QrPairingState.Starting) return@launch
            _state.value = when (result) {
                is PairingStartResult.Ready ->
                    QrPairingState.Waiting(result.offer)

                is PairingStartResult.NoNetwork -> QrPairingState.Failed(
                    message = "Could not find this device's network address.",
                    detail = result.detail,
                )

                is PairingStartResult.CouldNotBind -> QrPairingState.Failed(
                    message = "Could not open the pairing port.",
                    detail = result.detail,
                )
            }
        }
    }

    private suspend fun startServer(): PairingStartResult = server.start(
        onPaired = { session ->
            _state.value = QrPairingState.Receiving
            viewModelScope.launch {
                _state.value = try {
                    sessionManager.signIn(session)
                    QrPairingState.Success
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    QrPairingState.Failed(
                        message = "Could not save the session.",
                        detail = e.message,
                    )
                }
            }
        },
    )

    override fun onCleared() {
        // The endpoint must not outlive the screen showing its key.
        server.stop()
        super.onCleared()
    }

    private companion object {
        const val START_TIMEOUT_MILLIS = 10_000L
    }
}
