package io.github.ricox.boostyvp.core.domain.repository

import io.github.ricox.boostyvp.core.model.PersonalLibrary
import io.github.ricox.boostyvp.core.model.SavedPost
import kotlinx.coroutines.flow.Flow

interface PersonalLibraryRepository {
    fun entries(): Flow<PersonalLibrary>
    suspend fun setLater(post: SavedPost, saved: Boolean)
    suspend fun setFavoriteAuthor(blogUrl: String, title: String, favorite: Boolean)
    suspend fun setWatched(postId: String, watched: Boolean)
    suspend fun setFavoritePost(post: SavedPost, favorite: Boolean)
}
