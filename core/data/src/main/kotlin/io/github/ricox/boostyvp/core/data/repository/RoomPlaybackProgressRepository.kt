package io.github.ricox.boostyvp.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.ricox.boostyvp.core.data.library.PlaybackProgressDao
import io.github.ricox.boostyvp.core.data.library.PlaybackProgressEntity
import io.github.ricox.boostyvp.core.data.library.WatchHistoryDao
import io.github.ricox.boostyvp.core.data.library.WatchHistoryEntity
import io.github.ricox.boostyvp.core.data.library.PersonalLibraryDao
import io.github.ricox.boostyvp.core.data.library.WatchedPostEntity
import io.github.ricox.boostyvp.core.data.library.LibraryRecordingStore
import io.github.ricox.boostyvp.core.data.library.LibraryRecording
import io.github.ricox.boostyvp.core.domain.auth.CurrentAccount
import io.github.ricox.boostyvp.core.domain.repository.PlaybackProgressRepository
import io.github.ricox.boostyvp.core.model.WatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Resume points written by 0.2: `postId:videoId` -> position in ms, no account. */
interface LegacyPlaybackPositions {
    fun read(): Map<String, Long>
    fun clear()
}

class SharedPreferencesLegacyPositions @Inject constructor(
    @ApplicationContext context: Context,
) : LegacyPlaybackPositions {
    private val prefs by lazy { context.getSharedPreferences("playback_progress", Context.MODE_PRIVATE) }
    override fun read(): Map<String, Long> = prefs.all.mapNotNull { (key, value) ->
        (value as? Long)?.let { key to it }
    }.toMap()
    override fun clear() { prefs.edit().clear().commit() }
}

/**
 * Resume points per account in the library database.
 *
 * 0.2 kept positions without an account. They are moved, once, to the first
 * account identified after the update -- on a TV that is the account that
 * watched them -- and the old storage is then emptied so no later account
 * inherits them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RoomPlaybackProgressRepository @Inject constructor(
    private val dao: PlaybackProgressDao,
    private val account: CurrentAccount,
    private val legacy: LegacyPlaybackPositions,
    private val history: WatchHistoryDao,
    private val library: PersonalLibraryDao,
    private val recording: LibraryRecordingStore? = null,
) : PlaybackProgressRepository {
    private val migration = Mutex()
    @Volatile private var migrated = false

    override suspend fun recordsResumePositions(): Boolean = withContext(Dispatchers.IO) {
        val key = account.currentKey() ?: return@withContext false
        recording?.load(key)?.resume ?: true
    }

    // NonCancellable: the last checkpoint arrives while the player closes and
    // its scope is being cancelled; it must still reach the database.
    override suspend fun save(progress: WatchProgress) = withContext(NonCancellable + Dispatchers.IO) {
        val key = account.currentKey() ?: return@withContext
        migrateLegacy(key)
        val policy = recording?.load(key) ?: LibraryRecording()
        if (progress.hasCard && progress.postId.isNotBlank() && progress.videoId.isNotBlank()) {
            if (policy.history) history.upsert(WatchHistoryEntity(key, progress.postId, progress.videoId,
                progress.positionMs.coerceAtLeast(0), progress.durationMs.coerceAtLeast(0), progress.updatedAt,
                progress.blogUrl, progress.title, progress.blogTitle, progress.previewUrl))
            if (policy.watched && progress.durationMs > 0 && progress.positionMs >= progress.durationMs -
                minOf(30_000L, progress.durationMs / 20)) {
                library.setWatched(WatchedPostEntity(key, progress.postId, true))
            }
        }
        if (!policy.resume) return@withContext
        if (progress.isWorthResuming()) {
            dao.upsert(progress.toEntity(key))
        } else {
            // Near the start there is nothing to resume; near the end the
            // video is effectively finished.
            dao.delete(key, progress.postId, progress.videoId)
        }
    }

    override suspend fun resumePositionMs(postId: String, videoId: String): Long {
        val key = account.currentKey() ?: return 0L
        migrateLegacy(key)
        return dao.position(key, postId, videoId) ?: 0L
    }

    override suspend fun clear(postId: String, videoId: String) = withContext(NonCancellable) {
        val key = account.currentKey() ?: return@withContext
        dao.delete(key, postId, videoId)
    }

    override fun continueWatching(limit: Int): Flow<List<WatchProgress>> = forAccount(emptyList()) { key ->
        // One card per post: a post with several started videos shows its latest.
        dao.withCards(key).map { rows -> rows.distinctBy { it.postId }.take(limit).map { it.toModel() } }
    }

    override fun progressByPost(): Flow<Map<String, Float>> = forAccount(emptyMap()) { key ->
        dao.all(key).map { rows ->
            rows.distinctBy { it.postId }.associate { it.postId to it.toModel().fraction }
                .filterValues { it > 0f }
        }
    }

    private fun <T> forAccount(signedOut: T, rows: (String) -> Flow<T>): Flow<T> =
        account.key.flatMapLatest { key ->
            if (key == null) flowOf(signedOut) else flow {
                migrateLegacy(key)
                emitAll(rows(key))
            }
        }

    internal suspend fun prepareLibrary(key: String) { migrateLegacy(key) }

    private suspend fun migrateLegacy(key: String) {
        if (migrated) return
        migration.withLock {
            if (migrated) return
            withContext(Dispatchers.IO) {
                val old = legacy.read().mapNotNull { (stored, positionMs) ->
                    legacyEntry(key, stored, positionMs)
                }
                if (old.isNotEmpty()) dao.insertIfAbsent(old)
                legacy.clear()
            }
            migrated = true
        }
    }

    private companion object {
        const val MIN_RESUME_MS = 10_000L
        const val END_MARGIN_MS = 30_000L

        fun WatchProgress.isWorthResuming() = positionMs > MIN_RESUME_MS &&
            (durationMs <= 0 || positionMs < durationMs - END_MARGIN_MS)

        fun WatchProgress.toEntity(account: String) = PlaybackProgressEntity(
            account = account, postId = postId, videoId = videoId,
            positionMs = positionMs, durationMs = durationMs, updatedAt = updatedAt,
            blogUrl = blogUrl, title = title, blogTitle = blogTitle, previewUrl = previewUrl,
        )

        fun PlaybackProgressEntity.toModel() = WatchProgress(
            postId = postId, videoId = videoId, positionMs = positionMs, durationMs = durationMs,
            updatedAt = updatedAt, blogUrl = blogUrl, title = title, blogTitle = blogTitle,
            previewUrl = previewUrl,
        )

        /** Post ids are UUIDs without ':', so the first ':' separates the video id. */
        fun legacyEntry(account: String, stored: String, positionMs: Long): PlaybackProgressEntity? {
            val split = stored.indexOf(':')
            if (split <= 0 || split == stored.lastIndex || positionMs <= 0) return null
            return PlaybackProgressEntity(
                account = account, postId = stored.substring(0, split), videoId = stored.substring(split + 1),
                positionMs = positionMs, durationMs = 0, updatedAt = 0,
                blogUrl = "", title = "", blogTitle = "", previewUrl = null,
            )
        }
    }
}
