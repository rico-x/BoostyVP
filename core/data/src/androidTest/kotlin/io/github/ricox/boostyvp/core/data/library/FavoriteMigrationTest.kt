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
class FavoriteMigrationTest {
    @Test fun versionThreeKeepsEveryExistingListAndAddsPersistentAccountScopedFavoriteVideos() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName != "io.github.ricox.boostyvp")
        val name = "favorite-v3-v4-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(context.assets.open("io.github.ricox.boostyvp.core.data.library.LibraryDatabase/3.json")
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
            for (owner in listOf("a", "b")) {
                old.execSQL("INSERT INTO playback_progress VALUES ('$owner','p','v',42000,600000,10,'creator','Title','Creator',NULL)")
                old.execSQL("INSERT INTO watch_history VALUES ('$owner','p','v',42000,600000,10,'creator','Title','Creator',NULL)")
                old.execSQL("INSERT INTO watch_later VALUES ('$owner','p','creator','Title','Creator',1,NULL,1,'v',600,10)")
                old.execSQL("INSERT INTO favorite_authors VALUES ('$owner','creator','Creator',10)")
                old.execSQL("INSERT INTO watched_posts VALUES ('$owner','p',1)")
            }
            old.version = 3
        }
        fun open() = Room.databaseBuilder(context, LibraryDatabase::class.java, name)
            .addMigrations(LibraryDatabase.MIGRATION_3_4).build()
        var db = open()
        try {
            for (owner in listOf("a", "b")) {
                assertEquals(42_000L, db.playbackProgress().position(owner, "p", "v"))
                assertEquals(1, db.watchHistory().all(owner).first().size)
                assertEquals(1, db.personalLibrary().later(owner).first().size)
                assertEquals(1, db.personalLibrary().authors(owner).first().size)
                assertEquals(1, db.personalLibrary().watched(owner).first().size)
                assertTrue(db.personalLibrary().favorites(owner).first().isEmpty())
            }
            val card = SavedPostEntity("a", "p", "creator", "Favorite title", "Creator", 1, null, true, "v", 600, 20)
            repeat(2) { db.personalLibrary().saveFavorite(FavoritePostEntity(card)) }
            db.close(); db = open()
            assertEquals(listOf("Favorite title"), db.personalLibrary().favorites("a").first().map { it.title })
            assertTrue(db.personalLibrary().favorites("b").first().isEmpty())
            db.personalLibrary().removeFavorite("a", "p")
            assertEquals(1, db.personalLibrary().later("a").first().size)
            assertEquals(1, db.personalLibrary().authors("a").first().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
