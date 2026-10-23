package io.github.ricox.boostyvp.core.data.repository

import io.github.ricox.boostyvp.core.data.library.PlaybackProgressDao
import io.github.ricox.boostyvp.core.data.library.PlaybackProgressEntity
import io.github.ricox.boostyvp.core.domain.auth.CurrentAccount
import io.github.ricox.boostyvp.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomPlaybackProgressRepositoryTest {
    /** In-memory stand-in with the DAO's query semantics; the SQL itself is tested on device. */
    private class FakeDao : PlaybackProgressDao {
        val rows = MutableStateFlow<Map<Triple<String, String, String>, PlaybackProgressEntity>>(emptyMap())
        private fun PlaybackProgressEntity.id() = Triple(account, postId, videoId)
        override suspend fun upsert(entry: PlaybackProgressEntity) { rows.value = rows.value + (entry.id() to entry) }
        override suspend fun insertIfAbsent(entries: List<PlaybackProgressEntity>) {
            rows.value = entries.filter { it.id() !in rows.value }.associateBy { it.id() } + rows.value
        }
        override suspend fun position(account: String, postId: String, videoId: String) =
            rows.value[Triple(account, postId, videoId)]?.positionMs
        override suspend fun delete(account: String, postId: String, videoId: String) {
            rows.value = rows.value - Triple(account, postId, videoId)
        }
        override fun withCards(account: String): Flow<List<PlaybackProgressEntity>> =
            all(account).map { list -> list.filter { it.blogUrl.isNotEmpty() } }
        override fun all(account: String): Flow<List<PlaybackProgressEntity>> =
            rows.map { all -> all.values.filter { it.account == account }.sortedByDescending { it.updatedAt } }
    }

    private class FakeAccount(initial: String?) : CurrentAccount {
        override val key = MutableStateFlow(initial)
        override suspend fun currentKey() = key.value
    }

    private class FakeLegacy(var entries: Map<String, Long>) : LegacyPlaybackPositions {
        var clears = 0
        override fun read() = entries
        override fun clear() { clears++; entries = emptyMap() }
    }

    private val dao = FakeDao()
    private val account = FakeAccount("boosty:1")
    private val legacy = FakeLegacy(emptyMap())
    private val repository = RoomPlaybackProgressRepository(dao, account, legacy)

    private fun progress(post: String, video: String = "v-$post", position: Long = 60_000, duration: Long = 600_000,
        at: Long = 1, card: Boolean = true) = WatchProgress(post, video, position, duration, at,
        blogUrl = if (card) "blog" else "", title = "Title $post", blogTitle = "Blog", previewUrl = "p")

    @Test fun `positions stay with the account that watched them`() = runTest {
        repository.save(progress("a"))
        assertEquals(60_000L, repository.resumePositionMs("a", "v-a"))
        account.key.value = "boosty:2"
        assertEquals(0L, repository.resumePositionMs("a", "v-a"))
        assertTrue(repository.continueWatching().first().isEmpty())
        account.key.value = "boosty:1"
        assertEquals(listOf("a"), repository.continueWatching().first().map { it.postId })
    }

    @Test fun `nothing is read or written while the account is unknown`() = runTest {
        account.key.value = null
        repository.save(progress("a"))
        assertTrue(dao.rows.value.isEmpty())
        assertEquals(0L, repository.resumePositionMs("a", "v-a"))
        assertTrue(repository.continueWatching().first().isEmpty())
    }

    @Test fun `start and end of a video are not worth resuming`() = runTest {
        repository.save(progress("a"))
        repository.save(progress("a", position = 5_000))
        assertEquals(0L, repository.resumePositionMs("a", "v-a"))
        repository.save(progress("b", position = 590_000))
        assertEquals(0L, repository.resumePositionMs("b", "v-b"))
        repository.save(progress("c", position = 300_000, duration = 0))
        assertEquals(300_000L, repository.resumePositionMs("c", "v-c"))
    }

    @Test fun `continue watching is newest first with one card per post`() = runTest {
        repository.save(progress("old", at = 1))
        repository.save(progress("new", at = 3))
        repository.save(progress("old", video = "second", at = 2))
        assertEquals(listOf("new" to "v-new", "old" to "second"),
            repository.continueWatching().first().map { it.postId to it.videoId })
        assertEquals(listOf("new"), repository.continueWatching(limit = 1).first().map { it.postId })
    }

    @Test fun `card progress uses the latest video of each post`() = runTest {
        repository.save(progress("a", position = 60_000, duration = 600_000, at = 1))
        repository.save(progress("a", video = "later", position = 300_000, duration = 600_000, at = 2))
        assertEquals(mapOf("a" to 0.5f), repository.progressByPost().first())
    }

    @Test fun `old positions move once to the first identified account and resume there`() = runTest {
        legacy.entries = mapOf("post-1:video-1" to 120_000L, "broken" to 5L, "post-2:video:2" to 30_000L)
        account.key.value = "boosty:7"
        assertEquals(120_000L, repository.resumePositionMs("post-1", "video-1"))
        assertEquals(30_000L, repository.resumePositionMs("post-2", "video:2"))
        assertEquals(1, legacy.clears)
        // Without a card they resume but are not listed until played again.
        assertTrue(repository.continueWatching().first().isEmpty())
        assertTrue(repository.progressByPost().first().isEmpty())

        account.key.value = "boosty:8"
        assertEquals(0L, repository.resumePositionMs("post-1", "video-1"))
        assertEquals(1, legacy.clears)
    }

    @Test fun `an old position never overwrites a newer checkpoint`() = runTest {
        repository.save(progress("post-1", video = "video-1", position = 200_000))
        val fresh = RoomPlaybackProgressRepository(dao, account,
            FakeLegacy(mapOf("post-1:video-1" to 20_000L)))
        assertEquals(200_000L, fresh.resumePositionMs("post-1", "video-1"))
    }
}
