package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.repository.PostCommentsRepository
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.PostComment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommentListState(
    val items: List<PostComment> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
) {
    val canLoadMore: Boolean get() = !loaded || nextCursor != null
}

data class PostCommentsState(
    val expanded: Boolean = false,
    val root: CommentListState = CommentListState(),
    val expandedReplies: Set<Long> = emptySet(),
    val replies: Map<Long, CommentListState> = emptyMap(),
)

@HiltViewModel
class PostCommentsViewModel @Inject constructor(private val repository: PostCommentsRepository) : ViewModel() {
    private val mutable = MutableStateFlow(PostCommentsState())
    val state = mutable.asStateFlow()
    private var post: Post? = null
    private var generation = 0
    private val jobs = mutableMapOf<Long?, Job>()
    private val cursors = mutableMapOf<Long?, MutableSet<String>>()

    fun bind(value: Post, initiallyExpanded: Boolean = false) {
        if (post?.let { it.id == value.id && it.blogUrl == value.blogUrl &&
                it.hasAccess == value.hasAccess && it.commentsDenied == value.commentsDenied } == true) {
            post = value
            return
        }
        generation++
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        cursors.clear()
        post = value
        mutable.value = PostCommentsState()
        if (initiallyExpanded) toggle()
    }

    fun toggle() {
        val current = post ?: return
        if (!current.hasAccess || current.commentsDenied) return
        mutable.value = mutable.value.copy(expanded = !mutable.value.expanded)
        if (mutable.value.expanded && !mutable.value.root.loaded) loadMore()
    }

    fun toggleReplies(parentId: Long) {
        if (!mutable.value.expanded || mutable.value.root.items.none { it.intId == parentId && it.replyCount > 0 }) return
        val old = mutable.value.expandedReplies
        mutable.value = mutable.value.copy(expandedReplies = if (parentId in old) old - parentId else old + parentId)
        if (parentId in mutable.value.expandedReplies && mutable.value.replies[parentId]?.loaded != true) loadMore(parentId)
    }

    fun loadMore(parentId: Long? = null) {
        val current = post ?: return
        if (!current.hasAccess || current.commentsDenied || !mutable.value.expanded) return
        if (parentId != null && parentId !in mutable.value.expandedReplies) return
        val before = list(parentId)
        if (before.loading || !before.canLoadMore) return
        val epoch = generation
        update(parentId, before.copy(loading = true, error = null))
        jobs[parentId] = viewModelScope.launch {
            try {
                val page = repository.page(current.blogUrl, current.id, before.nextCursor, parentId)
                currentCoroutineContext().ensureActive()
                if (generation != epoch) return@launch
                val seen = cursors.getOrPut(parentId) { mutableSetOf() }
                val next = page.nextCursor
                check(next == null || seen.add(next)) { "Repeated comments cursor" }
                update(parentId, CommentListState(
                    items = (before.items + page.items).distinctBy { it.id },
                    loaded = true, nextCursor = page.nextCursor,
                ))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == epoch) update(parentId, before.copy(
                    error = tr(UiText.post_comments_view_model_001), loading = false))
            }
        }
    }

    private fun list(parentId: Long?): CommentListState =
        if (parentId == null) mutable.value.root else mutable.value.replies[parentId] ?: CommentListState()

    private fun update(parentId: Long?, value: CommentListState) {
        mutable.value = if (parentId == null) mutable.value.copy(root = value)
        else mutable.value.copy(replies = mutable.value.replies + (parentId to value))
    }
}
