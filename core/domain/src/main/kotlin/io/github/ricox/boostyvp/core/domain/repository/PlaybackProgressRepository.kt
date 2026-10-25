package io.github.ricox.boostyvp.core.domain.repository

import io.github.ricox.boostyvp.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow

/** Resume points of the signed-in account; another account never sees them. */
interface PlaybackProgressRepository {
    /** Session recreation must respect the user's per-account resume privacy switch. */
    suspend fun recordsResumePositions(): Boolean = true
    suspend fun save(progress: WatchProgress)
    suspend fun resumePositionMs(postId: String, videoId: String): Long
    suspend fun clear(postId: String, videoId: String)

    /** Started videos that can draw a card, most recently watched first. */
    fun continueWatching(limit: Int = 20): Flow<List<WatchProgress>>

    /** Watched fraction per post, from its most recently watched video. */
    fun progressByPost(): Flow<Map<String, Float>>
}
