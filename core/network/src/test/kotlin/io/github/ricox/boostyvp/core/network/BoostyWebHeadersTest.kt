package io.github.ricox.boostyvp.core.network

import io.github.ricox.boostyvp.core.network.interceptor.BoostyHeadersInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class BoostyWebHeadersTest {
    @Test fun `API and media use the same user agent origin and referer`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}"))
            server.start()
            val client = OkHttpClient.Builder().addInterceptor(BoostyHeadersInterceptor()).build()
            client.newCall(Request.Builder().url(server.url("/v1/test"))
                .header("User-Agent", "stale duplicate").build()).execute().use { }
            val request = server.takeRequest()
            for ((key, value) in BoostyWebHeaders.mediaRequestProperties) {
                assertEquals(key, value, request.getHeader(key))
                assertEquals("No duplicate $key header", 1, request.headers.values(key).size)
            }
            assertEquals(BoostyWebHeaders.USER_AGENT, request.getHeader("User-Agent"))
            assertEquals(BoostyWebHeaders.ORIGIN, request.getHeader("Origin"))
            assertEquals("web", request.getHeader("X-App"))
        }
    }
}
