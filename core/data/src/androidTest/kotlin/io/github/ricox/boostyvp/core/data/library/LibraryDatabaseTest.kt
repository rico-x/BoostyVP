package io.github.ricox.boostyvp.core.data.library

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real SQLite queries behind the library, in memory, with no account or network. */
@RunWith(AndroidJUnit4::class)
class LibraryDatabaseTest {
    private lateinit var db: LibraryDatabase
    private val dao get() = db.playbackProgress()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, LibraryDatabase::class.java,
        ).build()
    }

    @After fun close() = db.close()

    private fun row(account: String, post: String, video: String = "v", position: Long = 1_000,
        at: Long = 0, blog: String = "blog") =
        PlaybackProgressEntity(account, post, video, position, 10_000, at, blog, "T", "B", null)

    @Test fun upsertReplacesTheCheckpointOfTheSameVideoOnly() = runTest {
        dao.upsert(row("a", "p", position = 1_000))
        dao.upsert(row("a", "p", position = 2_000))
        dao.upsert(row("b", "p", position = 9_000))
        assertEquals(2_000L, dao.position("a", "p", "v"))
        assertEquals(9_000L, dao.position("b", "p", "v"))
        dao.delete("a", "p", "v")
        assertNull(dao.position("a", "p", "v"))
        assertEquals(9_000L, dao.position("b", "p", "v"))
    }

    @Test fun carriedOverPositionsNeverOverwriteAndStayOffTheCardList() = runTest {
        dao.upsert(row("a", "kept", position = 5_000, at = 1))
        dao.insertIfAbsent(listOf(row("a", "kept", position = 1, blog = ""), row("a", "old", blog = "")))
        assertEquals(5_000L, dao.position("a", "kept", "v"))
        assertEquals(1_000L, dao.position("a", "old", "v"))
        assertEquals(listOf("kept"), dao.withCards("a").first().map { it.postId })
        assertEquals(setOf("kept", "old"), dao.all("a").first().map { it.postId }.toSet())
    }

    @Test fun cardsAreNewestFirstAndPerAccount() = runTest {
        dao.upsert(row("a", "first", at = 1))
        dao.upsert(row("a", "third", at = 3))
        dao.upsert(row("a", "second", at = 2))
        dao.upsert(row("b", "other", at = 9))
        assertEquals(listOf("third", "second", "first"), dao.withCards("a").first().map { it.postId })
    }
}
