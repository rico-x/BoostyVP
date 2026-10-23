package io.github.ricox.boostyvp.core.data.repository

import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.Page
import io.github.ricox.boostyvp.core.model.Post
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPostPageTest {
    private val video = ContentBlock.Video(
        "video", null, null, 60, null, null, null, null, null, emptyList(), false,
    )
    private fun post(id: String, blocks: List<ContentBlock> = emptyList(),
        teaser: List<ContentBlock> = emptyList(), waiting: Boolean = false) = Post(
        id, 0, id, "author", "Author", 0, teaser.isEmpty(), false, waiting, false, null, blocks, teaser,
    )

    @Test
    fun `text-only pages are skipped and the actual continuation cursor is preserved`() = runBlocking {
        val requested = mutableListOf<String?>()
        val result = videoPostPage(null) { cursor ->
            requested += cursor
            when (cursor) {
                null -> Page(listOf(post("text")), "opaque/page-A", false)
                "opaque/page-A" -> Page(listOf(post("image", listOf(ContentBlock.Image("i", "url", null, null)))), "opaque/page-B", false)
                else -> Page(listOf(post("notice"), post("video", listOf(video))), "opaque/page-C", false)
            }
        }
        assertEquals(listOf(null, "opaque/page-A", "opaque/page-B"), requested)
        assertEquals(listOf("video"), result.items.map { it.id })
        assertEquals("opaque/page-C", result.nextCursor)
    }

    @Test
    fun `locked video teasers and videos still processing remain visible`() = runBlocking {
        val result = videoPostPage(null) {
            Page(listOf(post("text"), post("locked", teaser = listOf(video)), post("processing", waiting = true)), null, true)
        }
        assertEquals(listOf("locked", "processing"), result.items.map { it.id })
    }

    @Test
    fun `channel with no videos terminates at the last page`() = runBlocking {
        val result = videoPostPage("last-page") { Page(listOf(post("text")), null, true) }
        assertTrue(result.items.isEmpty())
        assertTrue(result.isLast)
    }

    @Test(expected = IllegalStateException::class)
    fun `repeated cursor on a page containing videos is rejected`() = runBlocking {
        videoPostPage("same") { Page(listOf(post("video", listOf(video))), "same", false) }
        Unit
    }

    @Test(expected = IllegalStateException::class)
    fun `repeated cursor cannot create an infinite request loop`() = runBlocking {
        videoPostPage("same") { Page(listOf(post("text")), "same", false) }
        Unit
    }
}
