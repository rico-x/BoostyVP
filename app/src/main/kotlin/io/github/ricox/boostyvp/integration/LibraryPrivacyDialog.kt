package io.github.ricox.boostyvp.integration

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ricox.boostyvp.core.data.library.*
import io.github.ricox.boostyvp.core.ui.component.TvActionButton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class LibraryPrivacyViewModel @Inject constructor(private val repository: LibraryPrivacyRepository,
    private val channels: TvChannels) : ViewModel() {
    val state = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryPrivacyState())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow("")
    fun save(value: LibraryRecording) { val key = state.value.account ?: return; task { repository.save(key, value) } }
    fun clear(key: String, selected: Set<LibraryData>) = task {
        repository.clear(key, selected)
        message.value = tr(UiText.library_privacy_dialog_001)
        channels.sync(fetchFresh = false)
    }
    private fun task(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; message.value = ""
        viewModelScope.launch {
            try { action() } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = tr(UiText.library_privacy_dialog_002) }
            finally { busy.value = false }
        }
    }
}

@Composable
fun LibraryPrivacyDialog(onDismiss: () -> Unit, model: LibraryPrivacyViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    LibraryPrivacyDialogContent(state, busy, message, model::save, model::clear, onDismiss)
}

@Composable
internal fun LibraryPrivacyDialogContent(state: LibraryPrivacyState, busy: Boolean, message: String,
    onSave: (LibraryRecording) -> Unit, onClear: (String, Set<LibraryData>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(state.account) { mutableStateOf(emptySet<LibraryData>()) }
    var confirmation by remember(state.account) { mutableStateOf(false) }
    val first = remember(confirmation, state.account) { FocusRequester() }
    val labels = mapOf(LibraryData.History to tr(UiText.library_privacy_dialog_003), LibraryData.Resume to tr(UiText.library_privacy_dialog_004),
        LibraryData.Later to tr(UiText.library_privacy_dialog_005), LibraryData.Videos to tr(UiText.library_privacy_dialog_006),
        LibraryData.Authors to tr(UiText.library_privacy_dialog_007), LibraryData.Watched to tr(UiText.library_privacy_dialog_008))
    Dialog(onDismissRequest = { if (!busy) { if (confirmation) confirmation = false else onDismiss() } },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(Modifier.width(720.dp).heightIn(max = 480.dp).background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()).padding(24.dp).testTag("library_privacy"),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr(UiText.library_privacy_dialog_009), style = MaterialTheme.typography.headlineSmall)
            if (confirmation) {
                Text(tr(UiText.library_privacy_dialog_010))
                Text(selected.joinToString(" · ") { labels.getValue(it) })
                Text(tr(UiText.library_privacy_dialog_011))
                TvActionButton(tr(UiText.library_privacy_dialog_012), { confirmation = false }, Modifier.fillMaxWidth().focusRequester(first), enabled = !busy)
                TvActionButton(tr(UiText.library_privacy_dialog_013), {
                    state.account?.let { onClear(it, selected.toSet()) }
                    confirmation = false; selected = emptySet()
                }, Modifier.fillMaxWidth().testTag("privacy_confirm_delete"), enabled = !busy && state.account != null)
            } else {
                Text(tr(UiText.library_privacy_dialog_014),
                    style = MaterialTheme.typography.bodySmall)
                val policy = state.recording
                TvActionButton(tr(UiText.library_privacy_dialog_017, if (policy.history) tr(UiText.library_privacy_dialog_015) else tr(UiText.library_privacy_dialog_016)), { onSave(policy.copy(history = !policy.history)) },
                    Modifier.fillMaxWidth().focusRequester(first).testTag("privacy_history"), enabled = !busy && state.account != null)
                TvActionButton(tr(UiText.library_privacy_dialog_018, if (policy.resume) tr(UiText.library_privacy_dialog_015) else tr(UiText.library_privacy_dialog_016)), { onSave(policy.copy(resume = !policy.resume)) },
                    Modifier.fillMaxWidth().testTag("privacy_resume"), enabled = !busy && state.account != null)
                TvActionButton(tr(UiText.library_privacy_dialog_019, if (policy.watched) tr(UiText.library_privacy_dialog_015) else tr(UiText.library_privacy_dialog_016)), { onSave(policy.copy(watched = !policy.watched)) },
                    Modifier.fillMaxWidth().testTag("privacy_watched"), enabled = !busy && state.account != null)
                Text(tr(UiText.library_privacy_dialog_020), style = MaterialTheme.typography.titleMedium)
                LibraryData.entries.forEach { item -> TvActionButton("${if (item in selected) "✓" else "□"} ${labels.getValue(item)}", {
                    selected = if (item in selected) selected - item else selected + item
                }, Modifier.fillMaxWidth().testTag("privacy_select_${item.name}"), enabled = !busy && state.account != null) }
                TvActionButton(tr(UiText.library_privacy_dialog_021), { confirmation = true }, Modifier.fillMaxWidth().testTag("privacy_delete"),
                    enabled = !busy && selected.isNotEmpty() && state.account != null)
                Text(tr(UiText.library_privacy_dialog_022), style = MaterialTheme.typography.bodySmall)
                TvActionButton(tr(UiText.language_settings_dialog_005), onDismiss, Modifier.fillMaxWidth(), enabled = !busy)
            }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        }
        LaunchedEffect(confirmation, state.account) { withFrameNanos { }; first.requestFocus() }
    }
}
