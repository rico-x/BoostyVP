package io.github.ricox.boostyvp.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.component.*
import io.github.ricox.boostyvp.core.ui.theme.Overscan
import io.github.ricox.boostyvp.localization.UiText
import io.github.ricox.boostyvp.localization.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class CommentsPostState(val post: Post? = null, val failed: Boolean = false)

/** Fetches fresh access/comment permissions without opening a media session or writing history. */
@HiltViewModel
class CommentsPostViewModel @Inject constructor(private val blogs: BlogRepository) : ViewModel() {
    private val mutable = MutableStateFlow(CommentsPostState())
    internal val state = mutable.asStateFlow()
    private var target: Pair<String, String>? = null
    private var job: Job? = null

    fun open(blog: String, post: String, retry: Boolean = false) {
        if (!retry && target == (blog to post)) return
        target = blog to post
        job?.cancel()
        mutable.value = CommentsPostState()
        job = viewModelScope.launch {
            try {
                val value = blogs.post(blog, post)
                coroutineContext.ensureActive()
                mutable.value = CommentsPostState(post = value)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                coroutineContext.ensureActive()
                mutable.value = CommentsPostState(failed = true)
            }
        }
    }
}

@Composable
fun VideoCommentsScreen(blogUrl: String, postId: String, onClose: () -> Unit,
    viewModel: CommentsPostViewModel = hiltViewModel()) {
    LaunchedEffect(blogUrl, postId) { viewModel.open(blogUrl, postId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val post = state.post
    if (post != null) {
        PostReaderScreen(post, onClose, commentsOnly = true)
    } else ScreenBackdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = Overscan.Horizontal, vertical = Overscan.Vertical),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClose)
            if (state.failed) ErrorState(modifier = Modifier.weight(1f), title = tr(UiText.video_comments_failed),
                onRetry = { viewModel.open(blogUrl, postId, retry = true) })
            else LoadingState(Modifier.weight(1f).fillMaxWidth())
        }
    }
}
