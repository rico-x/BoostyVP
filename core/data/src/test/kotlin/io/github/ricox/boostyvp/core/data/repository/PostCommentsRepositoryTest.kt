package io.github.ricox.boostyvp.core.data.repository

import io.github.ricox.boostyvp.core.network.BoostyApi
import io.github.ricox.boostyvp.core.network.dto.CommentDto
import io.github.ricox.boostyvp.core.network.dto.PagedResponse
import io.github.ricox.boostyvp.core.network.dto.PagingExtraDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PostCommentsRepositoryTest {
    private val api = mockk<BoostyApi>()
    private val repository = DefaultPostCommentsRepository(api, Json { ignoreUnknownKeys = true })
    @Test fun `cursor uses raw oldest intId and hidden comments are not rendered`() = runTest {
        coEvery { api.getComments("blog", "post", offset = null, parentId = null) } returns PagedResponse(
            listOf(CommentDto("hidden", 10, isDeleted = true), CommentDto("older", 20), CommentDto("newer", 30)),
            PagingExtraDto(isLast = false))
        val result = repository.page("blog", "post", null, null)
        assertEquals(listOf("newer", "older"), result.items.map { it.id })
        assertEquals("10", result.nextCursor)
        assertFalse(result.isLast)
    }
    @Test fun `replies pass the parent intId and opaque cursor verbatim`() = runTest {
        coEvery { api.getComments("blog", "post", offset = "120:opaque", parentId = 50) } returns
            PagedResponse(listOf(CommentDto("reply", 40)), PagingExtraDto(isLast = true))
        val result = repository.page("blog", "post", "120:opaque", 50)
        assertTrue(result.isLast)
        assertNull(result.nextCursor)
        coVerify { api.getComments("blog", "post", "120:opaque", 50, 20, 0, "top") }
    }
    @Test(expected = IllegalStateException::class) fun `repeated intId cursor fails rather than looping`() = runTest {
        coEvery { api.getComments("blog", "post", offset = "10", parentId = null) } returns
            PagedResponse(listOf(CommentDto("comment", 10)), PagingExtraDto(isLast = false))
        repository.page("blog", "post", "10", null)
        Unit
    }
}
