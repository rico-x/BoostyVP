package io.github.ricox.boostyvp.feature.pairing

import io.github.ricox.boostyvp.core.domain.auth.PhoneChallenge
import io.github.ricox.boostyvp.core.domain.auth.PhoneLogin
import io.github.ricox.boostyvp.core.domain.auth.PhoneLoginException
import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import io.github.ricox.boostyvp.core.domain.auth.TokenStore
import io.github.ricox.boostyvp.core.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneLoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = object : TokenStore {
        override val session = MutableStateFlow<Session?>(null)
        override suspend fun peek() = session.value
        override suspend fun save(session: Session) { this.session.value = session }
        override suspend fun clear() { session.value = null }
    }
    private val sessions = SessionManager(store, { error("No refresh in a login test") })
    private val issuedSession = Session("test-access", "test-refresh", 10_000, "test-device")
    private val sent = mutableListOf<Pair<String, String>>()
    private val confirmed = mutableListOf<List<String>>()
    private var rejectCode = false
    private val login = object : PhoneLogin {
        override suspend fun requestCode(phone: String, deviceId: String): PhoneChallenge {
            sent += phone to deviceId
            return PhoneChallenge("challenge-${sent.size}")
        }
        override suspend fun confirmCode(phone: String, challenge: String, smsCode: String, deviceId: String): Session {
            confirmed += listOf(phone, challenge, smsCode, deviceId)
            if (rejectCode) throw PhoneLoginException("Неверный код")
            return issuedSession.copy(deviceId = deviceId)
        }
    }

    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    @Test fun `phone and code produce a durable session with the same device id`() = runTest(dispatcher) {
        val model = PhoneLoginViewModel(login, sessions)
        assertFalse(model.state.value.canSubmit)
        model.changePhone("8 (999) 000-00-00")
        model.submit()
        model.submit()
        runCurrent()
        assertEquals(1, sent.size)
        assertEquals("+79990000000", sent.single().first)
        assertTrue(model.state.value.codeRequested)
        assertNull(store.peek())
        model.changeCode("12x34567")
        model.submit()
        runCurrent()
        assertEquals(listOf("+79990000000", "challenge-1", "123456", sent.single().second), confirmed.single())
        assertEquals(issuedSession.copy(deviceId = sent.single().second), store.peek())
        assertTrue(model.state.value.success)
        assertEquals("", model.state.value.code)
        model.submit()
        runCurrent()
        assertEquals(1, confirmed.size)
    }

    @Test fun `changing phone invalidates the previous challenge and code`() = runTest(dispatcher) {
        val model = PhoneLoginViewModel(login, sessions)
        model.changePhone("+79990000000"); model.submit(); runCurrent()
        model.changeCode("123456")
        model.changePhone("+79990000001")
        assertFalse(model.state.value.codeRequested)
        assertEquals("", model.state.value.code)
        model.submit(); runCurrent()
        assertEquals("+79990000001", sent.last().first)
        assertEquals(0, confirmed.size)
    }

    @Test fun `wrong code stays retryable and cannot save a session`() = runTest(dispatcher) {
        val model = PhoneLoginViewModel(login, sessions)
        model.changePhone("+79990000000"); model.submit(); runCurrent()
        rejectCode = true
        model.changeCode("123456"); model.submit(); runCurrent()
        assertNull(store.peek())
        assertEquals("Неверный код", model.state.value.error)
        assertFalse(model.state.value.busy)
        rejectCode = false
        model.changeCode("654321"); model.submit(); runCurrent()
        assertTrue(model.state.value.success)
    }

    @Test fun `empty code permits a resend only after the cooldown`() = runTest(dispatcher) {
        val model = PhoneLoginViewModel(login, sessions)
        model.changePhone("+79990000000"); model.submit(); runCurrent()
        model.submit(); runCurrent()
        assertEquals(1, sent.size)
        advanceTimeBy(120_001); runCurrent()
        assertTrue(model.state.value.canSubmit)
        model.submit(); runCurrent()
        assertEquals(2, sent.size)
    }

    @Test fun `international prefix is preserved and incomplete numbers are rejected`() {
        assertNull(normalizePhone("+7"))
        assertEquals("+441234567890", normalizePhone("+44 1234 567890"))
        assertEquals("+80012345678", normalizePhone("+80012345678"))
    }
}
