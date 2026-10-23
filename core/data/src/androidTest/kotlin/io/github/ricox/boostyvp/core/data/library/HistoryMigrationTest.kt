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

/** A real v1 file from the committed schema, never the production library or account. */
@RunWith(AndroidJUnit4::class)
class HistoryMigrationTest {
    @Test fun versionOneUpgradesWithoutLosingAnyAccountsResumePoints() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName != "io.github.ricox.boostyvp")
        val name = "history-migration-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(context.assets.open(
            "io.github.ricox.boostyvp.core.data.library.LibraryDatabase/1.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j)
                    .getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO playback_progress VALUES ('a','p','v',42000,600000,10,'blog','Title','Author',NULL)")
            old.execSQL("INSERT INTO playback_progress VALUES ('b','p','v',90000,600000,20,'other','Other','Author',NULL)")
            old.execSQL("INSERT INTO playback_progress VALUES ('a','legacy','v',12000,0,0,'','','',NULL)")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, LibraryDatabase::class.java, name)
            .addMigrations(LibraryDatabase.MIGRATION_1_2).build()
        try {
            assertEquals(42_000L, db.playbackProgress().position("a", "p", "v"))
            assertEquals(90_000L, db.playbackProgress().position("b", "p", "v"))
            assertEquals(12_000L, db.playbackProgress().position("a", "legacy", "v"))
            assertEquals(listOf("p"), db.watchHistory().all("a").first().map { it.postId })
            assertEquals(90_000L, db.watchHistory().all("b").first().single().positionMs)
            db.watchHistory().deletePost("a", "p")
            assertTrue(db.watchHistory().all("a").first().isEmpty())
            assertEquals(42_000L, db.playbackProgress().position("a", "p", "v"))
            assertEquals(1, db.watchHistory().all("b").first().size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
