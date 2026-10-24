package io.github.ricox.boostyvp.core.network

import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.network.dto.PostDto
import io.github.ricox.boostyvp.core.network.mapper.toDomain
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These lock down the two behaviours most likely to regress: surviving an
 * unknown block type, and unwrapping Boosty's double-encoded text.
 */
class ContentMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `unknown block type does not fail the post`() {
        val raw = """
            {
              "id": "p1",
              "title": "Mixed",
              "hasAccess": true,
              "data": [
                {"type": "text", "content": "[\"hello\",\"unstyled\",[]]"},
                {"type": "some_future_type", "id": "x"},
                {"type": "image", "id": "i1", "url": "https://cdn.example/a.jpg"}
              ]
            }
        """.trimIndent()

        val post = json.decodeFromString<PostDto>(raw).toDomain(json)

        assertEquals(3, post.blocks.size)
        assertTrue(post.blocks[1] is ContentBlock.Unknown)
        assertEquals("some_future_type", (post.blocks[1] as ContentBlock.Unknown).rawType)
        assertEquals("hello", (post.blocks[0] as ContentBlock.Text).text)
    }

    @Test
    fun `plain string text content is passed through unchanged`() {
        val raw = """{"id":"p2","data":[{"type":"text","content":"not json"}]}"""
        val post = json.decodeFromString<PostDto>(raw).toDomain(json)
        assertEquals("not json", (post.blocks[0] as ContentBlock.Text).text)
    }

    @Test
    fun `empty player urls are dropped`() {
        val raw = """
            {
              "id": "p3",
              "hasAccess": true,
              "data": [{
                "type": "ok_video",
                "id": "v1",
                "duration": 120,
                "playerUrls": [
                  {"type": "dash", "url": ""},
                  {"type": "hls", "url": "https://cdn.example/x.m3u8"},
                  {"type": "full_hd", "url": ""}
                ]
              }]
            }
        """.trimIndent()

        val video = json.decodeFromString<PostDto>(raw).toDomain(json)
            .blocks.filterIsInstance<ContentBlock.Video>().single()

        assertEquals(1, video.sources.size)
        assertEquals("hls", video.sources.single().rawType)
    }

    @Test
    fun `missing fields fall back to defaults rather than throwing`() {
        val post = json.decodeFromString<PostDto>("""{"id":"p4"}""").toDomain(json)
        assertEquals("p4", post.id)
        assertTrue(post.blocks.isEmpty())
    }

    @Test
    fun `a malformed size never fails the post`() {
        // Found by fuzzing: JsonPrimitive.intOrNull throws StringIndexOutOfBounds
        // on text such as "1 (a quote, then a digit) instead of returning null,
        // which used to fail the whole page.
        for (bad in listOf("\\\"1", "\\\"0", "\\\"", "-", "--1", "0x10", "1.5")) {
            val raw = """{"id":"p5","data":[{"type":"image","url":"u","width":"$bad","height":"$bad"},
                {"type":"ok_video","id":"v","width":"$bad","playerUrls":[]}]}"""
            val post = json.decodeFromString<PostDto>(raw).toDomain(json)
            val image = post.blocks.filterIsInstance<ContentBlock.Image>().single()
            assertEquals(bad, null, image.width)
            assertEquals(bad, null, image.height)
        }
        val numbers = json.decodeFromString<PostDto>(
            """{"id":"p6","data":[{"type":"image","url":"u","width":640,"height":"360"}]}""").toDomain(json)
        val image = numbers.blocks.single() as ContentBlock.Image
        assertEquals(640, image.width)
        assertEquals(360, image.height)
    }
}
