package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.domain.repository.PlaybackProgressRepository
import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.VideoSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val blogs = mockk<BlogRepository>()
    private val progress = mockk<PlaybackProgressRepository>(relaxed = true)

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun post(id: String): Post {
        val video = ContentBlock.Video("video-$id", null, null, 60, null, null, null, null, null,
            listOf(VideoSource(VideoSource.Kind.Progressive, "https://cdn.example/$id.mp4", 5, "high")), true)
        return Post(id, 0, id, "blog", "Blog", 0, true, false, false, false, null, listOf(video), emptyList())
    }

    @Test fun `a delayed previous load cannot replace the new video or its progress key`() = runTest(dispatcher) {
        coEvery { blogs.post("blog", "old") } coAnswers {
            withContext(NonCancellable) { delay(100) }
            post("old")
        }
        coEvery { blogs.post("blog", "new") } returns post("new")
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "old")
        runCurrent()
        model.load("blog", "new")
        advanceUntilIdle()
        assertEquals("new", (model.state.value as PlayerUiState.Ready).title)
        model.onProgress(model.state.value as PlayerUiState.Ready, 20_000, 120_000)
        advanceUntilIdle()
        coVerify { progress.save("new", "video-new", 20_000, 120_000) }
        coVerify(exactly = 0) { progress.save("new", "video-old", any(), any()) }
    }

    @Test fun `queued progress keeps the identity of the video that reported it`() = runTest(dispatcher) {
        coEvery { blogs.post("blog", any()) } answers { post(secondArg()) }
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "old")
        advanceUntilIdle()
        val old = model.state.value as PlayerUiState.Ready
        model.onProgress(old, 20_000, 120_000)
        model.onPlaybackEnded(old)
        model.load("blog", "new")
        advanceUntilIdle()
        coVerify { progress.save("old", "video-old", 20_000, 120_000) }
        coVerify { progress.clear("old", "video-old") }
        coVerify(exactly = 0) { progress.save("new", any(), any(), any()) }
    }

    @Test fun `final callbacks from a disposed player retain its old video identity`() = runTest(dispatcher) {
        coEvery { blogs.post("blog", any()) } answers { post(secondArg()) }
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "old")
        advanceUntilIdle()
        val disposed = model.state.value as PlayerUiState.Ready
        model.load("blog", "new")
        advanceUntilIdle()
        model.onProgress(disposed, 25_000, 120_000)
        model.onPlaybackEnded(disposed)
        advanceUntilIdle()
        coVerify { progress.save("old", "video-old", 25_000, 120_000) }
        coVerify { progress.clear("old", "video-old") }
        coVerify(exactly = 0) { progress.save("new", any(), any(), any()) }
        coVerify(exactly = 0) { progress.clear("new", any()) }
    }

    @Test fun `cancelling a load does not produce a failure screen`() = runTest(dispatcher) {
        coEvery { blogs.post("blog", "post") } throws CancellationException()
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "post")
        advanceUntilIdle()
        assertEquals(PlayerUiState.Loading, model.state.value)
    }
}
