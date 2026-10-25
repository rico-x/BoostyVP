package io.github.ricox.boostyvp.core.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.ricox.boostyvp.core.network.dto.PostDto
import io.github.ricox.boostyvp.core.network.mapper.toDomain
import io.github.ricox.boostyvp.core.network.mapper.toPostPage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.Retrofit

/** Shape observed from the signed-in feed on 2026-10-23, reduced to the fields that matter. */
class FeedProtocolTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val body = """
        {"extra":{"isLast":false,"offset":"1761200000:123"},
         "data":{"blogs":[{"blogUrl":"alpha","subscriptionKind":"paid","isSubscribed":true}],
                 "counters":{"newNotAllowedPosts":2},
                 "posts":[
                   {"id":"p1","intId":1,"title":"Video","publishTime":1761200000,"hasAccess":true,
                    "user":{"id":7,"name":"Alpha Author","blogUrl":"alpha","hasAvatar":true,"avatarUrl":"a"},
                    "data":[{"type":"text","content":"[\"hi\",\"unstyled\",[]]","modificator":""}],
                    "teaser":[],"count":{"comments":3}},
                   {"id":"p2","intId":2,"title":"Text","publishTime":1761100000,"hasAccess":true,
                    "user":{"id":8,"name":"Beta","blogUrl":"beta"},"data":[],"teaser":[]}]}}
    """.trimIndent()

    @Test fun `feed is a read-only GET of allowed posts that continues from the opaque cursor`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody(body))
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build().create(BoostyApi::class.java)

            val page = api.getFeed(offset = "1761300000:99").toPostPage(json)

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/v1/feed/post/", request.requestUrl!!.encodedPath)
            assertEquals("1761300000:99", request.requestUrl!!.queryParameter("offset"))
            assertEquals("true", request.requestUrl!!.queryParameter("only_allowed"))
            assertEquals(listOf("p1" to "alpha", "p2" to "beta"), page.items.map { it.id to it.blogUrl })
            assertEquals("Alpha Author", page.items.first().blogTitle)
            assertEquals(3, page.items.first().commentCount)
            assertEquals("1761200000:123", page.nextCursor)
            assertFalse(page.isLast)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `known blog slug still wins over the author's blog on per-blog lists`() {
        val post = json.decodeFromString<PostDto>("""{"id":"p","user":{"blogUrl":"author-own"}}""")
        assertEquals("requested", post.toDomain(json, fallbackBlogUrl = "requested").blogUrl)
        assertEquals("author-own", post.toDomain(json).blogUrl)
    }
}
