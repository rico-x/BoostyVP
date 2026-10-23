package io.github.ricox.boostyvp.core.data.library

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * The personal library: per-account data that lives only on this TV.
 *
 * Versioned with exported schemas in `core/data/schemas`; a change to any
 * entity needs a new version and a migration, never a destructive rebuild.
 */
@Database(entities = [PlaybackProgressEntity::class, WatchHistoryEntity::class], version = 2, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun playbackProgress(): PlaybackProgressDao
    abstract fun watchHistory(): WatchHistoryDao

    companion object {
        const val NAME = "library.db"

        /** Add history without changing even one existing resume point. Room migrates atomically. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `watch_history` (
                    `account` TEXT NOT NULL, `postId` TEXT NOT NULL, `videoId` TEXT NOT NULL,
                    `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                    `blogUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `blogTitle` TEXT NOT NULL, `previewUrl` TEXT,
                    PRIMARY KEY(`account`, `postId`, `videoId`))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_history_account_updatedAt` ON `watch_history` (`account`, `updatedAt`)")
                db.execSQL("""INSERT INTO watch_history
                    (account, postId, videoId, positionMs, durationMs, updatedAt, blogUrl, title, blogTitle, previewUrl)
                    SELECT account, postId, videoId, positionMs, durationMs, updatedAt, blogUrl, title, blogTitle, previewUrl
                    FROM playback_progress WHERE blogUrl != ''""")
            }
        }
    }
}

/** Deliberately contains no signed media URLs, tokens or publication bodies. */
@Entity(tableName = "watch_history", primaryKeys = ["account", "postId", "videoId"],
    indices = [Index(value = ["account", "updatedAt"])])
data class WatchHistoryEntity(
    val account: String,
    val postId: String,
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val blogUrl: String,
    val title: String,
    val blogTitle: String,
    val previewUrl: String?,
)

@Dao
interface WatchHistoryDao {
    @Upsert
    suspend fun upsert(entry: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE account = :account ORDER BY updatedAt DESC, postId, videoId")
    fun all(account: String): Flow<List<WatchHistoryEntity>>

    @Query("DELETE FROM watch_history WHERE account = :account AND postId = :postId")
    suspend fun deletePost(account: String, postId: String)
}

/** One video's resume point for one account, with the post fields its card needs. */
@Entity(
    tableName = "playback_progress",
    primaryKeys = ["account", "postId", "videoId"],
    indices = [Index(value = ["account", "updatedAt"])],
)
data class PlaybackProgressEntity(
    val account: String,
    val postId: String,
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /** Empty for positions carried over from 0.2 storage until replayed. */
    val blogUrl: String,
    val title: String,
    val blogTitle: String,
    val previewUrl: String?,
)

@Dao
interface PlaybackProgressDao {
    @Upsert
    suspend fun upsert(entry: PlaybackProgressEntity)

    /** Carried-over positions never overwrite anything already recorded. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entries: List<PlaybackProgressEntity>)

    @Query("SELECT positionMs FROM playback_progress WHERE account = :account AND postId = :postId AND videoId = :videoId")
    suspend fun position(account: String, postId: String, videoId: String): Long?

    @Query("DELETE FROM playback_progress WHERE account = :account AND postId = :postId AND videoId = :videoId")
    suspend fun delete(account: String, postId: String, videoId: String)

    @Query("SELECT * FROM playback_progress WHERE account = :account AND blogUrl != '' ORDER BY updatedAt DESC")
    fun withCards(account: String): Flow<List<PlaybackProgressEntity>>

    @Query("SELECT * FROM playback_progress WHERE account = :account ORDER BY updatedAt DESC")
    fun all(account: String): Flow<List<PlaybackProgressEntity>>
}
