package io.github.ricox.boostyvp.core.data.library

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ricox.boostyvp.core.data.repository.*
import io.github.ricox.boostyvp.core.domain.auth.CurrentAccount
import io.github.ricox.boostyvp.core.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryPrivacyDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: LibraryDatabase
    private lateinit var store: LibraryRecordingStore
    private lateinit var progress: RoomPlaybackProgressRepository
    private lateinit var privacy: LibraryPrivacyRepository
    private val account = object : CurrentAccount {
        override val key = MutableStateFlow<String?>("privacy-test-a")
        override suspend fun currentKey() = key.value
    }
    private val legacy = object : LegacyPlaybackPositions {
        var cleared = false
        override fun read() = if (cleared) emptyMap() else mapOf("old:video" to 42_000L)
        override fun clear() { cleared = true }
    }
    @Before fun setup() {
        check(context.packageName != "io.github.ricox.boostyvp")
        db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        store = LibraryRecordingStore(context)
        store.save("privacy-test-a", LibraryRecording()); store.save("privacy-test-b", LibraryRecording())
        progress = RoomPlaybackProgressRepository(db.playbackProgress(), account, legacy, db.watchHistory(), db.personalLibrary(), store)
        privacy = LibraryPrivacyRepository(db, account, store, progress)
    }
    @After fun cleanup() {
        db.close(); store.save("privacy-test-a", LibraryRecording()); store.save("privacy-test-b", LibraryRecording())
    }
    private fun checkpoint(id: String = "post", position: Long = 42_000) = WatchProgress(id, "video", position, 600_000, 1,
        "creator", "Title", "Creator", null)
    @Test fun recordingSwitchesAreIndependentAndDoNotDeleteExistingEntries() = runTest {
        progress.save(checkpoint())
        privacy.save("privacy-test-a", LibraryRecording(history = false, resume = false, watched = false))
        progress.save(checkpoint("private", 599_000))
        assertEquals(listOf("post"), db.watchHistory().all("privacy-test-a").first().map { it.postId })
        assertNull(db.playbackProgress().position("privacy-test-a", "private", "video"))
        assertTrue(db.personalLibrary().watched("privacy-test-a").first().isEmpty())
        assertEquals(42_000L, db.playbackProgress().position("privacy-test-a", "post", "video"))
        privacy.save("privacy-test-a", LibraryRecording(history = false, resume = true, watched = true))
        progress.save(checkpoint("resumed")); progress.save(checkpoint("finished", 599_000))
        assertEquals(42_000L, db.playbackProgress().position("privacy-test-a", "resumed", "video"))
        assertEquals(listOf("finished"), db.personalLibrary().watched("privacy-test-a").first().map { it.postId })
        account.key.value = "privacy-test-b"
        assertEquals(LibraryRecording(), privacy.state.first().recording)
    }
    @Test fun selectedDeletionPreservesOtherListsAndOtherAccountsAndCannotReplayLegacyPositions() = runTest {
        for (owner in listOf("privacy-test-a", "privacy-test-b")) {
            account.key.value = owner
            progress.save(checkpoint())
            val post = SavedPostEntity(owner, "post", "creator", "Title", "Creator", 1, null, true, "video", 600, 1)
            db.personalLibrary().saveLater(post); db.personalLibrary().saveFavorite(FavoritePostEntity(post))
            db.personalLibrary().saveAuthor(FavoriteAuthorEntity(owner, "creator", "Creator", 1))
            db.personalLibrary().setWatched(WatchedPostEntity(owner, "post", true))
        }
        account.key.value = "privacy-test-a"
        privacy.clear("privacy-test-a", setOf(LibraryData.History))
        assertTrue(db.watchHistory().all("privacy-test-a").first().isEmpty())
        assertEquals(42_000L, db.playbackProgress().position("privacy-test-a", "post", "video"))
        assertEquals(1, db.personalLibrary().favorites("privacy-test-a").first().size)
        privacy.clear("privacy-test-a", LibraryData.entries.toSet())
        assertTrue(db.playbackProgress().all("privacy-test-a").first().isEmpty())
        assertTrue(db.personalLibrary().favorites("privacy-test-a").first().isEmpty())
        assertTrue(db.personalLibrary().later("privacy-test-a").first().isEmpty())
        assertTrue(db.personalLibrary().authors("privacy-test-a").first().isEmpty())
        assertTrue(db.personalLibrary().watched("privacy-test-a").first().isEmpty())
        assertEquals(0L, progress.resumePositionMs("old", "video"))
        assertEquals(1, db.watchHistory().all("privacy-test-b").first().size)
        assertEquals(1, db.personalLibrary().favorites("privacy-test-b").first().size)
    }
    @Test fun staleConfirmationCannotDeleteAnotherAccountsData() = runTest {
        progress.save(checkpoint())
        account.key.value = "privacy-test-b"
        assertTrue(runCatching { privacy.clear("privacy-test-a", LibraryData.entries.toSet()) }.isFailure)
        assertEquals(1, db.watchHistory().all("privacy-test-a").first().size)
        assertTrue(runCatching { privacy.save("privacy-test-a", LibraryRecording(false, false, false)) }.isFailure)
    }
}
