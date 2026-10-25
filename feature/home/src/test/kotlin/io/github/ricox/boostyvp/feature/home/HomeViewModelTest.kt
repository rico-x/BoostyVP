package io.github.ricox.boostyvp.feature.home

import io.github.ricox.boostyvp.core.domain.repository.PlaybackProgressRepository
import io.github.ricox.boostyvp.core.domain.usecase.*
import io.github.ricox.boostyvp.core.model.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val loader = mockk<GetHomeFeed>()
    private val progress = mockk<PlaybackProgressRepository>()
    private val blog = Blog("author", "author", "Автор", null, null, 0, 0, true, false, null)
    private fun row(title: String, failed: Boolean = false) = BlogPosts(blog,
        if (failed) emptyList() else listOf(SavedPost("id", "author", title, "Автор", 1, null, true, "v", 60).toPost()), failed = failed)
    @Before fun setup() { Dispatchers.setMain(dispatcher); every { progress.continueWatching(any()) } returns flowOf(emptyList()) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun `a failed refresh retains previously loaded cards and offers retry`() = runTest(dispatcher) {
        coEvery { loader.invoke(any()) } returns HomeFeed(true, listOf(row("Кеш")))
        val model = HomeViewModel(loader, progress); runCurrent()
        coEvery { loader.invoke(any()) } throws IOException()
        model.refresh(); runCurrent()
        val content = model.state.value as HomeUiState.Content
        assertEquals("Кеш", content.rows.single().posts.single().title)
        assertTrue(content.refreshFailed)
    }
    @Test fun `named author retry is isolated and is cancelled by a newer refresh`() = runTest(dispatcher) {
        coEvery { loader.invoke(any()) } returns HomeFeed(true, listOf(row("", failed = true)))
        coEvery { loader.author(blog) } coAnswers { delay(1_000); row("Устаревший ответ") }
        val model = HomeViewModel(loader, progress); runCurrent()
        model.retryAuthor("author"); runCurrent()
        assertTrue((model.state.value as HomeUiState.Content).rows.single().loading)
        coEvery { loader.invoke(any()) } returns HomeFeed(true, listOf(row("Свежий ответ")))
        model.refresh(); advanceUntilIdle()
        assertEquals("Свежий ответ", (model.state.value as HomeUiState.Content).rows.single().posts.single().title)
        coVerify(exactly = 1) { loader.author(blog) }
    }
    @Test fun `incremental rows become visible without waiting for the final result`() = runTest(dispatcher) {
        coEvery { loader.invoke(any()) } coAnswers {
            val callback = firstArg<(HomeFeed) -> Unit>()
            callback(HomeFeed(true, listOf(row("Первая карточка")), refreshing = true))
            delay(1_000)
            HomeFeed(true, listOf(row("Готово")))
        }
        val model = HomeViewModel(loader, progress); runCurrent()
        assertEquals("Первая карточка", (model.state.value as HomeUiState.Content).rows.single().posts.single().title)
        assertTrue((model.state.value as HomeUiState.Content).refreshing)
        advanceUntilIdle()
        assertFalse((model.state.value as HomeUiState.Content).refreshing)
    }
}
