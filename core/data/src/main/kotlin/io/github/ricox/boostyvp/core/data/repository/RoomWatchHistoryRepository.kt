package io.github.ricox.boostyvp.core.data.repository

import io.github.ricox.boostyvp.core.data.library.WatchHistoryDao
import io.github.ricox.boostyvp.core.domain.auth.CurrentAccount
import io.github.ricox.boostyvp.core.domain.repository.WatchHistoryRepository
import io.github.ricox.boostyvp.core.model.WatchProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RoomWatchHistoryRepository @Inject constructor(
    private val dao: WatchHistoryDao,
    private val account: CurrentAccount,
) : WatchHistoryRepository {
    override fun entries() = account.key.flatMapLatest { key ->
        if (key == null) flowOf(emptyList()) else dao.all(key).map { rows ->
            rows.distinctBy { it.postId }.map {
                WatchProgress(it.postId, it.videoId, it.positionMs, it.durationMs, it.updatedAt,
                    it.blogUrl, it.title, it.blogTitle, it.previewUrl)
            }
        }
    }

    override suspend fun removePost(postId: String) {
        val key = account.currentKey() ?: error("Account is not identified")
        dao.deletePost(key, postId)
    }
}
