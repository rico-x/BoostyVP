package io.github.ricox.boostyvp.core.data.repository

import io.github.ricox.boostyvp.core.data.library.*
import io.github.ricox.boostyvp.core.domain.auth.CurrentAccount
import io.github.ricox.boostyvp.core.domain.repository.PersonalLibraryRepository
import io.github.ricox.boostyvp.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RoomPersonalLibraryRepository @Inject constructor(
    private val dao: PersonalLibraryDao, private val account: CurrentAccount,
) : PersonalLibraryRepository {
    override fun entries(): Flow<PersonalLibrary> = account.key.flatMapLatest { key ->
        if (key == null) flowOf(PersonalLibrary()) else combine(dao.later(key), dao.authors(key), dao.watched(key), dao.favorites(key)) { later, authors, watched, favorites ->
            PersonalLibrary(later.map { SavedPost(it.postId, it.blogUrl, it.title, it.blogTitle,
                it.publishedAt, it.previewUrl, it.hasVideo, it.videoId, it.durationSeconds) },
                authors.map { FavoriteAuthor(it.blogUrl, it.title) },
                watched.filter { it.watched }.mapTo(mutableSetOf()) { it.postId },
                favorites.map { SavedPost(it.postId, it.blogUrl, it.title, it.blogTitle,
                    it.publishedAt, it.previewUrl, it.hasVideo, it.videoId, it.durationSeconds) })
        }
    }

    private suspend fun key() = account.currentKey() ?: error("Account is not identified")
    override suspend fun setLater(post: SavedPost, saved: Boolean) {
        require(post.postId.isNotBlank() && post.blogUrl.isNotBlank())
        val key = key()
        if (saved) dao.saveLater(SavedPostEntity(key, post.postId, post.blogUrl, post.title,
            post.blogTitle, post.publishedAt, post.previewUrl, post.hasVideo, post.videoId,
            post.durationSeconds, System.currentTimeMillis())) else dao.removeLater(key, post.postId)
    }
    override suspend fun setFavoriteAuthor(blogUrl: String, title: String, favorite: Boolean) {
        require(blogUrl.isNotBlank())
        val key = key()
        if (favorite) dao.saveAuthor(FavoriteAuthorEntity(key, blogUrl, title, System.currentTimeMillis()))
        else dao.removeAuthor(key, blogUrl)
    }
    override suspend fun setWatched(postId: String, watched: Boolean) {
        require(postId.isNotBlank())
        dao.setWatched(WatchedPostEntity(key(), postId, watched))
    }
    override suspend fun setFavoritePost(post: SavedPost, favorite: Boolean) {
        require(post.postId.isNotBlank() && post.blogUrl.isNotBlank() && post.hasVideo)
        val key = key()
        if (favorite) dao.saveFavorite(FavoritePostEntity(SavedPostEntity(key, post.postId,
            post.blogUrl, post.title, post.blogTitle, post.publishedAt, post.previewUrl,
            post.hasVideo, post.videoId, post.durationSeconds, System.currentTimeMillis())))
        else dao.removeFavorite(key, post.postId)
    }
}
