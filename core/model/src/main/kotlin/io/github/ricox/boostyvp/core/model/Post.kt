package io.github.ricox.boostyvp.core.model

data class Post(
    val id: String,
    val intId: Long,
    val title: String,
    val blogUrl: String,
    val blogTitle: String,
    /** Unix epoch seconds. */
    val publishedAt: Long,
    /**
     * False when the viewer's tier does not cover this post. [blocks] will be
     * empty and [teaser] carries the preview instead. Never attempt playback.
     */
    val hasAccess: Boolean,
    val hasAdultContent: Boolean,
    /** Video is still transcoding on Boosty's side. */
    val isWaitingVideo: Boolean,
    val isPinned: Boolean,
    val signedQuery: String?,
    val blocks: List<ContentBlock>,
    val teaser: List<ContentBlock>,
    val commentCount: Int = 0,
    val commentsDenied: Boolean = false,
) {
    /** Includes processing videos and video teasers of locked posts. */
    val hasVideo: Boolean
        get() = isWaitingVideo || (blocks + teaser).any { it is ContentBlock.Video }

    val videos: List<ContentBlock.Video>
        get() = blocks.filterIsInstance<ContentBlock.Video>()

    /** The video this post is "about", if any -- drives the card and the player. */
    val primaryVideo: ContentBlock.Video?
        get() = videos.firstOrNull { it.isReady && it.hasPlayableSource }

    val thumbnailUrl: String?
        get() = videos.firstNotNullOfOrNull { it.previewUrl }
            ?: teaser.filterIsInstance<ContentBlock.Video>().firstNotNullOfOrNull { it.previewUrl }
            ?: (blocks + teaser).filterIsInstance<ContentBlock.Image>().firstOrNull()?.url

    val isPlayable: Boolean
        get() = hasAccess && !isWaitingVideo && primaryVideo != null
}

/** A page of items plus Boosty's opaque continuation cursor. */
data class Page<T>(
    val items: List<T>,
    /**
     * Opaque cursor from `extra.offset`. NOT a numeric offset -- pass it back
     * verbatim as the `offset` query parameter. Null when [isLast] is true.
     */
    val nextCursor: String?,
    val isLast: Boolean,
)
