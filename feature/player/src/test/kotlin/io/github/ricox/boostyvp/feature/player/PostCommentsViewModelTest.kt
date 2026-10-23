package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.core.domain.repository.PostCommentsRepository
import io.github.ricox.boostyvp.core.model.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PostCommentsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<PostCommentsRepository>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun post(id: String = "post") = Post(id, 0, "Article", "blog", "Blog", 0,
        true, false, false, false, null, emptyList(), emptyList(), 3)
    private fun comment(id: String, intId: Long = 20, replies: Int = 0) =
        PostComment(id, intId, "Author", 0, listOf(ContentBlock.Text(id, null)), replies)

    @Test fun `binding and reading do not fetch collapsed comments`() = runTest(dispatcher) {
        val model = PostCommentsViewModel(repository)
        model.bind(post())
        advanceUntilIdle()
        assertFalse(model.state.value.expanded)
        coVerify(exactly = 0) { repository.page(any(), any(), any(), any()) }
    }
    @Test fun `expansion loads once and reopening uses cached comments`() = runTest(dispatcher) {
        coEvery { repository.page("blog", "post", null, null) } returns Page(listOf(comment("one")), null, true)
        val model = PostCommentsViewModel(repository)
        model.bind(post()); model.toggle(); advanceUntilIdle()
        assertEquals("one", model.state.value.root.items.single().id)
        model.toggle(); model.toggle(); advanceUntilIdle()
        coVerify(exactly = 1) { repository.page(any(), any(), any(), any()) }
    }
    @Test fun `pagination deduplicates and replies have independent cursors`() = runTest(dispatcher) {
        coEvery { repository.page("blog", "post", null, null) } returns Page(listOf(comment("root", replies = 2)), "20", false)
        coEvery { repository.page("blog", "post", "20", null) } returns Page(listOf(comment("root"), comment("older", 10)), null, true)
        coEvery { repository.page("blog", "post", null, 20) } returns Page(listOf(comment("reply", 15)), "15", false)
        coEvery { repository.page("blog", "post", "15", 20) } returns Page(listOf(comment("reply2", 12)), null, true)
        val model = PostCommentsViewModel(repository)
        model.bind(post()); model.toggle(); advanceUntilIdle()
        model.toggleReplies(20); advanceUntilIdle()
        model.loadMore(20); model.loadMore(); advanceUntilIdle()
        assertEquals(listOf("root", "older"), model.state.value.root.items.map { it.id })
        assertEquals(listOf("reply", "reply2"), model.state.value.replies.getValue(20).items.map { it.id })
    }
    @Test fun `locked posts and disabled comments make no requests`() = runTest(dispatcher) {
        val model = PostCommentsViewModel(repository)
        model.bind(post("locked").copy(hasAccess = false)); model.toggle(); model.loadMore()
        model.bind(post("disabled").copy(commentsDenied = true)); model.toggle(); model.loadMore()
        advanceUntilIdle()
        assertFalse(model.state.value.expanded)
        coVerify(exactly = 0) { repository.page(any(), any(), any(), any()) }
    }
    @Test fun `failed request is retryable without exposing an exception URL`() = runTest(dispatcher) {
        coEvery { repository.page("blog", "post", null, null) } throws IllegalStateException("https://private/?token=secret")
        val model = PostCommentsViewModel(repository)
        model.bind(post()); model.toggle(); advanceUntilIdle()
        assertFalse(model.state.value.root.error.orEmpty().contains("secret"))
        coEvery { repository.page("blog", "post", null, null) } returns Page(emptyList(), null, true)
        model.loadMore(); advanceUntilIdle()
        assertTrue(model.state.value.root.loaded)
        assertNull(model.state.value.root.error)
    }
    @Test fun `late previous post response cannot populate a new article`() = runTest(dispatcher) {
        coEvery { repository.page("blog", "old", null, null) } coAnswers {
            withContext(NonCancellable) { delay(100) }
            Page(listOf(comment("private-old")), null, true)
        }
        val model = PostCommentsViewModel(repository)
        model.bind(post("old")); model.toggle(); runCurrent()
        model.bind(post("new")); advanceUntilIdle()
        assertEquals(PostCommentsState(), model.state.value)
    }
    @Test fun `revoked access clears previously loaded comments for the same post`() = runTest(dispatcher) {
        coEvery { repository.page("blog", "post", null, null) } returns Page(listOf(comment("private")), null, true)
        val model = PostCommentsViewModel(repository)
        model.bind(post()); model.toggle(); advanceUntilIdle()
        model.bind(post().copy(hasAccess = false))
        assertEquals(PostCommentsState(), model.state.value)
    }
    @Test fun `repeated next cursor stops pagination and keeps loaded content`() = runTest(dispatcher) {
        coEvery { repository.page("blog", "post", any(), null) } returns Page(listOf(comment("root")), "same", false)
        val model = PostCommentsViewModel(repository)
        model.bind(post()); model.toggle(); advanceUntilIdle()
        model.loadMore(); advanceUntilIdle()
        assertEquals(1, model.state.value.root.items.size)
        assertNotNull(model.state.value.root.error)
    }
}
