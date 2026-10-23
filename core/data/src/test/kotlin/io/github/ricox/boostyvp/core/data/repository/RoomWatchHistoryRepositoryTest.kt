package io.github.ricox.boostyvp.core.data.repository

import io.github.ricox.boostyvp.core.data.library.WatchHistoryDao
import io.github.ricox.boostyvp.core.data.library.WatchHistoryEntity
import io.github.ricox.boostyvp.core.domain.auth.CurrentAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RoomWatchHistoryRepositoryTest {
    private class Dao : WatchHistoryDao {
        val rows = MutableStateFlow<List<WatchHistoryEntity>>(emptyList())
        override suspend fun upsert(entry: WatchHistoryEntity) {
            rows.value = rows.value.filterNot { it.account == entry.account && it.postId == entry.postId && it.videoId == entry.videoId } + entry
        }
        override fun all(account: String) = rows.map { list ->
            list.filter { it.account == account }.sortedByDescending { it.updatedAt }
        }
        override suspend fun deletePost(account: String, postId: String) {
            rows.value = rows.value.filterNot { it.account == account && it.postId == postId }
        }
    }
    private val account = object : CurrentAccount {
        override val key = MutableStateFlow<String?>("a")
        override suspend fun currentKey() = key.value
    }
    private val dao = Dao()
    private val repository = RoomWatchHistoryRepository(dao, account)
    private fun row(user: String, post: String, video: String = "v", at: Long = 1, position: Long = 600_000) =
        WatchHistoryEntity(user, post, video, position, 600_000, at, "blog", "Title", "Author", null)

    @Test fun `history is newest first with one card per post including finished videos`() = runTest {
        dao.upsert(row("a", "old", at = 1))
        dao.upsert(row("a", "new", at = 3))
        dao.upsert(row("a", "old", video = "v2", at = 2))
        val entries = repository.entries().first()
        assertEquals(listOf("new" to "v", "old" to "v2"), entries.map { it.postId to it.videoId })
        assertEquals(1f, entries.first().fraction)
    }

    @Test fun `changing account never exposes another accounts history`() = runTest {
        dao.upsert(row("a", "a-post"))
        dao.upsert(row("b", "b-post"))
        assertEquals(listOf("a-post"), repository.entries().first().map { it.postId })
        account.key.value = "b"
        assertEquals(listOf("b-post"), repository.entries().first().map { it.postId })
        account.key.value = null
        assertTrue(repository.entries().first().isEmpty())
    }

    @Test fun `removal affects only this accounts history for all videos of the post`() = runTest {
        dao.upsert(row("a", "p")); dao.upsert(row("a", "p", "v2")); dao.upsert(row("b", "p"))
        repository.removePost("p")
        assertTrue(repository.entries().first().isEmpty())
        assertEquals(listOf("b"), dao.rows.value.map { it.account })
    }

    @Test fun `unknown account cannot remove stored history`() = runTest {
        dao.upsert(row("a", "p"))
        account.key.value = null
        val failure = runCatching { repository.removePost("p") }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(1, dao.rows.value.size)
    }
}
