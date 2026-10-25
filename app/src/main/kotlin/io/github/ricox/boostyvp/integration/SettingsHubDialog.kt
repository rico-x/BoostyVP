package io.github.ricox.boostyvp.integration

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import android.content.Intent
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.ricox.boostyvp.core.auth.pairing.LanAddress
import io.github.ricox.boostyvp.core.auth.pairing.SettingsTransferServer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.ui.appearance.*
import io.github.ricox.boostyvp.core.ui.player.*
import io.github.ricox.boostyvp.core.ui.component.TvActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsHubDialog(viewModel: IntegrationViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup = remember { SettingsBackup(context) }
    var section by remember { mutableStateOf("main") }
    var confirmReset by remember { mutableStateOf(false) }
    var localMessage by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf(TvChannelPreferences(context).load()) }
    var transferUrl by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var server by remember { mutableStateOf<SettingsTransferServer?>(null) }
    val fileExport = remember(context) { SettingsFileExport(context) }
    var exportApps by remember { mutableStateOf(emptyList<ExportApp>()) }
    var showExportApps by remember { mutableStateOf(false) }
    var exportBusy by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf("") }
    var documentText by rememberSaveable { mutableStateOf<String?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) {
            server?.stop(); server = null; transferUrl = null; pendingImport = null
        } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); server?.stop() }
    }
    LaunchedEffect(section, transferUrl) {
        if (section != "transfer") { server?.stop(); server = null; transferUrl = null; pendingImport = null }
        else if (transferUrl != null) { kotlinx.coroutines.delay(10 * 60_000L); server?.stop(); server = null; transferUrl = null; pendingImport = null; localMessage = tr(UiText.settings_hub_dialog_001) }
    }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val available by viewModel.available.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = documentText
        documentText = null
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null && text != null) scope.launch {
            localMessage = try { withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(text) }
            }; tr(UiText.settings_hub_dialog_002) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { tr(UiText.settings_hub_dialog_003) }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            localMessage = try {
                val text = withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                    while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= 64 * 1024); out.write(buffer, 0, n) }
                    out.toString("UTF-8")
                } }
                withContext(Dispatchers.IO) { backup.restore(text) }
                channels = TvChannelPreferences(context).load(); viewModel.refreshChannels(); applyImportedLanguage(context)
                tr(UiText.settings_hub_dialog_004)
            } catch (_: Exception) { tr(UiText.settings_hub_dialog_005) }
        }
    }
    fun openBrowserTransfer() {
        scope.launch {
            try {
                val created = SettingsTransferServer(backup::export) { text ->
                    BackupSchema.decode(text)
                    scope.launch { pendingImport = text }
                }
                val address = withContext(Dispatchers.IO) {
                    val host = (LanAddress(context).find() as? LanAddress.Result.Found)?.ip
                        ?: error(tr(UiText.settings_hub_dialog_026))
                    created.start(host)
                }
                server = created; transferUrl = address; section = "transfer"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { localMessage = tr(UiText.settings_hub_dialog_027) }
        }
    }
    if (section == "player") {
        val prefs = remember { PlayerPreferences(context) }; var options by remember { mutableStateOf(prefs.load()) }
        PlayerSettingsDialog(options, { options = it; prefs.save(it) }, { section = "main" }); return
    }
    if (section == "interface") { InterfaceSettingsDialog { section = "main" }; return }
    if (section == "privacy") { LibraryPrivacyDialog({ section = "main" }); return }
    if (section == "menu") { MenuSettingsDialog { section = "main" }; return }
    if (section == "language") { LanguageSettingsDialog { section = "main" }; return }
    val first = remember(section) { FocusRequester() }
    Dialog(onDismissRequest = { if (section == "main") onDismiss() else section = "main" },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(Modifier.width(720.dp).heightIn(max = 480.dp).background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()).padding(24.dp).testTag("settings_hub"),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(when (section) { "tv" -> tr(UiText.settings_hub_dialog_006); "backup" -> tr(UiText.settings_hub_dialog_007); "updates" -> tr(UiText.settings_hub_dialog_008); else -> tr(UiText.settings_hub_dialog_009) }, style = MaterialTheme.typography.headlineSmall)
            when (section) {
                "main" -> {
                    TvActionButton(tr(UiText.settings_hub_dialog_010), { section = "player" }, Modifier.fillMaxWidth().focusRequester(first))
                    TvActionButton(tr(UiText.settings_hub_dialog_011), { section = "interface" }, Modifier.fillMaxWidth())
                    TvActionButton(tr(UiText.settings_hub_dialog_012), { section = "menu" }, Modifier.fillMaxWidth().testTag("settings_menu"))
                    TvActionButton(tr(UiText.language_settings_dialog_001), { section = "language" }, Modifier.fillMaxWidth().testTag("settings_language"))
                    TvActionButton(tr(UiText.library_privacy_dialog_009), { section = "privacy" }, Modifier.fillMaxWidth().testTag("settings_privacy"))
                    TvActionButton(tr(UiText.settings_hub_dialog_006), { section = "tv" }, Modifier.fillMaxWidth().testTag("settings_tv"))
                    TvActionButton(tr(UiText.settings_hub_dialog_007), { section = "backup" }, Modifier.fillMaxWidth())
                    TvActionButton(tr(UiText.settings_hub_dialog_013), { section = "updates" }, Modifier.fillMaxWidth())
                }
                "tv" -> {
                    fun save(value: TvChannelOptions) { channels = value.normalized(); TvChannelPreferences(context).save(channels); viewModel.refreshChannels() }
                    TvActionButton(tr(UiText.settings_hub_dialog_016, if (channels.enabled) tr(UiText.settings_hub_dialog_014) else tr(UiText.settings_hub_dialog_015)), { save(channels.copy(enabled = !channels.enabled)) }, Modifier.fillMaxWidth().focusRequester(first))
                    TvActionButton(tr(UiText.settings_hub_dialog_017, channels.count), { save(channels.copy(count = channels.count % TvFeedSource.entries.size + 1)) }, Modifier.fillMaxWidth().testTag("tv_channel_count"))
                    channels.sources.take(channels.count).forEachIndexed { index, source ->
                        TvActionButton(tr(UiText.settings_hub_dialog_018, index + 1, source.label), { save(channels.changeSource(index)) }, Modifier.fillMaxWidth().testTag("tv_source_$index"))
                    }
                    TvActionButton(tr(UiText.settings_hub_dialog_019, channels.cards), { save(channels.copy(cards = if (channels.cards >= 50) 5 else channels.cards + 5)) }, Modifier.fillMaxWidth())
                    TvActionButton(tr(UiText.settings_hub_dialog_020, if (channels.watchNext) tr(UiText.library_privacy_dialog_015) else tr(UiText.library_privacy_dialog_016)), { save(channels.copy(watchNext = !channels.watchNext)) }, Modifier.fillMaxWidth())
                    TvActionButton(tr(UiText.settings_hub_dialog_021), viewModel::refreshChannels, Modifier.fillMaxWidth(), enabled = !busy)
                    TvActionButton(tr(UiText.settings_hub_dialog_022), {
                        try { context.startActivity(Intent("android.media.tv.action.REQUEST_CHANNEL_BROWSABLE").apply {
                            putExtra("android.media.tv.extra.CHANNEL_ID", findUnapprovedChannel(context) ?: return@TvActionButton)
                        }) } catch (_: Exception) { localMessage = tr(UiText.settings_hub_dialog_023) }
                    }, Modifier.fillMaxWidth())
                    Text(tr(UiText.settings_hub_dialog_024), style = MaterialTheme.typography.bodySmall)
                }
                "backup" -> {
                    TvActionButton(tr(UiText.settings_hub_dialog_025), ::openBrowserTransfer,
                        Modifier.fillMaxWidth().focusRequester(first))
                    TvActionButton(tr(UiText.settings_hub_dialog_028), {
                        exportBusy = true; localMessage = ""; exportError = ""
                        scope.launch {
                            try {
                                exportApps = withContext(Dispatchers.IO) { fileExport.apps() }
                                showExportApps = true
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { localMessage = tr(UiText.export_lookup_failed) }
                            finally { exportBusy = false }
                        }
                    }, Modifier.fillMaxWidth().testTag("settings_export_file"), enabled = !exportBusy)
                    TvActionButton(tr(UiText.settings_hub_dialog_030), { try { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } catch (_: Exception) { localMessage = tr(UiText.settings_hub_dialog_031) } }, Modifier.fillMaxWidth())
                    TvActionButton(if (confirmReset) tr(UiText.settings_hub_dialog_032) else tr(UiText.settings_hub_dialog_033), {
                        if (confirmReset) scope.launch {
                            withContext(Dispatchers.IO) { backup.reset() }
                            channels = TvChannelPreferences(context).load(); confirmReset = false
                            viewModel.refreshChannels(); localMessage = tr(UiText.settings_hub_dialog_034); applyImportedLanguage(context)
                        }
                        else confirmReset = true
                    }, Modifier.fillMaxWidth())
                    if (confirmReset) TvActionButton(tr(UiText.settings_hub_dialog_035), { confirmReset = false }, Modifier.fillMaxWidth())
                    Text(tr(UiText.settings_hub_dialog_036), style = MaterialTheme.typography.bodySmall)
                }
                "transfer" -> {
                    Text(tr(UiText.settings_hub_dialog_037), style = MaterialTheme.typography.bodySmall)
                    transferUrl?.let { url ->
                        val bitmap = remember(url) {
                            val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(url, com.google.zxing.BarcodeFormat.QR_CODE, 480, 480)
                            android.graphics.Bitmap.createBitmap(480, 480, android.graphics.Bitmap.Config.ARGB_8888).apply {
                                val pixels = IntArray(480 * 480) { n -> if (matrix[n % 480, n / 480]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                                setPixels(pixels, 0, 480, 0, 0, 480, 480)
                            }.asImageBitmap()
                        }
                        Image(bitmap, tr(UiText.settings_hub_dialog_038), Modifier.size(160.dp))
                        Text(url, style = MaterialTheme.typography.bodySmall)
                    }
                    TvActionButton(if (pendingImport != null) tr(UiText.settings_hub_dialog_039) else tr(UiText.settings_hub_dialog_040), {
                        pendingImport?.let { text ->
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { backup.restore(text) }
                                    channels = TvChannelPreferences(context).load(); pendingImport = null
                                    viewModel.refreshChannels(); localMessage = tr(UiText.settings_hub_dialog_041); applyImportedLanguage(context)
                                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (_: Exception) { localMessage = tr(UiText.settings_hub_dialog_042) }
                            }
                        }
                    }, Modifier.fillMaxWidth(), enabled = pendingImport != null)
                    if (pendingImport != null) TvActionButton(tr(UiText.settings_hub_dialog_043), { pendingImport = null }, Modifier.fillMaxWidth())
                }
                "updates" -> {
                    Text(tr(UiText.settings_hub_dialog_044, viewModel.updates.currentVersion))
                    TvActionButton(tr(UiText.settings_hub_dialog_045), viewModel::checkUpdates, Modifier.fillMaxWidth().focusRequester(first), enabled = !busy)
                    available?.let { update ->
                        Text(tr(UiText.settings_hub_dialog_046, update.version))
                        Text(update.notes.ifBlank { tr(UiText.settings_hub_dialog_047) }, style = MaterialTheme.typography.bodySmall)
                        if (update.code > viewModel.updates.currentCode && downloaded == null)
                            TvActionButton(tr(UiText.settings_hub_dialog_048), viewModel::downloadUpdate, Modifier.fillMaxWidth(), enabled = !busy)
                        if (downloaded != null) TvActionButton(tr(UiText.settings_hub_dialog_049), {
                            try {
                                if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls())
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                                else {
                                    viewModel.updates.validateApk(downloaded!!, update)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", downloaded!!)
                                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                }
                            } catch (_: Exception) { localMessage = tr(UiText.settings_hub_dialog_050) }
                        }, Modifier.fillMaxWidth(), enabled = !busy)
                    }
                }
            }
            if (localMessage.isNotBlank()) Text(localMessage, style = MaterialTheme.typography.bodySmall)
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
            TvActionButton(if (section == "main") tr(UiText.settings_hub_dialog_051) else tr(UiText.language_settings_dialog_005), { if (section == "main") onDismiss() else { section = "main"; localMessage = "" } },
                Modifier.fillMaxWidth().then(if (section == "transfer") Modifier.focusRequester(first) else Modifier))
        }
        LaunchedEffect(section) { withFrameNanos { }; first.requestFocus() }
    }
    if (showExportApps) SettingsExportDialog(exportApps, exportBusy, exportError, onPick = { app ->
        exportBusy = true; exportError = ""
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { backup.export() }
                val name = fileExport.filename()
                when (app.method) {
                    ExportMethod.CreateDocument -> {
                        documentText = text
                        exporter.launch(fileExport.documentIntent(app, name))
                    }
                    ExportMethod.SaveCopy -> {
                        val uri = withContext(Dispatchers.IO) { fileExport.prepareCopy(text, name) }
                        context.startActivity(fileExport.copyIntent(app, uri))
                        localMessage = tr(UiText.export_opened_in, app.label)
                    }
                }
                showExportApps = false
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                documentText = null
                exportError = tr(UiText.export_open_failed, app.label)
            } finally { exportBusy = false }
        }
    }, onBrowserTransfer = { showExportApps = false; openBrowserTransfer() },
        onDismiss = { showExportApps = false })
}

private fun applyImportedLanguage(context: android.content.Context) {
    val selected = LanguagePreferences.selected(context)
    if (selected != io.github.ricox.boostyvp.localization.Language.selected) LanguagePreferences.change(context, selected)
}

private fun findUnapprovedChannel(context: android.content.Context): Long? {
    if (Build.VERSION.SDK_INT < 26) return null
    return runCatching {
        context.contentResolver.query(androidx.tvprovider.media.tv.TvContractCompat.Channels.CONTENT_URI,
            arrayOf("_id", "package_name", "browsable", "internal_provider_id"), null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getString(1) == context.packageName && c.getInt(2) == 0 && c.getString(3)?.startsWith("boostyvp.slot.") == true) return@use c.getLong(0)
            null
        }
    }.getOrNull()
}
