package io.github.ricox.boostyvp.core.domain.repository

import io.github.ricox.boostyvp.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow

/** Video history on this TV, isolated by account and independent of resume points. */
interface WatchHistoryRepository {
    /** Latest viewing of each post, newest first, including completed videos. */
    fun entries(): Flow<List<WatchProgress>>

    /** Remove only this post's history, not its resume point or another account's data. */
    suspend fun removePost(postId: String)
}
