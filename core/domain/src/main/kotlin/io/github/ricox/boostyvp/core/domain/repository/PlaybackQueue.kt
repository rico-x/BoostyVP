package io.github.ricox.boostyvp.core.domain.repository

import io.github.ricox.boostyvp.core.model.SavedPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Explicit, session-only queue; unlike author autoplay, its order is chosen by the viewer. */
class PlaybackQueue {
    private val mutable = MutableStateFlow<List<SavedPost>>(emptyList())
    val items = mutable.asStateFlow()
    private var account: String? = null
    @Synchronized fun activate(key: String?) {
        if (account != key) { account = key; mutable.value = emptyList() }
    }
    @Synchronized fun add(post: SavedPost) {
        if (account == null || !post.hasVideo || post.postId.isBlank() || post.blogUrl.isBlank()) return
        if (mutable.value.none { it.postId == post.postId } && mutable.value.size < 100) mutable.value += post
    }
    @Synchronized fun remove(id: String) { mutable.value = mutable.value.filterNot { it.postId == id } }
    @Synchronized fun move(id: String, delta: Int) {
        val list = mutable.value.toMutableList()
        val index = list.indexOfFirst { it.postId == id }
        if (index < 0) return
        val target = (index + delta.coerceIn(-1, 1)).coerceIn(0, list.lastIndex)
        if (index == target) return
        list.add(target, list.removeAt(index)); mutable.value = list
    }
    @Synchronized fun nextAfter(current: String): SavedPost? {
        return queueContinuation(mutable.value, current).next
    }
    @Synchronized fun clear() { mutable.value = emptyList() }
}

data class QueueContinuation(val next: SavedPost?, val completed: List<String>)
fun queueContinuation(items: List<SavedPost>, current: String): QueueContinuation {
    val index = items.indexOfFirst { it.postId == current }
    return if (index < 0) QueueContinuation(items.firstOrNull(), emptyList())
    else QueueContinuation(items.getOrNull(index + 1), items.take(index + 1).map { it.postId })
}
