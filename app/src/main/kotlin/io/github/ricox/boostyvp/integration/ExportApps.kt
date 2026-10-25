package io.github.ricox.boostyvp.integration

internal enum class ExportMethod { CreateDocument, SaveCopy }

internal data class ExportApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val method: ExportMethod,
)

/** Browsing documents alone does not imply CREATE_DOCUMENT support on Android TV. */
internal fun selectExportApps(
    creators: List<ExportApp>,
    documentBrowsers: Set<String>,
    copyReceivers: List<ExportApp>,
): List<ExportApp> {
    fun usable(app: ExportApp) = app.packageName != "com.android.tv.frameworkpackagestubs"
    // SEND also matches players/torrent clients. Offer save-copy only for apps
    // that additionally declare a document picker, never every share receiver.
    return (creators.filter(::usable) + copyReceivers.filter {
        usable(it) && it.packageName in documentBrowsers
    }).distinctBy { it.packageName }.sortedBy { it.label.lowercase(java.util.Locale.ROOT) }
}
