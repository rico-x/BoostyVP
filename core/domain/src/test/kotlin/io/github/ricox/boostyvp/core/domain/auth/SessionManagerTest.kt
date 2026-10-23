package io.github.ricox.boostyvp.core.domain.auth

import io.github.ricox.boostyvp.core.model.Session
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SessionManagerTest {

    private class FakeStore(initial: Session?) : TokenStore {
        private val state = MutableStateFlow(initial)
        override val session: Flow<Session?> = state
        override suspend fun peek(): Session? = state.value
        override suspend fun save(session: Session) { state.value = session }
        override suspend fun clear() { state.value = null }
    }

    private fun session(access: String, refresh: String, expiresAt: Long = 10_000) =
        Session(access, refresh, expiresAt, "device-1")

    @Test
    fun `valid token is returned without refreshing`() = runTest {
        val calls = AtomicInteger()
        val manager = SessionManager(
            store = FakeStore(session("good", "r1", expiresAt = 10_000)),
            refresher = { calls.incrementAndGet(); error("should not be called") },
            now = { 1_000 },
        )

        assertEquals("good", manager.validAccessToken())
        assertEquals(0, calls.get())
    }

    @Test
    fun `expired token triggers exactly one refresh`() = runTest {
        val calls = AtomicInteger()
        val manager = SessionManager(
            store = FakeStore(session("stale", "r1", expiresAt = 500)),
            refresher = { current ->
                calls.incrementAndGet()
                session("fresh", "r2", expiresAt = 10_000).copy(deviceId = current.deviceId)
            },
            now = { 1_000 },
        )

        assertEquals("fresh", manager.validAccessToken())
        assertEquals(1, calls.get())
    }

    /**
     * The regression this whole design exists to prevent. Boosty rotates the
     * refresh token, so a burst of concurrent 401s must produce ONE refresh --
     * otherwise every call after the first sends an already-consumed refresh
     * token and the user is silently signed out.
     */
    @Test
    fun `concurrent refreshes collapse into a single call`() = runTest {
        val calls = AtomicInteger()
        val manager = SessionManager(
            store = FakeStore(session("stale", "r1", expiresAt = 500)),
            refresher = { current ->
                calls.incrementAndGet()
                delay(50) // simulate the round trip
                session("fresh", "r2", expiresAt = 10_000).copy(deviceId = current.deviceId)
            },
            now = { 1_000 },
        )

        val results = (1..12).map {
            async { manager.refreshIfNeeded(staleToken = "stale") }
        }.awaitAll()

        assertEquals(1, calls.get())
        assertTrue(results.all { it.accessToken == "fresh" })
    }

    @Test
    fun `a caller holding an already-replaced token does not refresh again`() = runTest {
        val calls = AtomicInteger()
        val manager = SessionManager(
            store = FakeStore(session("current", "r2", expiresAt = 10_000)),
            refresher = { calls.incrementAndGet(); error("should not be called") },
            now = { 1_000 },
        )

        val result = manager.refreshIfNeeded(staleToken = "an-older-token")

        assertEquals("current", result.accessToken)
        assertEquals(0, calls.get())
    }

    @Test
    fun `a rejected refresh token clears the session`() = runTest {
        val store = FakeStore(session("stale", "dead", expiresAt = 500))
        val manager = SessionManager(
            store = store,
            refresher = { throw AuthException.Unrecoverable("rejected") },
            now = { 1_000 },
        )

        val thrown = runCatching { manager.refreshIfNeeded("stale") }.exceptionOrNull()
        assertTrue(thrown is AuthException.Unrecoverable)
        assertNull(store.peek())
    }

    @Test
    fun `a transient failure leaves the session intact`() = runTest {
        val store = FakeStore(session("stale", "r1", expiresAt = 500))
        val manager = SessionManager(
            store = store,
            refresher = { throw java.io.IOException("network down") },
            now = { 1_000 },
        )

        val thrown = runCatching { manager.refreshIfNeeded("stale") }.exceptionOrNull()
        assertTrue(thrown is java.io.IOException)
        // Critical: a flaky connection must not sign the user out.
        assertEquals("r1", store.peek()?.refreshToken)
    }

    @Test
    fun `no session at all is reported distinctly`() = runTest {
        val manager = SessionManager(
            store = FakeStore(null),
            refresher = { error("should not be called") },
            now = { 1_000 },
        )
        val thrown = runCatching { manager.validAccessToken() }.exceptionOrNull()
        assertTrue(thrown is AuthException.NotAuthenticated)
    }

    @Test
    fun `sign out during refresh cannot restore the old session`() = runTest {
        val store = FakeStore(session("stale", "r1", expiresAt = 500))
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val manager = SessionManager(store, {
            started.complete(Unit)
            finish.await()
            session("fresh", "r2")
        })

        val refresh = async { manager.refreshIfNeeded("stale") }
        started.await()
        val signOut = async { manager.signOut() }
        finish.complete(Unit)
        refresh.await()
        signOut.await()

        assertNull(store.peek())
    }

    @Test
    fun `sign in during refresh keeps the newly selected account`() = runTest {
        val store = FakeStore(session("stale", "r1", expiresAt = 500))
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val manager = SessionManager(store, {
            started.complete(Unit)
            finish.await()
            session("old-account-refreshed", "r2")
        })
        val selected = session("new-account", "new-refresh")

        val refresh = async { manager.refreshIfNeeded("stale") }
        started.await()
        val signIn = async { manager.signIn(selected) }
        finish.complete(Unit)
        refresh.await()
        signIn.await()

        assertEquals(selected, store.peek())
    }
}
