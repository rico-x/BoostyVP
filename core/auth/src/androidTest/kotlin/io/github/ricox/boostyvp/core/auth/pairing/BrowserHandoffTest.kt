package io.github.ricox.boostyvp.core.auth.pairing

import io.github.ricox.boostyvp.core.domain.auth.pairing.PairingStartResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ricox.boostyvp.core.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/** Uses a separate test APK and synthetic session; never changes the TV account. */
@RunWith(AndroidJUnit4::class)
class BrowserHandoffTest {
    @Test
    fun packagedBrowserPageAndEncryptedHandoffWorkOnAndroid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = PairingServer(Json, scope, LanAddress(context), advertisedHost = "127.0.0.1")
        val accepted = AtomicReference<Session>()
        try {
            val result = server.start(onPaired = { accepted.set(it) }) as PairingStartResult.Ready
            val get = URL(result.offer.url).openConnection() as HttpURLConnection
            try {
                get.connectTimeout = 2_000
                get.readTimeout = 2_000
                assertEquals(200, get.responseCode)
                val page = get.inputStream.bufferedReader().use { it.readText() }
                assertTrue(page.contains("id=\"bookmark-url\""))
                assertTrue(page.contains("iPhone / iPad"))
                assertTrue(!page.contains("%%"))
            } finally {
                get.disconnect()
            }
            val payload = """{"auth":"{\"accessToken\":\"test-access\",\"refreshToken\":\"test-refresh\"}","clientId":"test-device"}"""
            val sealed = PairingCrypto.seal(PairingCrypto.decodeKey(result.offer.encodedKey), payload)
            val post = URL("http://127.0.0.1:${result.offer.port}/pair").openConnection() as HttpURLConnection
            try {
                post.connectTimeout = 2_000
                post.readTimeout = 2_000
                post.requestMethod = "POST"
                post.doOutput = true
                post.setRequestProperty("Content-Type", "text/plain")
                post.outputStream.use { it.write(sealed.toByteArray()) }
                assertEquals(200, post.responseCode)
                assertEquals("test-access", accepted.get()?.accessToken)
                assertEquals("test-device", accepted.get()?.deviceId)
            } finally {
                post.disconnect()
            }
        } finally {
            server.stop()
            scope.cancel()
        }
    }
}
