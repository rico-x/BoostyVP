package io.github.ricox.boostyvp.core.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.ricox.boostyvp.core.domain.auth.AuthException
import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import io.github.ricox.boostyvp.core.domain.auth.TokenStore
import io.github.ricox.boostyvp.core.model.Session
import io.github.ricox.boostyvp.core.network.di.NetworkModule
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PhoneAndRefreshProtocolTest {
    private class Store(initial: Session) : TokenStore {
        override val session = MutableStateFlow<Session?>(initial)
        override suspend fun peek() = session.value
        override suspend fun save(session: Session) { this.session.value = session }
        override suspend fun clear() { session.value = null }
    }

    private class Fixture : AutoCloseable {
        val server = MockWebServer().apply { start() }
        val refreshClient = NetworkModule.provideRefreshOkHttpClient()
        val store = Store(Session("stale", "refresh+0", Long.MAX_VALUE, "test-device"))
        val refresher = BoostyTokenRefresher(api(refreshClient))
        val sessions = SessionManager(store, refresher)
        val client = NetworkModule.provideOkHttpClient(sessions)
        fun api(client: OkHttpClient): BoostyApi = Retrofit.Builder().baseUrl(server.url("/"))
            .client(client).addConverterFactory(NetworkModule.provideJson()
                .asConverterFactory("application/json".toMediaType())).build().create(BoostyApi::class.java)
        override fun close() {
            client.dispatcher.executorService.shutdownNow()
            refreshClient.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
            refreshClient.connectionPool.evictAll()
            server.shutdown()
        }
    }

    private fun token(access: String = "fresh", refresh: String = "rotated") = MockResponse()
        .setBody("""{"access_token":"$access","refresh_token":"$refresh","expires_in":3600}""")

    private fun RecordedRequest.form(): Map<String, String> = body.readUtf8().split('&').associate {
        val pair = it.split('=', limit = 2)
        URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
    }

    @Test(timeout = 10_000)
    fun `each refresh uses the newly rotated token and persists before returning`() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(token("fresh-1", "refresh+1"))
            f.server.enqueue(token("fresh-2", "refresh+2"))
            val first = f.sessions.refreshIfNeeded("stale")
            val second = f.sessions.refreshIfNeeded(first.accessToken)
            assertEquals(second, f.store.peek())
            assertEquals("refresh+2", second.refreshToken)
            assertTrue(second.expiresAt > System.currentTimeMillis() / 1000)
            for (oldRefresh in listOf("refresh+0", "refresh+1")) {
                val request = f.server.takeRequest()
                assertEquals("/oauth/token/", request.path)
                assertEquals("POST", request.method)
                assertEquals(mapOf("device_id" to "test-device", "device_os" to "web",
                    "grant_type" to "refresh_token", "refresh_token" to oldRefresh), request.form())
                assertEquals("test-device", request.getHeader("X-From-Id"))
                assertEquals("web", request.getHeader("X-App"))
                assertNull(request.getHeader("Authorization"))
            }
        }
    }

    @Test(timeout = 10_000)
    fun `concurrent 401 responses refresh once and replay with the persisted session`() = runBlocking {
        Fixture().use { f ->
            val oldRequests = CountDownLatch(6)
            val refreshes = AtomicInteger()
            f.client.dispatcher.maxRequestsPerHost = 12
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path == "/oauth/token/" -> { refreshes.incrementAndGet(); token() }
                    request.getHeader("Authorization") == "Bearer stale" -> {
                        oldRequests.countDown()
                        oldRequests.await(3, TimeUnit.SECONDS)
                        MockResponse().setResponseCode(401)
                    }
                    else -> {
                        assertEquals("Bearer fresh", request.getHeader("Authorization"))
                        assertEquals("rotated", f.store.session.value?.refreshToken)
                        MockResponse().setBody("""{"blogUrl":"creator"}""")
                    }
                }
            }
            val result = (1..6).map { async { f.api(f.client).getBlog("creator").blogUrl } }.awaitAll()
            assertTrue(result.all { it == "creator" })
            assertEquals(1, refreshes.get())
        }
    }

    @Test(timeout = 10_000)
    fun `transient refresh failures retain the session and invalid grants are distinguished`() = runBlocking {
        Fixture().use { f ->
            val before = f.store.peek()
            f.server.enqueue(MockResponse().setResponseCode(503))
            assertTrue(runCatching { f.sessions.refreshIfNeeded("stale") }.isFailure)
            assertEquals(before, f.store.peek())
            f.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))
            assertTrue(runCatching { f.refresher.refresh(before!!) }.exceptionOrNull() is AuthException.Unrecoverable)
        }
    }

    @Test(timeout = 10_000)
    fun `phone login uses the official two requests and the same device id`() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(MockResponse().setBody("""{"data":{"phoneCode":{"code":"challenge-id","sentTransport":"sms"}}}"""))
            f.server.enqueue(token())
            val login = BoostyPhoneLogin(f.api(f.refreshClient))
            val challenge = login.requestCode("+79990000000", "phone-device")
            val session = login.confirmCode("+79990000000", challenge.verificationId, "123456", "phone-device")
            assertEquals("phone-device", session.deviceId)
            assertEquals("rotated", session.refreshToken)
            val send = f.server.takeRequest()
            assertEquals("POST", send.method)
            assertEquals("/auth/phone/verification_code/send", send.path)
            assertEquals(mapOf("phone" to "+79990000000", "device_id" to "phone-device", "device_os" to "web"), send.form())
            val confirm = f.server.takeRequest()
            assertEquals("PUT", confirm.method)
            assertEquals("/auth/phone/verification_code/confirm", confirm.path)
            assertEquals(mapOf("phone" to "+79990000000", "device_id" to "phone-device",
                "device_os" to "web", "code" to "challenge-id", "sms_code" to "123456"), confirm.form())
            assertNull(send.getHeader("Authorization"))
            assertNull(confirm.getHeader("Authorization"))
        }
    }

    @Test(timeout = 10_000)
    fun `captcha challenges are reported and never bypassed`() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_check"}"""))
            val failure = runCatching { BoostyPhoneLogin(f.api(f.refreshClient))
                .requestCode("+79990000000", "phone-device") }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty().contains("CAPTCHA"))
            assertEquals(1, f.server.requestCount)
        }
    }
}
