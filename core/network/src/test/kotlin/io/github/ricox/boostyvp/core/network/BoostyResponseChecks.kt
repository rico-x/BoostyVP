package io.github.ricox.boostyvp.core.network

import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.network.dto.CommentDto
import io.github.ricox.boostyvp.core.network.dto.ContentBlockDto
import io.github.ricox.boostyvp.core.network.dto.FeedResponseDto
import io.github.ricox.boostyvp.core.network.dto.PagedResponse
import io.github.ricox.boostyvp.core.network.dto.PostDto
import io.github.ricox.boostyvp.core.network.dto.UserSubscriptionDto
import io.github.ricox.boostyvp.core.network.mapper.toDomain
import io.github.ricox.boostyvp.core.network.mapper.toPostPage
import io.github.ricox.boostyvp.core.network.mapper.toSubscriptionPage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * What must hold for any Boosty API answer. Called by [BoostyResponseFuzzTest]
 * with Jazzer's inputs: decoding may reject the text, but whatever decodes must
 * map to the domain without throwing -- one odd post must not break a page.
 */
internal object BoostyResponseChecks {
    /** Same settings as NetworkModule.provideJson. */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun response(kind: Int, body: String) {
        when (kind.mod(4)) {
            0 -> decode(PagedResponse.serializer(PostDto.serializer()), body)?.toPostPage(json, "blog")
            1 -> decode(FeedResponseDto.serializer(), body)?.toPostPage(json)
            2 -> decode(PagedResponse.serializer(CommentDto.serializer()), body)?.data?.forEach { it.toDomain(json) }
            else -> decode(PagedResponse.serializer(UserSubscriptionDto.serializer()), body)?.toSubscriptionPage()
        }
    }

    /** A block assembled from fuzzed fields, so mapping is reached far more often than by raw text. */
    fun contentBlock(type: String, content: String, url: String, width: String, playerType: String, playerUrl: String, status: String) {
        val element = buildJsonObject {
            put("type", type)
            put("content", content)
            put("url", url)
            put("width", JsonPrimitive(width))
            put("status", status)
            putJsonArray("playerUrls") {
                addJsonObject {
                    put("type", playerType)
                    put("url", playerUrl)
                }
            }
        }
        val dto = try {
            json.decodeFromJsonElement(ContentBlockDto.serializer(), element)
        } catch (_: IllegalArgumentException) {
            return
        }
        val block = listOf(dto).toDomain(json).single()
        if (block is ContentBlock.Video) check(block.sources.all { it.url.isNotBlank() }) { "Empty player URL kept" }
        if (block is ContentBlock.Text) check(block.text.length <= content.length || content.isBlank()) { "Text grew while decoding" }
    }

    private fun <T> decode(serializer: KSerializer<T>, body: String): T? = try {
        json.decodeFromString(serializer, body)
    } catch (_: IllegalArgumentException) {
        null  // Includes SerializationException: rejected input, as intended.
    }
}
