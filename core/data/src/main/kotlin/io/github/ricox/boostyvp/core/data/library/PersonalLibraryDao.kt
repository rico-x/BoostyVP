package io.github.ricox.boostyvp.core.data.library

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watch_later", primaryKeys = ["account", "postId"])
data class SavedPostEntity(
    val account: String, val postId: String, val blogUrl: String, val title: String,
    val blogTitle: String, val publishedAt: Long, val previewUrl: String?,
    val hasVideo: Boolean, val videoId: String, val durationSeconds: Int, val addedAt: Long,
)

@Entity(tableName = "favorite_authors", primaryKeys = ["account", "blogUrl"])
data class FavoriteAuthorEntity(val account: String, val blogUrl: String, val title: String, val addedAt: Long)

@Entity(tableName = "watched_posts", primaryKeys = ["account", "postId"])
data class WatchedPostEntity(val account: String, val postId: String, val watched: Boolean)

@Entity(tableName = "favorite_posts", primaryKeys = ["account", "postId"])
data class FavoritePostEntity(@Embedded val post: SavedPostEntity)

@Dao
interface PersonalLibraryDao {
    @Query("SELECT * FROM watch_later WHERE account = :account ORDER BY addedAt DESC, postId")
    fun later(account: String): Flow<List<SavedPostEntity>>
    @Upsert suspend fun saveLater(post: SavedPostEntity)
    @Query("DELETE FROM watch_later WHERE account = :account AND postId = :postId")
    suspend fun removeLater(account: String, postId: String)
    @Query("SELECT * FROM favorite_authors WHERE account = :account ORDER BY addedAt DESC, blogUrl")
    fun authors(account: String): Flow<List<FavoriteAuthorEntity>>
    @Upsert suspend fun saveAuthor(author: FavoriteAuthorEntity)
    @Query("DELETE FROM favorite_authors WHERE account = :account AND blogUrl = :blogUrl")
    suspend fun removeAuthor(account: String, blogUrl: String)
    @Query("SELECT * FROM watched_posts WHERE account = :account")
    fun watched(account: String): Flow<List<WatchedPostEntity>>
    @Upsert suspend fun setWatched(post: WatchedPostEntity)
    @Query("SELECT * FROM favorite_posts WHERE account = :account ORDER BY addedAt DESC, postId")
    fun favorites(account: String): Flow<List<SavedPostEntity>>
    @Upsert suspend fun saveFavorite(post: FavoritePostEntity)
    @Query("DELETE FROM favorite_posts WHERE account = :account AND postId = :postId")
    suspend fun removeFavorite(account: String, postId: String)
}
