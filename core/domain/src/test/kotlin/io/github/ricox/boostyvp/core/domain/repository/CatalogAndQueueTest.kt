package io.github.ricox.boostyvp.core.domain.repository

import io.github.ricox.boostyvp.core.model.*
import org.junit.Assert.*
import org.junit.Test

class CatalogAndQueueTest {
    private fun post(id: String, title: String = id, time: Long = 0, video: Boolean = true, access: Boolean = true) =
        Post(id, 0, title, "author", "Автор", time, access, false, false, false, "private-query",
            if (video) listOf(ContentBlock.Video("v$id", null, null, 60, null, null, "https://example.org/cover.jpg", null, null,
                listOf(VideoSource(VideoSource.Kind.Progressive, "https://example.org/private-video", 720, "mp4")), true)) else emptyList(), emptyList())

    @Test fun `catalog is bounded metadata only and rejects stale account results`() {
        val index = CatalogIndex(2)
        index.activate("one"); index.record("one", listOf(post("1"), post("2"), post("3")))
        assertEquals(listOf("2", "3"), index.posts.value.map { it.id })
        assertNull(index.posts.value.first().signedQuery)
        assertTrue(index.posts.value.first().videos.first().sources.isEmpty())
        index.activate("two"); index.record("one", listOf(post("old")))
        assertTrue(index.posts.value.isEmpty())
        index.record("two", listOf(post("locked", access = false)))
        assertFalse(index.posts.value.single().hasAccess)
        index.activate(null); index.record(null, listOf(post("anonymous")))
        assertTrue(index.posts.value.isEmpty())
    }
    @Test fun `search uses all words author scope and stable sorts without inventing global results`() {
        val posts = listOf(post("1", "Первая часть", 10), post("2", "Вторая часть", 20, access = false), post("3", "Текст", 30, video = false))
        assertEquals(listOf("2", "1"), filterCatalog(posts, "часть автор", CatalogSort.Newest, CatalogKind.Video).map { it.id })
        assertEquals(listOf("1"), filterCatalog(posts, "часть", CatalogSort.Newest, CatalogKind.All, true).map { it.id })
        assertEquals(listOf("3"), filterCatalog(posts, "", CatalogSort.Oldest, CatalogKind.Text).map { it.id })
        assertTrue(filterCatalog(posts, "", CatalogSort.Title, CatalogKind.All, blogUrl = "other").isEmpty())
    }
    @Test fun `queue deduplicates reorders and stays independent from autoplay and account`() {
        val queue = PlaybackQueue(); queue.add(SavedPost.from(post("ignored")))
        assertTrue(queue.items.value.isEmpty())
        queue.activate("one")
        listOf("1", "2", "3", "2").forEach { queue.add(SavedPost.from(post(it))) }
        assertEquals(listOf("1", "2", "3"), queue.items.value.map { it.postId })
        queue.move("3", -1); assertEquals("3", queue.nextAfter("1")?.postId)
        queue.remove("1"); assertEquals("3", queue.nextAfter("outside")?.postId)
        assertNull(queue.nextAfter("2"))
        queue.activate("two"); assertTrue(queue.items.value.isEmpty())
    }
    @Test fun `queue rejects text and caps session metadata at one hundred items`() {
        val queue = PlaybackQueue(); queue.activate("one")
        queue.add(SavedPost.from(post("text", video = false)))
        repeat(120) { queue.add(SavedPost.from(post(it.toString()))) }
        assertEquals(100, queue.items.value.size)
        queue.move("0", -1); assertEquals("0", queue.items.value.first().postId)
        queue.clear(); assertTrue(queue.items.value.isEmpty())
    }
    @Test fun `finishing a selected middle or last item does not loop back to skipped entries`() {
        val items = listOf("1", "2", "3").map { SavedPost.from(post(it)) }
        assertEquals(listOf("1", "2"), queueContinuation(items, "2").completed)
        assertEquals("3", queueContinuation(items, "2").next?.postId)
        assertEquals(listOf("1", "2", "3"), queueContinuation(items, "3").completed)
        assertNull(queueContinuation(items, "3").next)
        assertTrue(queueContinuation(items, "outside").completed.isEmpty())
    }
}
