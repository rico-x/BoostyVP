package io.github.ricox.boostyvp.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ricox.boostyvp.integration.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Real APK/certificate verification without publishing a test release or installing a fixture. */
@RunWith(AndroidJUnit4::class)
class UpdateVerificationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(): File {
        check(context.packageName.endsWith(".debug"))
        val path = InstrumentationRegistry.getArguments().getString("updateFixture")
        assumeTrue("Opt-in signed synthetic update fixture", path != null && File(path).isFile)
        return File(path!!)
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    @Suppress("DEPRECATION")
    private fun signer(file: File): String {
        val archive = context.packageManager.getPackageArchiveInfo(file.path, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)!!
        return hash(archive.signingInfo!!.apkContentsSigners.single().toByteArray())
    }
    private fun info(file: File) = AvailableUpdate("2026.10.24.1", 26102401, "Synthetic notes", "boostyvp-v2026.10.24.1-universal.apk",
        "https://github.com/rico-x/BoostyVP/releases/download/v2026.10.24.1/boostyvp-v2026.10.24.1-universal.apk", hash(file.readBytes()), file.length(), signer(file))
    private fun client(bytes: ByteArray, info: AvailableUpdate, corrupt: Boolean = false) = OkHttpClient.Builder().addInterceptor { chain ->
        val path = chain.request().url.encodedPath
        val body = when {
            path.endsWith("/releases/latest") -> """{"draft":false,"prerelease":false,"body":"Synthetic notes","assets":[{"name":"release-manifest.json","browser_download_url":"https://github.com/rico-x/BoostyVP/releases/download/v2026.10.24.1/release-manifest.json"},{"name":"${info.filename}","browser_download_url":"${info.url}"}]}""".toByteArray()
            path.endsWith("release-manifest.json") -> """{"applicationId":"io.github.ricox.boostyvp","versionName":"${info.version}","versionCode":${info.code},"signerCertificateSha256":"${info.signer}","assets":[{"file":"${info.filename}","bytes":${info.bytes},"sha256":"${info.hash}"}]}""".toByteArray()
            else -> if (corrupt) bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() } else bytes
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody("application/octet-stream".toMediaType())).build()
    }.build()

    @Test fun completeReleaseCheckDownloadAndAndroidArchiveVerificationAcceptMatchingHigherVersion() = runBlocking<Unit> {
        val file = fixture(); val expected = info(file)
        val updates = AppUpdates(context, client(file.readBytes(), expected))
        val actual = updates.check()
        assertEquals(expected, actual)
        val downloaded = updates.download(actual)
        assertTrue(downloaded.isFile); assertEquals(expected.hash, hash(downloaded.readBytes()))
        updates.validateApk(downloaded, actual)
        downloaded.delete()
    }
    @Test fun corruptDownloadWrongSignerAndDowngradeNeverReachInstaller() = runBlocking<Unit> {
        val file = fixture(); val expected = info(file)
        val corrupt = AppUpdates(context, client(file.readBytes(), expected, corrupt = true))
        assertTrue(runCatching { corrupt.download(expected) }.isFailure)
        val updates = AppUpdates(context, client(file.readBytes(), expected))
        assertTrue(runCatching { updates.download(expected.copy(code = updates.currentCode)) }.isFailure)
        assertTrue(runCatching { updates.validateApk(file, expected.copy(code = expected.code + 1)) }.isFailure)
        assertTrue(runCatching { updates.validateApk(file, expected.copy(signer = "0".repeat(64))) }.isFailure)
        val otherSigner = File(file.parentFile, "other-signer.apk")
        assertTrue("Second synthetic signer fixture is required", otherSigner.isFile)
        assertTrue(runCatching { updates.validateApk(otherSigner, info(otherSigner)) }.isFailure)
        // Installed .debug APK is not a higher version, even though its signature/package are valid.
        assertTrue(runCatching { updates.validateApk(File(context.applicationInfo.sourceDir), expected) }.isFailure)
    }
}
