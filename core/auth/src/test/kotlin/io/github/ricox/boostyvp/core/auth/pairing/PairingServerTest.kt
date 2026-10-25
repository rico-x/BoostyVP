package io.github.ricox.boostyvp.core.auth.pairing

import io.github.ricox.boostyvp.core.domain.auth.pairing.PairingOffer
import io.github.ricox.boostyvp.core.domain.auth.pairing.PairingStartResult
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

class PairingServerTest {
    private val language = io.github.ricox.boostyvp.localization.Language.selected
    @org.junit.Before fun chooseRussian() { io.github.ricox.boostyvp.localization.Language.selected = "ru" }
    @org.junit.After fun restoreLanguage() { io.github.ricox.boostyvp.localization.Language.selected = language }
    @Test(timeout = 10_000)
    fun `relay offer advertises external host while serving the fixed local port`() = runBlocking {
        mockkObject(PairingCrypto)
        every { PairingCrypto.newKey() } returns ByteArray(32)
        every { PairingCrypto.encodeKey(any()) } returns "B".repeat(43)
        val port = LanAddress.freePort()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = PairingServer(Json, scope, mockk(), advertisedHost = "192.168.2.20", listenPort = port)
        try {
            val result = server.start(onPaired = {}) as PairingStartResult.Ready
            assertEquals("192.168.2.20", result.offer.host)
            assertEquals(port, result.offer.port)
            assertEquals("http://192.168.2.20:$port/link", result.offer.url)
            val connection = URL("http://127.0.0.1:$port/link")
                .openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                assertEquals(200, connection.responseCode)
                val page = connection.inputStream.bufferedReader().use { it.readText() }
                assertTrue(page.contains("iPhone / iPad"))
                assertTrue(page.contains("Инструкция для компьютера"))
                assertTrue(page.contains("id=\"send-session\""))
                assertTrue(page.contains("id=\"bookmark-url\""))
                assertTrue(page.contains("action=\"/p/${result.offer.encodedKey}\""))
                assertTrue(!page.contains("%%"))
            } finally {
                connection.disconnect()
            }
        } finally {
            server.stop()
            scope.cancel()
            unmockkObject(PairingCrypto)
        }
    }

    @Test(timeout = 10_000)
    fun `browser encrypted envelope is authenticated and accepted only once`() = runBlocking {
        mockkObject(PairingCrypto)
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        val key = ByteArray(32) { 7 }
        every { PairingCrypto.newKey() } returns key
        every { PairingCrypto.encodeKey(any()) } returns java.util.Base64.getUrlEncoder()
            .withoutPadding().encodeToString(key)
        every { PairingCrypto.open(any(), any()) } answers {
            // Android's Base64 is the only unavailable JVM boundary. Use the
            // actual AES-GCM wire layout, including the browser's 12-byte IV.
            val bytes = java.util.Base64.getDecoder().decode(secondArg<String>())
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(firstArg<ByteArray>(), "AES"),
                javax.crypto.spec.GCMParameterSpec(128, bytes.copyOfRange(0, 12)),
            )
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }
        val address = mockk<LanAddress>()
        every { address.find() } returns LanAddress.Result.Found("127.0.0.1", "test")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = PairingServer(Json, scope, address)
        val accepted = AtomicInteger()
        try {
            val result = server.start(onPaired = {
                assertEquals("test-access", it.accessToken)
                assertEquals("test-device", it.deviceId)
                accepted.incrementAndGet()
            }) as PairingStartResult.Ready
            fun submit(body: String): Int {
                val connection = URL("http://127.0.0.1:${result.offer.port}/pair")
                    .openConnection() as HttpURLConnection
                return try {
                    connection.connectTimeout = 2_000
                    connection.readTimeout = 2_000
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "text/plain")
                    connection.outputStream.use { it.write(body.toByteArray()) }
                    connection.responseCode
                } finally {
                    connection.disconnect()
                }
            }

            // Web Crypto fixture also checked by tools/test_browser_pairing.mjs.
            val sealed = checkNotNull(javaClass.getResourceAsStream("/pairing/browser-envelope.txt"))
                .bufferedReader().use { it.readText().trim() }
            val tampered = sealed.replaceRange(20, 21, if (sealed[20] == 'A') "B" else "A")
            assertNotEquals(200, submit(tampered))
            assertEquals(0, accepted.get())
            assertEquals(200, submit(sealed))
            assertNotEquals(200, runCatching { submit(sealed) }.getOrNull())
            assertEquals(1, accepted.get())
        } finally {
            server.stop()
            scope.cancel()
            unmockkObject(PairingCrypto)
            unmockkStatic(Log::class)
        }
    }

    @Test(timeout = 10_000)
    fun `a pairing offer accepts only one session`() = runBlocking {
        // Only Android's Base64 boundary needs replacing for this JVM test.
        // Requests still use the actual TCP server, form parser and session parser.
        mockkObject(PairingCrypto)
        every { PairingCrypto.newKey() } returns ByteArray(32)
        every { PairingCrypto.encodeKey(any()) } returns "A".repeat(43)
        val address = mockk<LanAddress>()
        every { address.find() } returns LanAddress.Result.Found("127.0.0.1", "test")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = PairingServer(Json, scope, address)
        val accepted = AtomicInteger()

        try {
            val result = server.start(onPaired = { accepted.incrementAndGet() })
                as PairingStartResult.Ready
            fun submit(): Int {
                val connection = URL("http://127.0.0.1:${result.offer.port}/p/${result.offer.encodedKey}")
                    .openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 2_000
                    connection.readTimeout = 2_000
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    val auth = """{"accessToken":"access","refreshToken":"refresh"}"""
                    val body = "auth=${URLEncoder.encode(auth, "UTF-8")}&clientId=test"
                    connection.outputStream.use { it.write(body.toByteArray()) }
                    return connection.responseCode
                } finally {
                    connection.disconnect()
                }
            }

            assertEquals(200, submit())
            // The endpoint may already be closed if the shutdown grace elapsed.
            assertNotEquals(200, runCatching { submit() }.getOrNull())
            assertEquals(1, accepted.get())
        } finally {
            server.stop()
            scope.cancel()
            unmockkObject(PairingCrypto)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test(timeout = 10_000)
    fun `short link keeps working after a day and reopens with a fresh internal key`() = runTest {
        mockkObject(PairingCrypto)
        every { PairingCrypto.newKey() } returns ByteArray(32)
        every { PairingCrypto.encodeKey(any()) } returnsMany listOf("A".repeat(43), "B".repeat(43))
        val port = LanAddress.freePort()
        val server = PairingServer(Json, this, mockk(), advertisedHost = "127.0.0.1", listenPort = port)
        fun get(path: String): Pair<Int, String> {
            val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
            return try {
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                connection.responseCode to (if (connection.responseCode == 200)
                    connection.inputStream.bufferedReader().use { it.readText() } else "")
            } finally { connection.disconnect() }
        }
        try {
            val first = (server.start({}) as PairingStartResult.Ready).offer
            assertEquals(200, get("/link").first)
            advanceTimeBy(24 * 60 * 60 * 1_000L)
            runCurrent()
            assertTrue(get("/link").second.contains(first.encodedKey))
            val second = (server.start({}) as PairingStartResult.Ready).offer
            assertEquals(first.url, second.url)
            assertNotEquals(first.encodedKey, second.encodedKey)
            assertFalse(get("/link").second.contains(first.encodedKey))
            assertEquals(404, get("/p/${first.encodedKey}").first)
        } finally {
            server.stop()
            unmockkObject(PairingCrypto)
        }
    }
}
