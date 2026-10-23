package io.github.ricox.boostyvp.core.data.repository

import io.github.ricox.boostyvp.core.network.BoostyApi
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

    @Test(expected = IllegalStateException::class)
    fun `repeated subscriptions cursor terminates with an error`() = runTest {
        coEvery { api.getSubscriptions(limit = 100, offset = any()) } returns page(listOf("a"), "same")
        repository.subscriptions(100)
        Unit
    }
}
