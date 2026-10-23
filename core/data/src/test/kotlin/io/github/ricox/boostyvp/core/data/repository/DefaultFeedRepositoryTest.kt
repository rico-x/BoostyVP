package io.github.ricox.boostyvp.core.data.repository

import io.github.ricox.boostyvp.core.network.BoostyApi
import io.github.ricox.boostyvp.core.network.dto.FeedResponseDto
import io.github.ricox.boostyvp.core.network.dto.PagedResponse
import io.github.ricox.boostyvp.core.network.dto.UserSubscriptionDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultFeedRepositoryTest {
    private val api = mockk<BoostyApi>()
    private val json = Json { ignoreUnknownKeys = true }
    private val repository = DefaultFeedRepository(api, json)
    private fun page(slugs: List<String>, cursor: String?, last: Boolean = false) =
        json.decodeFromString<PagedResponse<UserSubscriptionDto>>("""
            {"data":[${slugs.joinToString { """{"blog":{"blogUrl":"$it"}}""" }}],
             "extra":{"offset":${cursor?.let { "\"$it\"" } ?: "null"},"isLast":$last}}
        """.trimIndent())

    @Test fun `subscriptions follow opaque cursors and deduplicate authors`() = runTest {
        coEvery { api.getSubscriptions(limit = 100, offset = null) } returns page(listOf("a"), "opaque:20")
        coEvery { api.getSubscriptions(limit = 100, offset = "opaque:20") } returns page(listOf("a", "b"), null, true)
        assertEquals(listOf("a", "b"), repository.subscriptions(100).map { it.blog.url })
        coVerify(exactly = 2) { api.getSubscriptions(any(), any(), any()) }
    }

    // The feed's real envelope: posts sit beside other blocks inside `data`.
    private fun feed(posts: String, cursor: String?, last: Boolean) =
        json.decodeFromString<FeedResponseDto>("""
            {"extra":{"offset":${cursor?.let { "\"$it\"" } ?: "null"},"isLast":$last},
             "data":{"blogs":[{"blogUrl":"alpha","subscriptionKind":"paid"}],
                     "counters":{"newNotAllowedPosts":0},"posts":[$posts]}}
        """.trimIndent())

    @Test fun `feed takes each post's blog from its author and drops posts that cannot be opened`() = runTest {
        coEvery { api.getFeed(offset = null) } returns feed(
            """{"id":"a","title":"A","user":{"blogUrl":"alpha","name":"Alpha"}},
               {"id":"orphan","title":"No author"},
               {"id":"b","title":"B","user":{"blogUrl":"beta","name":"Beta"}}""",
            cursor = "opaque:2", last = false,
        )
        val page = repository.feedPage(null)
        assertEquals(listOf(Triple("a", "alpha", "Alpha"), Triple("b", "beta", "Beta")),
            page.items.map { Triple(it.id, it.blogUrl, it.blogTitle) })
        assertEquals("opaque:2", page.nextCursor)
    }

    @Test fun `feed page with only unopenable posts continues to the next cursor`() = runTest {
        coEvery { api.getFeed(offset = null) } returns feed("""{"id":"orphan"}""", "opaque:1", last = false)
        coEvery { api.getFeed(offset = "opaque:1") } returns feed(
            """{"id":"c","user":{"blogUrl":"gamma"}}""", cursor = null, last = true,
        )
        assertEquals(listOf("c"), repository.feedPage(null).items.map { it.id })
    }

    @Test(expected = IllegalStateException::class)
    fun `repeated subscriptions cursor terminates with an error`() = runTest {
        coEvery { api.getSubscriptions(limit = 100, offset = any()) } returns page(listOf("a"), "same")
        repository.subscriptions(100)
        Unit
    }
}
