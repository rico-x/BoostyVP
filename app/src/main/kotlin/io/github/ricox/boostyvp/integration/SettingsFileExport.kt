package io.github.ricox.boostyvp.integration

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** A separate provider exposes only prepared export copies, never app preferences or updates. */
class SettingsExportProvider : FileProvider()

internal class SettingsFileExport(private val context: Context) {
    companion object { const val MIME = "application/json" }

    @Suppress("DEPRECATION") // This overload also supports Android 6–12.
    private fun handlers(intent: Intent, method: ExportMethod): List<ExportApp> =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).mapNotNull { result ->
            val info = result.activityInfo ?: return@mapNotNull null
            if (!info.enabled || !info.applicationInfo.enabled || !info.exported) return@mapNotNull null
            ExportApp(info.packageName, info.name,
                info.applicationInfo.loadLabel(context.packageManager).toString(), method)
        }

    fun apps(): List<ExportApp> {
        val creators = handlers(documentIntent(), ExportMethod.CreateDocument)
        val browsers = listOf(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT).flatMap { action ->
            handlers(Intent(action).addCategory(Intent.CATEGORY_OPENABLE).setType(MIME), ExportMethod.SaveCopy)
        }.map { it.packageName }.toSet()
        val receivers = handlers(Intent(Intent.ACTION_SEND).setType(MIME), ExportMethod.SaveCopy)
        return selectExportApps(creators, browsers, receivers)
    }

    fun filename(now: Long = System.currentTimeMillis()): String =
        "BoostyVP-settings-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(now))}.json"

    fun documentIntent(app: ExportApp? = null, filename: String? = null): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(MIME).apply {
            app?.let { component = ComponentName(it.packageName, it.activityName) }
            filename?.let { putExtra(Intent.EXTRA_TITLE, it) }
        }

    /** Each URI is immutable after launch; a later export cannot overwrite an earlier recipient's copy. */
    fun prepareCopy(text: String, filename: String): Uri {
        BackupSchema.decode(text)
        require(filename.matches(Regex("BoostyVP-settings-[0-9-]+\\.json")))
        val directory = File(context.cacheDir, "settings-exports/${UUID.randomUUID()}")
        check(directory.mkdirs())
        val file = File(directory, filename)
        try {
            file.writeText(text, Charsets.UTF_8)
            return FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
        } catch (e: Exception) {
            file.delete(); directory.delete()
            throw e
        }
    }

    fun copyIntent(app: ExportApp, uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        component = ComponentName(app.packageName, app.activityName)
        type = MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "BoostyVP", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
