package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.domain.repository.PlaybackProgressRepository
import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.VideoSource
import io.github.ricox.boostyvp.core.model.WatchProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.MockKMatcherScope
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

    private fun MockKMatcherScope.saved(post: String, video: String, position: Long, duration: Long) =
        match<WatchProgress> {
            it.postId == post && it.videoId == video && it.positionMs == position && it.durationMs == duration
        }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun post(id: String): Post {
        val video = ContentBlock.Video("video-$id", null, null, 60, null, null, null, null, null,
            listOf(VideoSource(VideoSource.Kind.Progressive, "https://cdn.example/$id.mp4", 5, "high")), true)
        return Post(id, 0, id, "blog", "Blog", 0, true, false, false, false, null, listOf(video), emptyList())
    }

    @Test fun `text-only post opens a publication instead of an unplayable error`() = runTest(dispatcher) {
        val article = post("text").copy(blocks = listOf(ContentBlock.Text("Full article", null)))
        coEvery { blogs.post("blog", "text") } returns article
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "text")
        advanceUntilIdle()
        assertEquals(article, (model.state.value as PlayerUiState.Publication).post)
        coVerify(exactly = 0) { progress.resumePositionMs(any(), any()) }
    }

    @Test fun `locked text keeps its access boundary and teaser`() = runTest(dispatcher) {
        val article = post("locked").copy(hasAccess = false, blocks = emptyList(),
            teaser = listOf(ContentBlock.Text("Preview only", null)))
        coEvery { blogs.post("blog", "locked") } returns article
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "locked")
        advanceUntilIdle()
        assertEquals(article, (model.state.value as PlayerUiState.Publication).post)
    }

    @Test fun `external embed without playable URLs still opens the publication text`() = runTest(dispatcher) {
        val original = post("embed")
        val article = original.copy(blocks = listOf(ContentBlock.Text("Readable announcement", null),
            (original.blocks.single() as ContentBlock.Video).copy(sources = emptyList())))
        coEvery { blogs.post("blog", "embed") } returns article
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "embed")
        advanceUntilIdle()
        assertEquals(article, (model.state.value as PlayerUiState.Publication).post)
    }

    @Test fun `checkpoints carry what the continue-watching card needs`() = runTest(dispatcher) {
        val base = post("card")
        val video = (base.blocks.single() as ContentBlock.Video).copy(previewUrl = "https://img.example/card.jpg")
        coEvery { blogs.post("blog", "card") } returns base.copy(title = "Card title", blocks = listOf(video))
        val model = PlayerViewModel(blogs, progress) { 1_700_000_000_000 }
        model.load("blog", "card")
        advanceUntilIdle()
        model.onProgress(model.state.value as PlayerUiState.Ready, 40_000, 120_000)
        advanceUntilIdle()
        coVerify {
            progress.save(WatchProgress("card", "video-card", 40_000, 120_000, 1_700_000_000_000,
                blogUrl = "blog", title = "Card title", blogTitle = "Blog", previewUrl = "https://img.example/card.jpg"))
        }
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
        coVerify { progress.save(saved("new", "video-new", 20_000, 120_000)) }
        coVerify(exactly = 0) { progress.save(match { it.postId == "new" && it.videoId == "video-old" }) }
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
        coVerify { progress.save(saved("old", "video-old", 20_000, 120_000)) }
        coVerify { progress.clear("old", "video-old") }
        coVerify(exactly = 0) { progress.save(match { it.postId == "new" }) }
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
        coVerify { progress.save(saved("old", "video-old", 25_000, 120_000)) }
        coVerify { progress.clear("old", "video-old") }
        coVerify(exactly = 0) { progress.save(match { it.postId == "new" }) }
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
