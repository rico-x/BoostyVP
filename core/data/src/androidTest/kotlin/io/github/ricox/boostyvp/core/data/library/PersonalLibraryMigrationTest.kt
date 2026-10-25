package io.github.ricox.boostyvp.core.data.library

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalLibraryMigrationTest {
    @Test fun versionTwoUpgradesAndRetainsResumeAndHistoryForBothAccounts() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName != "io.github.ricox.boostyvp")
        val name = "personal-library-migration-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(context.assets.open("io.github.ricox.boostyvp.core.data.library.LibraryDatabase/2.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                val indices = e.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql")
                    .replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            for (account in listOf("a", "b")) {
                old.execSQL("INSERT INTO playback_progress VALUES ('$account','p','v',42000,600000,10,'blog','Title','Author',NULL)")
                old.execSQL("INSERT INTO watch_history VALUES ('$account','p','v',42000,600000,10,'blog','Title','Author',NULL)")
                old.execSQL("INSERT INTO watch_history VALUES ('$account','done','v',600000,600000,11,'blog','Done','Author',NULL)")
                old.execSQL("INSERT INTO watch_history VALUES ('$account','short','v',0,15000,12,'blog','Short','Author',NULL)")
            }
            old.version = 2
        }
        fun open() = Room.databaseBuilder(context, LibraryDatabase::class.java, name)
            .addMigrations(LibraryDatabase.MIGRATION_1_2, LibraryDatabase.MIGRATION_2_3, LibraryDatabase.MIGRATION_3_4).build()
        var db = open()
        try {
            for (account in listOf("a", "b")) {
                assertEquals(42_000L, db.playbackProgress().position(account, "p", "v"))
                assertEquals(3, db.watchHistory().all(account).first().size)
                assertEquals(listOf("done"), db.personalLibrary().watched(account).first().map { it.postId })
            }
            val dao = db.personalLibrary()
            dao.saveLater(SavedPostEntity("a", "p", "blog", "Title", "Author", 0, null, true, "v", 600, 1))
            dao.saveAuthor(FavoriteAuthorEntity("a", "blog", "Author", 1))
            dao.setWatched(WatchedPostEntity("a", "p", true))
            dao.setWatched(WatchedPostEntity("a", "done", false))
            db.close(); db = open()
            assertEquals(1, db.personalLibrary().later("a").first().size)
            assertTrue(db.personalLibrary().later("b").first().isEmpty())
            assertEquals("blog", db.personalLibrary().authors("a").first().single().blogUrl)
            assertFalse(db.personalLibrary().watched("a").first().first { it.postId == "done" }.watched)
            db.watchHistory().deletePost("a", "p")
            assertEquals(42_000L, db.playbackProgress().position("a", "p", "v"))
            assertEquals(1, db.personalLibrary().later("a").first().size)
            assertTrue(db.personalLibrary().watched("a").first().first { it.postId == "p" }.watched)
            assertEquals(3, db.watchHistory().all("b").first().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
