package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.model.Post
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsPostViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val blogs = mockk<BlogRepository>()
    private fun post(id: String) = Post(id, 0, "Video", "author", "Author", 0,
        true, false, false, false, null, emptyList(), emptyList())
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun `loads post once without any video or history dependency`() = runTest(dispatcher) {
        coEvery { blogs.post("author", "video") } returns post("video")
        val model = CommentsPostViewModel(blogs)
        model.open("author", "video"); advanceUntilIdle()
        model.open("author", "video"); advanceUntilIdle()
        assertEquals("video", model.state.value.post?.id)
        coVerify(exactly = 1) { blogs.post(any(), any()) }
        confirmVerified(blogs)
    }

    @Test fun `failure does not expose private URL and can retry`() = runTest(dispatcher) {
        coEvery { blogs.post(any(), any()) } throws IllegalStateException("private?token=secret")
        val model = CommentsPostViewModel(blogs)
        model.open("author", "video"); advanceUntilIdle()
        assertEquals(CommentsPostState(failed = true), model.state.value)
        coEvery { blogs.post(any(), any()) } returns post("video").copy(commentsDenied = true)
        model.open("author", "video", retry = true); advanceUntilIdle()
        assertTrue(model.state.value.post!!.commentsDenied)
    }

    @Test fun `late response or failure cannot replace new post`() = runTest(dispatcher) {
        coEvery { blogs.post("author", "old") } coAnswers {
            withContext(NonCancellable) { delay(100) }
            throw IllegalStateException("stale")
        }
        coEvery { blogs.post("author", "new") } returns post("new")
        val model = CommentsPostViewModel(blogs)
        model.open("author", "old"); runCurrent()
        model.open("author", "new"); advanceUntilIdle()
        assertEquals("new", model.state.value.post?.id)
        assertFalse(model.state.value.failed)
    }
}
