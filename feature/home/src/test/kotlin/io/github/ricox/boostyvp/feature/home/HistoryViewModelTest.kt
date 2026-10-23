package io.github.ricox.boostyvp.feature.home

import io.github.ricox.boostyvp.core.domain.repository.WatchHistoryRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<WatchHistoryRepository>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun `history is collected only when subscribed and failures can be retried`() = runTest(dispatcher) {
        var fail = true
        every { repository.entries() } answers { flow {
            if (fail) error("Database busy") else emit(emptyList())
        } }
        val model = HistoryViewModel(repository)
        verify(exactly = 0) { repository.entries() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        runCurrent()
        assertEquals(HistoryUiState.Failed, model.state.value)
        fail = false
        model.retry()
        runCurrent()
        assertEquals(HistoryUiState.Content(emptyList()), model.state.value)
    }

    @Test fun `removal reports its result without clearing the rest of the list`() = runTest(dispatcher) {
        coEvery { repository.removePost("one") } just Runs
        coEvery { repository.removePost("two") } throws IllegalStateException()
        val model = HistoryViewModel(repository)
        assertTrue(model.remove("one"))
        assertFalse(model.remove("two"))
        coVerify(exactly = 1) { repository.removePost("one") }
        coVerify(exactly = 1) { repository.removePost("two") }
    }

    @Test fun `cancellation is never presented as a failed removal`() = runTest(dispatcher) {
        coEvery { repository.removePost(any()) } throws CancellationException()
        val failure = runCatching { HistoryViewModel(repository).remove("one") }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }
}
