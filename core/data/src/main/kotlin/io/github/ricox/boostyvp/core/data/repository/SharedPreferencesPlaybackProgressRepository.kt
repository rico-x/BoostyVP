package io.github.ricox.boostyvp.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.ricox.boostyvp.core.domain.repository.PlaybackProgressRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resume points, keyed by post + video.
 *
 * Plain SharedPreferences: this is non-sensitive, must survive a cold start,
 * and is written often enough during playback that a heavier store would be
 * counterproductive.
 */
@Singleton
class SharedPreferencesPlaybackProgressRepository @Inject constructor(
    @ApplicationContext context: Context,
) : PlaybackProgressRepository {
    private val prefs = context.getSharedPreferences("playback_progress", Context.MODE_PRIVATE)
    private val io: CoroutineDispatcher = Dispatchers.IO

    override suspend fun save(postId: String, videoId: String, positionMs: Long, durationMs: Long) =
        withContext(io) {
            // Near the start there is nothing to resume, and near the end the
            // user has effectively finished -- storing either just produces an
            // annoying "resume" prompt.
            val meaningful = positionMs > MIN_RESUME_MS &&
                (durationMs <= 0 || positionMs < durationMs - END_MARGIN_MS)
            if (meaningful) {
                prefs.edit().putLong(key(postId, videoId), positionMs).apply()
            } else {
                prefs.edit().remove(key(postId, videoId)).apply()
            }
        }

    override suspend fun resumePositionMs(postId: String, videoId: String): Long = withContext(io) {
        prefs.getLong(key(postId, videoId), 0L)
    }

    override suspend fun clear(postId: String, videoId: String) = withContext(io) {
        prefs.edit().remove(key(postId, videoId)).apply()
    }

    private fun key(postId: String, videoId: String) = "$postId:$videoId"

    private companion object {
        const val MIN_RESUME_MS = 10_000L
        const val END_MARGIN_MS = 30_000L
    }
}
