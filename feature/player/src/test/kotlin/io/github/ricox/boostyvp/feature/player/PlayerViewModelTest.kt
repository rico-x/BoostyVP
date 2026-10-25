package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.domain.repository.PlaybackProgressRepository
import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.Page
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

    @Test fun `refresh fetches the post again and continues where it stopped`() = runTest(dispatcher) {
        coEvery { blogs.post("blog", "a") } answers { post("a") }
        val model = PlayerViewModel(blogs, progress) { 0 }
        model.load("blog", "a")
        advanceUntilIdle()
        assertEquals(0L, (model.state.value as PlayerUiState.Ready).startPositionMs)

        assertEquals(true, model.refresh(95_000))
        advanceUntilIdle()
        assertEquals(95_000L, (model.state.value as PlayerUiState.Ready).startPositionMs)
        coVerify(exactly = 2) { blogs.post("blog", "a") }
    }

    @Test fun `a video watched to the end elsewhere starts from the top, a recovery does not`() = runTest(dispatcher) {
        val watched = post("a").let { post ->
            val video = post.videos.single().copy(duration = 35, timeCode = 35)
            post.copy(blocks = listOf(video))
        }
        coEvery { blogs.post("blog", "a") } returns watched
        val model = PlayerViewModel(blogs, progress) { 0 }
        model.load("blog", "a")
        advanceUntilIdle()
        assertEquals(0L, (model.state.value as PlayerUiState.Ready).startPositionMs)

        model.refresh(34_000, force = true)
        advanceUntilIdle()
        assertEquals(34_000L, (model.state.value as PlayerUiState.Ready).startPositionMs)
    }

    @Test fun `automatic refresh is rate-limited but the viewer's retry is not`() = runTest(dispatcher) {
        coEvery { blogs.post("blog", "a") } answers { post("a") }
        var now = 0L
        val model = PlayerViewModel(blogs, progress) { now }
        model.load("blog", "a")
        advanceUntilIdle()
        assertEquals(true, model.refresh(10_000))
        now = 60_000
        assertEquals("A second automatic refresh within two minutes", false, model.refresh(20_000))
        assertEquals("The retry button always refreshes", true, model.refresh(20_000, force = true))
        now = 300_000
        assertEquals(true, model.refresh(30_000))
    }

    @Test fun `next video is the following playable post, across pages`() = runTest(dispatcher) {
        val text = post("text").copy(blocks = listOf(ContentBlock.Text("Article", null)))
        val locked = post("locked").copy(hasAccess = false)
        coEvery { blogs.postsPage("blog", null) } returns Page(listOf(post("first"), post("a"), text, locked), "c1", false)
        coEvery { blogs.postsPage("blog", "c1") } returns Page(listOf(post("b"), post("c")), null, true)
        coEvery { blogs.post("blog", any()) } answers { post(secondArg()) }
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "a")
        advanceUntilIdle()
        val next = model.nextVideo(model.state.value as PlayerUiState.Ready)
        assertEquals("b", next?.id)

        model.playNext(next!!)
        advanceUntilIdle()
        assertEquals("b", (model.state.value as PlayerUiState.Ready).postId)
        model.retry()
        advanceUntilIdle()
        coVerify(exactly = 2) { blogs.post("blog", "b") }
    }

    @Test fun `no next video at the end of the author's list`() = runTest(dispatcher) {
        coEvery { blogs.postsPage("blog", null) } returns Page(listOf(post("first"), post("a")), null, true)
        coEvery { blogs.post("blog", "a") } returns post("a")
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "a")
        advanceUntilIdle()
        assertEquals(null, model.nextVideo(model.state.value as PlayerUiState.Ready))
    }

    @Test fun `watch from start overrides a resume point and survives a failed request`() = runTest(dispatcher) {
        var attempts = 0
        coEvery { blogs.post("blog", "a") } answers {
            if (attempts++ == 0) error("Offline") else post("a")
        }
        coEvery { progress.resumePositionMs("a", "video-a") } returns 40_000L
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "a", resumeAtMs = 0)
        advanceUntilIdle()
        assertEquals(true, model.state.value is PlayerUiState.Failed)
        model.retry()
        advanceUntilIdle()
        assertEquals(0L, (model.state.value as PlayerUiState.Ready).startPositionMs)
        coVerify(exactly = 0) { progress.clear(any(), any()) }
    }

    @Test fun `previous video crosses pages and skips text locked and processing posts`() = runTest(dispatcher) {
        val text = post("text").copy(blocks = listOf(ContentBlock.Text("Article", null)))
        coEvery { blogs.postsPage("blog", null) } returns Page(listOf(post("first"), post("previous"), text), "p2", false)
        coEvery { blogs.postsPage("blog", "p2") } returns Page(listOf(post("locked").copy(hasAccess = false),
            post("processing").copy(isWaitingVideo = true), post("current")), null, true)
        coEvery { blogs.post("blog", "current") } returns post("current")
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "current"); advanceUntilIdle()
        val result = model.adjacentVideo(model.state.value as PlayerUiState.Ready, VideoDirection.Previous)
        assertEquals("previous", (result as AdjacentVideoResult.Found).post.id)
    }

    @Test fun `first video has a boundary not a network error`() = runTest(dispatcher) {
        coEvery { blogs.postsPage("blog", null) } returns Page(listOf(post("current"), post("next")), null, true)
        coEvery { blogs.post("blog", "current") } returns post("current")
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "current"); advanceUntilIdle()
        assertEquals(AdjacentVideoResult.Boundary,
            model.adjacentVideo(model.state.value as PlayerUiState.Ready, VideoDirection.Previous))
    }

    @Test fun `navigation errors and missing current post do not masquerade as a boundary`() = runTest(dispatcher) {
        coEvery { blogs.post("blog", "current") } returns post("current")
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "current"); advanceUntilIdle()
        val ready = model.state.value as PlayerUiState.Ready
        coEvery { blogs.postsPage("blog", null) } throws java.io.IOException("offline")
        assertEquals(AdjacentVideoResult.Unavailable, model.adjacentVideo(ready, VideoDirection.Next))
        coEvery { blogs.postsPage("blog", null) } returns Page(listOf(post("other")), null, true)
        assertEquals(AdjacentVideoResult.Unavailable, model.adjacentVideo(ready, VideoDirection.Previous))
        assertEquals(ready, model.state.value)
    }

    @Test fun `repeated cursor stops navigation and cancellation propagates`() = runTest(dispatcher) {
        coEvery { blogs.post("blog", "current") } returns post("current")
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "current"); advanceUntilIdle()
        val ready = model.state.value as PlayerUiState.Ready
        coEvery { blogs.postsPage("blog", any()) } returns Page(listOf(post("other")), "same", false)
        assertEquals(AdjacentVideoResult.Unavailable, model.adjacentVideo(ready, VideoDirection.Next))
        coVerify(exactly = 2) { blogs.postsPage("blog", any()) }
        coEvery { blogs.postsPage("blog", null) } throws CancellationException()
        assertEquals(true, runCatching { model.adjacentVideo(ready, VideoDirection.Previous) }.exceptionOrNull() is CancellationException)
    }
}
