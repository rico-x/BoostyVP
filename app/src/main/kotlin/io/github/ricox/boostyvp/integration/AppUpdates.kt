package io.github.ricox.boostyvp.integration

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

data class AvailableUpdate(val version: String, val code: Long, val notes: String,
    val filename: String, val url: String, val hash: String, val bytes: Long, val signer: String)

object UpdatePolicy {
    const val MAX_BYTES = 120L * 1024 * 1024
    const val REPOSITORY = "https://github.com/rico-x/BoostyVP/"
    fun trustedAsset(url: String): Boolean = runCatching {
        val u = url.toHttpUrl()
        u.isHttps && u.username.isEmpty() && u.password.isEmpty() && u.host == "github.com" &&
            u.encodedPath.startsWith("/rico-x/BoostyVP/releases/download/") && u.query == null && u.fragment == null
    }.getOrDefault(false)
    fun downloadableHost(host: String) = host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    fun validate(info: AvailableUpdate, packageId: String, ownPackage: String, code: Long, currentCode: Long, signer: String) {
        require(packageId == ownPackage && code == info.code && code > currentCode) { tr(UiText.app_updates_001) }
        require(info.signer.equals(signer, true)) { tr(UiText.app_updates_002) }
        require(info.hash.matches(Regex("[a-fA-F0-9]{64}")) && info.bytes in 1..MAX_BYTES && trustedAsset(info.url)) { tr(UiText.app_updates_003) }
    }
}

class AppUpdates(private val context: Context, private val client: OkHttpClient) {
    @Inject constructor(@ApplicationContext context: Context) : this(context,
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build())
    private fun get(url: String) = client.newCall(Request.Builder().url(url)
        .header("User-Agent", "BoostyVP-update-check").header("Accept", "application/vnd.github+json").build()).execute()
    private fun text(url: String): String = get(url).use { response ->
        require(response.request.url.isHttps && (response.request.url.host == "api.github.com" || UpdatePolicy.downloadableHost(response.request.url.host))) { tr(UiText.app_updates_004) }
        require(response.isSuccessful) { if (response.code == 404) tr(UiText.app_updates_005) else tr(UiText.app_updates_006, response.code) }
        val body = requireNotNull(response.body)
        require(body.contentLength() <= 512 * 1024) { tr(UiText.app_updates_007) }
        body.byteStream().use { stream -> val bytes = stream.readBytesLimited(512 * 1024); bytes.toString(Charsets.UTF_8) }
    }
    val currentVersion: String get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    @Suppress("DEPRECATION")
    val currentCode: Long get() = context.packageManager.getPackageInfo(context.packageName, 0).let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }

    suspend fun check(): AvailableUpdate = withContext(Dispatchers.IO) {
        val release = Json.parseToJsonElement(text("https://api.github.com/repos/rico-x/BoostyVP/releases/latest")).jsonObject
        require(release["draft"]?.jsonPrimitive?.boolean == false && release["prerelease"]?.jsonPrimitive?.boolean == false)
        val assets = release.getValue("assets").jsonArray.map { it.jsonObject }
        val manifestUrl = assets.first { it["name"]?.jsonPrimitive?.content == "release-manifest.json" }.getValue("browser_download_url").jsonPrimitive.content
        require(UpdatePolicy.trustedAsset(manifestUrl))
        val manifest = Json.parseToJsonElement(text(manifestUrl)).jsonObject
        require(manifest["applicationId"]?.jsonPrimitive?.content == "io.github.ricox.boostyvp")
        val version = manifest.getValue("versionName").jsonPrimitive.content
        require(version.matches(Regex("20\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{1,2}")))
        val filename = "boostyvp-v$version-universal.apk"
        val item = manifest.getValue("assets").jsonArray.map { it.jsonObject }.first { it["file"]?.jsonPrimitive?.content == filename }
        val url = assets.first { it["name"]?.jsonPrimitive?.content == filename }.getValue("browser_download_url").jsonPrimitive.content
        val info = AvailableUpdate(version, manifest.getValue("versionCode").jsonPrimitive.long,
            release["body"]?.jsonPrimitive?.content.orEmpty().take(24_000), filename, url,
            item.getValue("sha256").jsonPrimitive.content, item.getValue("bytes").jsonPrimitive.long,
            manifest.getValue("signerCertificateSha256").jsonPrimitive.content)
        require(UpdatePolicy.trustedAsset(info.url) && info.bytes in 1..UpdatePolicy.MAX_BYTES && info.hash.matches(Regex("[a-fA-F0-9]{64}")))
        info
    }

    suspend fun download(info: AvailableUpdate): File = withContext(Dispatchers.IO) {
        require(info.code > currentCode) { tr(UiText.app_updates_008) }
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(directory, "update.part")
        val final = File(directory, "boostyvp-update.apk")
        try {
            get(info.url).use { response ->
                require(response.isSuccessful && response.request.url.isHttps && UpdatePolicy.downloadableHost(response.request.url.host)) { tr(UiText.app_updates_009) }
                val body = requireNotNull(response.body)
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                body.byteStream().use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer); if (n < 0) break
                        count += n; require(count <= info.bytes && count <= UpdatePolicy.MAX_BYTES) { tr(UiText.app_updates_010) }
                        digest.update(buffer, 0, n); output.write(buffer, 0, n)
                    }
                } }
                require(count == info.bytes && digest.digest().hex().equals(info.hash, true)) { tr(UiText.app_updates_011) }
            }
            validateApk(partial, info)
            check(partial.renameTo(final)) { tr(UiText.app_updates_012) }
            final
        } finally { partial.delete() }
    }

    @Suppress("DEPRECATION")
    fun validateApk(file: File, info: AvailableUpdate) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pm = context.packageManager
        val archive = requireNotNull(pm.getPackageArchiveInfo(file.absolutePath, flags)) { tr(UiText.app_updates_013) }
        val installed = pm.getPackageInfo(context.packageName, flags)
        fun certificates(pkg: android.content.pm.PackageInfo) = if (Build.VERSION.SDK_INT >= 28)
            pkg.signingInfo?.apkContentsSigners.orEmpty() else pkg.signatures.orEmpty()
        val own = certificates(installed).map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet()
        val incoming = certificates(archive).map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet()
        require(own.isNotEmpty() && incoming == own) { tr(UiText.app_updates_014) }
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        UpdatePolicy.validate(info, archive.packageName, context.packageName, code, currentCode, incoming.single())
        require(archive.versionName == info.version) { tr(UiText.app_updates_015) }
    }
}
private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
private fun java.io.InputStream.readBytesLimited(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) { val n = read(buffer); if (n < 0) break; require(out.size() + n <= max); out.write(buffer, 0, n) }
    return out.toByteArray()
}
