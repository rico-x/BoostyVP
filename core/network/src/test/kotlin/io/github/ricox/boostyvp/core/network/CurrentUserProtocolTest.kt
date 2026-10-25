package io.github.ricox.boostyvp.core.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit

class CurrentUserProtocolTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `current user is a read-only GET whose id is the only field kept`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            // Shape observed 2026-10-23, with placeholder values.
            server.enqueue(MockResponse().setBody("""
                {"id":123456,"name":"Name","email":"user@example.com","blogUrl":"own",
                 "authSource":{"id":"x","type":"phone","list":["a"]},"flags":{"GDPR":false}}
            """.trimIndent()))
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build().create(BoostyApi::class.java)

            val user = api.getCurrentUser()

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/v1/user/current", request.requestUrl!!.encodedPath)
            assertEquals(123456L, user.id)
            assertEquals("CurrentUserDto(id=123456)", user.toString())
        } finally {
            server.shutdown()
        }
    }
}
