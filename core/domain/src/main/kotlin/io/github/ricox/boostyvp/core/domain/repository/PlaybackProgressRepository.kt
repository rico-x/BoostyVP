package io.github.ricox.boostyvp.core.domain.repository

interface PlaybackProgressRepository {
    suspend fun save(postId: String, videoId: String, positionMs: Long, durationMs: Long)
    suspend fun resumePositionMs(postId: String, videoId: String): Long
    suspend fun clear(postId: String, videoId: String)
}
