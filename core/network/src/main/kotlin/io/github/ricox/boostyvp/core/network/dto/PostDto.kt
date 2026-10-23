package io.github.ricox.boostyvp.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PostDto(
    val id: String = "",
    @SerialName("intId") val intId: Long = 0,
    val title: String = "",
    val publishTime: Long = 0,
    val createdAt: Long = 0,
    val hasAccess: Boolean = false,
    val hasAdultContent: Boolean = false,
    val isWaitingVideo: Boolean = false,
    val isPinned: Boolean = false,
    val isPublished: Boolean = true,
    val isDeleted: Boolean = false,
    val isBlocked: Boolean = false,
    val signedQuery: String? = null,
    val user: OwnerDto? = null,
    /** Absent on per-blog and feed responses alike; the feed names the blog in [user]. */
    val blog: BlogDto? = null,
    val data: List<ContentBlockDto> = emptyList(),
    val teaser: List<ContentBlockDto> = emptyList(),
    val count: PostCountDto = PostCountDto(),
    val isCommentsDenied: Boolean = false,
)

@Serializable
data class PostCountDto(val comments: Int = 0)

/**
 * A single flat DTO for every content block type.
 *
 * Boosty returns a heterogeneous array discriminated by `type`, which invites a
 * sealed hierarchy plus a polymorphic serializer. This deliberately does not do
 * that. A private API that adds a block type -- or changes one field's shape --
 * would make a strict polymorphic parse throw, and one bad block would fail the
 * entire post, and with it the entire page. A flat DTO with every field
 * optional cannot fail: unknown types fall through to
 * [io.github.ricox.boostyvp.core.model.ContentBlock.Unknown] during mapping and the rest of
 * the post still renders.
 *
 * Fields whose JSON type is not stable across block types are kept as
 * [JsonElement] and decoded during mapping.
 */
@Serializable
data class ContentBlockDto(
    val type: String = "",
    val id: String? = null,

    /**
     * String in text and link blocks, but Boosty has been known to vary this.
     * Kept raw; [io.github.ricox.boostyvp.core.network.mapper.decodeTextContent] handles it.
     */
    val content: JsonElement? = null,
    val modificator: String? = null,

    // image / file / audio
    val url: String? = null,
    val width: JsonElement? = null,
    val height: JsonElement? = null,
    val title: String? = null,
    val size: Long? = null,
    val complete: Boolean? = null,

    // ok_video
    val vid: String? = null,
    val duration: Int? = null,
    val preview: String? = null,
    val defaultPreview: String? = null,
    val previewId: String? = null,
    val failoverHost: String? = null,
    val timeCode: Int? = null,
    val uploadStatus: String? = null,
    val status: String? = null,
    val playerUrls: List<PlayerUrlDto> = emptyList(),

    // link
    val explicit: Boolean? = null,
)

@Serializable
data class PlayerUrlDto(
    val type: String = "",
    /**
     * Frequently an empty string. Boosty returns a slot for every rendition it
     * knows about whether or not it produced one, so entries must be filtered
     * before they reach the player.
     */
    val url: String = "",
)
