package io.github.ricox.boostyvp.core.network

import io.github.ricox.boostyvp.core.network.dto.PagedResponse
import io.github.ricox.boostyvp.core.network.dto.PostDto
import io.github.ricox.boostyvp.core.network.mapper.toPostPage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagingCursorTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `opaque cursor is carried through untouched`() {
        val raw = """
            {"data":[{"id":"a"}],"extra":{"offset":"1755500000:123456","isLast":false}}
        """.trimIndent()
        val page = json.decodeFromString<PagedResponse<PostDto>>(raw).toPostPage(json)
        assertEquals("1755500000:123456", page.nextCursor)
        assertEquals(false, page.isLast)
    }

    @Test
    fun `last page reports no cursor`() {
        val raw = """{"data":[{"id":"a"}],"extra":{"offset":"1755500000:1","isLast":true}}"""
        val page = json.decodeFromString<PagedResponse<PostDto>>(raw).toPostPage(json)
        assertNull(page.nextCursor)
        assertTrue(page.isLast)
    }

    @Test
    fun `missing extra block is treated as the last page`() {
        val page = json.decodeFromString<PagedResponse<PostDto>>("""{"data":[]}""").toPostPage(json)
        assertTrue(page.isLast)
        assertNull(page.nextCursor)
    }

    @Test
    fun `deleted and blocked posts are filtered out`() {
        val raw = """
            {"data":[
              {"id":"a"},
              {"id":"b","isDeleted":true},
              {"id":"c","isBlocked":true},
              {"id":"d","isPublished":false}
            ],"extra":{"isLast":true}}
        """.trimIndent()
        val page = json.decodeFromString<PagedResponse<PostDto>>(raw).toPostPage(json)
        assertEquals(listOf("a"), page.items.map { it.id })
    }
}
