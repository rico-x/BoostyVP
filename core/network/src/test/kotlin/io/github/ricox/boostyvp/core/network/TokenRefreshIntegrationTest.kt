package io.github.ricox.boostyvp.core.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import io.github.ricox.boostyvp.core.domain.auth.TokenStore
import io.github.ricox.boostyvp.core.model.Session
import io.github.ricox.boostyvp.core.network.di.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class TokenRefreshIntegrationTest {
    @Test(timeout = 10_000)
    fun `expired token can refresh while the API dispatcher is full`() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/oauth/token/" -> MockResponse().setBody(
                    """{"access_token":"fresh","refresh_token":"rotated","expires_in":3600}""",
                )
                else -> {
                    assertEquals("Bearer fresh", request.getHeader("Authorization"))
                    MockResponse().setBody("""{"blogUrl":"creator"}""")
                }
            }
        }
        server.start()

        val json = NetworkModule.provideJson()
        fun api(client: okhttp3.OkHttpClient) = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(BoostyApi::class.java)

        val refreshClient = NetworkModule.provideRefreshOkHttpClient()
        val store = object : TokenStore {
            override val session = MutableStateFlow<Session?>(Session("stale", "old", 0, "device"))
            override suspend fun peek() = session.value
            override suspend fun save(session: Session) { this.session.value = session }
            override suspend fun clear() { session.value = null }
        }
        val manager = SessionManager(store, BoostyTokenRefresher(api(refreshClient)))
        val client = NetworkModule.provideOkHttpClient(manager).newBuilder()
            .callTimeout(3, TimeUnit.SECONDS).build()
        client.dispatcher.maxRequests = 1
        client.dispatcher.maxRequestsPerHost = 1

        try {
            assertEquals("creator", api(client).getBlog("creator").blogUrl)
            assertEquals("rotated", store.peek()?.refreshToken)
            assertEquals(2, server.requestCount)
        } finally {
            client.dispatcher.executorService.shutdownNow()
            refreshClient.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
            refreshClient.connectionPool.evictAll()
            server.shutdown()
        }
    }
}
