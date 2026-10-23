package io.github.ricox.boostyvp.feature.home

import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.WatchProgress

/**
 * A card for "Продолжить просмотр", drawn from the stored checkpoint alone.
 * It only opens the post: the player always fetches the post fresh, so no media
 * URL is needed here and none is kept.
 */
internal fun WatchProgress.toCardPost(): Post = Post(
    id = postId,
    intId = 0,
    title = title,
    blogUrl = blogUrl,
    blogTitle = blogTitle,
    publishedAt = 0,
    hasAccess = true,
    hasAdultContent = false,
    isWaitingVideo = false,
    isPinned = false,
    signedQuery = null,
    blocks = listOf(
        ContentBlock.Video(
            id = videoId, vid = null, title = null, duration = (durationMs / 1000).toInt(),
            width = null, height = null, previewUrl = previewUrl, failoverHost = null,
            timeCode = null, sources = emptyList(), isReady = true,
        ),
    ),
    teaser = emptyList(),
)
