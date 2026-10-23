package io.github.ricox.boostyvp.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.network.dto.CommentDto
import io.github.ricox.boostyvp.core.network.dto.PostDto
import io.github.ricox.boostyvp.core.network.mapper.toDomain
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit

class CommentProtocolTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test fun `comment endpoint uses read-only GET with parent and intId cursor`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"data":[],"extra":{"isLast":true,"isFirst":true}}"""))
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(BoostyApi::class.java)
            api.getComments("author", "post", "123456", 987)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/v1/blog/author/post/post/comment/", request.requestUrl!!.encodedPath)
            assertEquals("123456", request.requestUrl!!.queryParameter("offset"))
            assertEquals("987", request.requestUrl!!.queryParameter("parent_id"))
            assertEquals("0", request.requestUrl!!.queryParameter("reply_limit"))
            assertEquals("top", request.requestUrl!!.queryParameter("order"))
        } finally { server.shutdown() }
    }
    @Test fun `comments decode the same wrapped text as publications`() {
        val comment = json.decodeFromString<CommentDto>("""{"id":"c","intId":123,"author":{"name":"Reader"},
            "data":[{"type":"text","content":"[\"Comment text\",\"unstyled\",[]]"}],"replyCount":2} """)
        val mapped = comment.toDomain(json)
        assertEquals("Reader", mapped.authorName)
        assertEquals("Comment text", (mapped.blocks.single() as ContentBlock.Text).text)
        assertEquals(2, mapped.replyCount)
    }
    @Test fun `post maps comment count disabled flag and header blocks`() {
        val post = json.decodeFromString<PostDto>("""{"count":{"comments":7},"isCommentsDenied":true,
            "data":[{"type":"header","content":"Section"}]}""").toDomain(json)
        assertEquals(7, post.commentCount)
        assertTrue(post.commentsDenied)
        assertEquals(ContentBlock.Text("Section", "HEADER"), post.blocks.single())
    }
}
