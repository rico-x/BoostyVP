package io.github.ricox.boostyvp.navigation

import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import io.github.ricox.boostyvp.core.domain.auth.WebLoginSessionCleaner
import io.github.ricox.boostyvp.core.model.AuthState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = mockk<SessionManager>()
    private val browser = mockk<WebLoginSessionCleaner>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { session.authState } returns flowOf(AuthState.SignedOut)
        coEvery { session.signOut() } returns Unit
        coEvery { browser.clear() } returns Unit
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `sign out clears the embedded browser and stored session only once`() = runTest(dispatcher) {
        val viewModel = RootViewModel(session, browser)
        coVerify(exactly = 0) { session.signOut() }
        viewModel.signOut()
        viewModel.signOut()
        runCurrent()
        coVerifyOrder { browser.clear(); session.signOut() }
        coVerify(exactly = 1) { session.signOut() }
        assertFalse(viewModel.signOutState.value.inProgress)
    }

    @Test
    fun `failed sign out remains retryable and reports the failure`() = runTest(dispatcher) {
        coEvery { session.signOut() } throws java.io.IOException("storage failure")
        val viewModel = RootViewModel(session, browser)
        viewModel.signOut()
        runCurrent()
        assertNotNull(viewModel.signOutState.value.error)
        assertFalse(viewModel.signOutState.value.inProgress)
        coEvery { session.signOut() } returns Unit
        viewModel.signOut()
        runCurrent()
        coVerify(exactly = 2) { session.signOut() }
    }
}
