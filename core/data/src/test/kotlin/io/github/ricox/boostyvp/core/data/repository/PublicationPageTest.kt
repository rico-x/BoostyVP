package io.github.ricox.boostyvp.core.data.repository

import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.Page
import io.github.ricox.boostyvp.core.model.Post
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicationPageTest {
    private val video = ContentBlock.Video("v", null, null, 60, null, null, null, null, null, emptyList(), false)
    private fun post(id: String, blocks: List<ContentBlock> = emptyList(),
        teaser: List<ContentBlock> = emptyList(), waiting: Boolean = false) = Post(
        id, 0, id, "author", "Author", 0, teaser.isEmpty(), false, waiting, false, null, blocks, teaser)

    @Test fun `text and images remain visible without skipping their page`() = runBlocking {
        var calls = 0
        val result = publicationPage(null) {
            calls++
            Page(listOf(post("text"), post("image", listOf(ContentBlock.Image("i", "url", null, null)))), "opaque", false)
        }
        assertEquals(1, calls)
        assertEquals(listOf("text", "image"), result.items.map { it.id })
        assertEquals("opaque", result.nextCursor)
    }
    @Test fun `locked teasers processing videos and text are retained`() = runBlocking {
        val result = publicationPage(null) {
            Page(listOf(post("text"), post("locked", teaser = listOf(video)), post("processing", waiting = true)), null, true)
        }
        assertEquals(listOf("text", "locked", "processing"), result.items.map { it.id })
    }
    @Test fun `empty filtered page continues using the opaque cursor`() = runBlocking {
        val requested = mutableListOf<String?>()
        val result = publicationPage(null) { cursor ->
            requested += cursor
            if (cursor == null) Page(emptyList(), "opaque-A", false) else Page(listOf(post("text")), null, true)
        }
        assertEquals(listOf(null, "opaque-A"), requested)
        assertEquals("text", result.items.single().id)
    }
    @Test fun `last empty page terminates`() = runBlocking {
        assertTrue(publicationPage(null) { Page(emptyList(), null, true) }.isLast)
    }
    @Test(expected = IllegalStateException::class) fun `repeated cursor is rejected for text too`() = runBlocking {
        publicationPage("same") { Page(listOf(post("text")), "same", false) }
        Unit
    }
}
