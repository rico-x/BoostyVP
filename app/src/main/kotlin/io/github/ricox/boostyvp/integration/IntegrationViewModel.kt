package io.github.ricox.boostyvp.integration

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.domain.auth.CurrentAccount
import io.github.ricox.boostyvp.core.domain.repository.CatalogIndex
import io.github.ricox.boostyvp.core.domain.repository.PlaybackQueue
import io.github.ricox.boostyvp.core.ui.component.CatalogControls
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject

@HiltViewModel
class IntegrationViewModel @Inject constructor(account: CurrentAccount, catalog: CatalogIndex,
    queue: PlaybackQueue, private val channels: TvChannels, val updates: AppUpdates,
    private val blogs: io.github.ricox.boostyvp.core.domain.repository.BlogRepository) : ViewModel() {
    suspend fun resolvePost(post: io.github.ricox.boostyvp.core.model.Post) = blogs.post(post.blogUrl, post.id)
    val controls = combine(account.key, catalog.posts, queue.items) { key, posts, items ->
        CatalogControls(if (catalog.account == key && key != null) posts else emptyList(),
            if (key != null && catalog.account == key) items else emptyList(), key != null,
            queue::add, queue::remove, queue::move, queue::clear)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogControls())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow("")
    val available = MutableStateFlow<AvailableUpdate?>(null)
    val downloaded = MutableStateFlow<File?>(null)
    private var channelRefresh: Job? = null
    fun refreshChannels() {
        // Settings can change several times while the network is slow: only the latest request wins.
        channelRefresh?.cancel()
        channelRefresh = viewModelScope.launch {
            message.value = tr(UiText.integration_view_model_001)
            message.value = channels.sync()
        }
    }
    fun checkUpdates() = task {
        downloaded.value = null
        val update = updates.check(); available.value = update
        message.value = if (update.code > updates.currentCode) tr(UiText.integration_view_model_002, update.version) else tr(UiText.app_updates_008)
    }
    fun downloadUpdate() = task {
        val update = available.value ?: return@task
        message.value = tr(UiText.integration_view_model_003)
        downloaded.value = updates.download(update)
        message.value = tr(UiText.integration_view_model_004)
    }
    private fun task(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; message.value = tr(UiText.integration_view_model_005)
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = e.message ?: tr(UiText.integration_view_model_006) }
            finally { busy.value = false }
        }
    }
}
