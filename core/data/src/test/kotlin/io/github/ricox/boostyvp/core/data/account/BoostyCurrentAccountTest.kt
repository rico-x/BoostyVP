package io.github.ricox.boostyvp.core.data.account

import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import io.github.ricox.boostyvp.core.domain.auth.TokenStore
import io.github.ricox.boostyvp.core.model.Session
import io.github.ricox.boostyvp.core.network.BoostyApi
import io.github.ricox.boostyvp.core.network.dto.CurrentUserDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BoostyCurrentAccountTest {
    private class FakeStore(initial: Session?) : TokenStore {
        val state = MutableStateFlow(initial)
        override val session: Flow<Session?> = state
        override suspend fun peek(): Session? = state.value
        override suspend fun save(session: Session) { state.value = session }
        override suspend fun clear() { state.value = null }
    }

    private class FakeCache(var id: Long? = null) : AccountIdCache {
        override fun read() = id
        override fun write(id: Long) { this.id = id }
        override fun clear() { id = null }
    }

    private val api = mockk<BoostyApi>()
    private val signedIn = Session("access", "refresh", 10_000, "device")

    private fun TestScope.account(store: FakeStore, cache: FakeCache) = BoostyCurrentAccount(
        api, SessionManager(store, { it }), cache, backgroundScope, retryDelayMs = { 1_000 },
    )

    @Test fun `the account is identified once and then served from cache`() = runTest {
        coEvery { api.getCurrentUser() } returns CurrentUserDto(id = 42)
        val cache = FakeCache()
        val account = account(FakeStore(signedIn), cache)
        runCurrent()
        assertEquals("boosty:42", account.key.value)
        assertEquals(42L, cache.id)

        val later = account(FakeStore(signedIn), cache)
        runCurrent()
        assertEquals("boosty:42", later.currentKey())
        coVerify(exactly = 1) { api.getCurrentUser() }
    }

    @Test fun `offline sign-in keeps retrying until Boosty answers`() = runTest {
        var calls = 0
        coEvery { api.getCurrentUser() } coAnswers {
            if (++calls < 3) throw IOException("offline") else CurrentUserDto(id = 7)
        }
        val account = account(FakeStore(signedIn), FakeCache())
        runCurrent()
        assertNull(account.key.value)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals("boosty:7", account.key.value)
        assertEquals(3, calls)
    }

    @Test fun `signing out forgets the account and the next one is asked again`() = runTest {
        coEvery { api.getCurrentUser() } returnsMany listOf(CurrentUserDto(id = 1), CurrentUserDto(id = 2))
        val store = FakeStore(signedIn)
        val cache = FakeCache()
        val account = account(store, cache)
        runCurrent()
        assertEquals("boosty:1", account.key.value)

        store.state.value = null
        runCurrent()
        assertNull(account.key.value)
        assertNull(cache.id)

        store.state.value = signedIn.copy(accessToken = "other")
        runCurrent()
        assertEquals("boosty:2", account.key.value)
    }

    @Test fun `no key while signed out even with a stale cache`() = runTest {
        val account = account(FakeStore(null), FakeCache(id = 5))
        runCurrent()
        assertNull(account.key.value)
        assertNull(account.currentKey())
    }
}
