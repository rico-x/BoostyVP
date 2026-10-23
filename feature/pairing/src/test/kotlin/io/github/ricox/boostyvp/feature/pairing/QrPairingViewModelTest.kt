package io.github.ricox.boostyvp.feature.pairing

import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import io.github.ricox.boostyvp.core.domain.auth.TokenStore
import io.github.ricox.boostyvp.core.domain.auth.pairing.PairingOffer
import io.github.ricox.boostyvp.core.domain.auth.pairing.PairingService
import io.github.ricox.boostyvp.core.domain.auth.pairing.PairingStartResult
import io.github.ricox.boostyvp.core.model.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class QrPairingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = object : TokenStore {
        override val session = MutableStateFlow<Session?>(null)
        override suspend fun peek() = session.value
        override suspend fun save(session: Session) { this.session.value = session }
        override suspend fun clear() { session.value = null }
    }
    private val sessions = SessionManager(store, { error("No refresh expected") })
    private val ready = PairingStartResult.Ready(PairingOffer("127.0.0.1", 8765, "key"))
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun model(start: suspend ((Session) -> Unit) -> PairingStartResult) =
        QrPairingViewModel(PairingService.Factory {
            object : PairingService {
                override suspend fun start(onPaired: (Session) -> Unit) = start(onPaired)
                override fun stop() = Unit
            }
        }, sessions)

    @Test fun `repeated begin while starting does not open several servers`() = runTest(dispatcher) {
        var starts = 0
        val viewModel = model { starts++; delay(500); ready }
        repeat(3) { viewModel.begin() }
        advanceUntilIdle()
        assertEquals(1, starts)
        assertEquals(QrPairingState.Waiting(ready.offer), viewModel.state.value)
    }

    @Test fun `a fast pairing callback cannot be overwritten by the waiting state`() = runTest(dispatcher) {
        val session = Session("access", "refresh", 10_000, "device")
        val viewModel = model { onPaired -> onPaired(session); ready }
        advanceUntilIdle()
        assertEquals(QrPairingState.Success, viewModel.state.value)
        assertEquals(session, store.peek())
    }

    @Test fun `cancellation while starting does not become a pairing failure`() = runTest(dispatcher) {
        val viewModel = model { throw CancellationException() }
        advanceUntilIdle()
        assertEquals(QrPairingState.Starting, viewModel.state.value)
    }
}
