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
import kotlinx.coroutines.flow.Flow

/**
 * The personal library: per-account data that lives only on this TV.
 *
 * Versioned with exported schemas in `core/data/schemas`; a change to any
 * entity needs a new version and a migration, never a destructive rebuild.
 */
@Database(entities = [PlaybackProgressEntity::class], version = 1, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun playbackProgress(): PlaybackProgressDao

    companion object {
        const val NAME = "library.db"
    }
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
