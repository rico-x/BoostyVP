package io.github.ricox.boostyvp.core.network.mapper

import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.model.Page
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.Subscription
import io.github.ricox.boostyvp.core.network.dto.BlogDto
import io.github.ricox.boostyvp.core.network.dto.PagedResponse
import io.github.ricox.boostyvp.core.network.dto.PostDto
import io.github.ricox.boostyvp.core.network.dto.UserSubscriptionDto
import kotlinx.serialization.json.Json

fun BlogDto.toDomain(): Blog = Blog(
    url = blogUrl,
    title = title.ifBlank { owner?.name.orEmpty() },
    ownerName = owner?.name.orEmpty(),
    avatarUrl = owner?.avatarUrl?.takeIf { owner.hasAvatar && it.isNotBlank() },
    coverUrl = coverUrl?.takeIf { it.isNotBlank() },
    subscriberCount = count?.subscribers ?: 0,
    postCount = count?.posts ?: 0,
    isSubscribed = isSubscribed,
    hasAdultContent = hasAdultContent,
    signedQuery = signedQuery?.takeIf { it.isNotBlank() },
)

fun UserSubscriptionDto.toDomainOrNull(): Subscription? {
    val blogDto = blog ?: return null
    if (blogDto.blogUrl.isBlank()) return null
    return Subscription(
        blog = blogDto.toDomain(),
        levelName = level?.name?.takeIf { it.isNotBlank() },
        expiresAt = nextPayTime,
    )
}

/**
 * @param fallbackBlogUrl per-blog post endpoints omit the blog object, so the
 *   caller supplies the slug it already knows.
 */
fun PostDto.toDomain(json: Json, fallbackBlogUrl: String = ""): Post = Post(
    id = id,
    intId = intId,
    title = title,
    blogUrl = blog?.blogUrl?.takeIf { it.isNotBlank() } ?: fallbackBlogUrl,
    blogTitle = blog?.title?.takeIf { it.isNotBlank() }
        ?: blog?.owner?.name
        ?: user?.name.orEmpty(),
    publishedAt = if (publishTime > 0) publishTime else createdAt,
    hasAccess = hasAccess,
    hasAdultContent = hasAdultContent,
    isWaitingVideo = isWaitingVideo,
    isPinned = isPinned,
    signedQuery = signedQuery?.takeIf { it.isNotBlank() },
    blocks = data.toDomain(json),
    teaser = teaser.toDomain(json),
    commentCount = count.comments.coerceAtLeast(0),
    commentsDenied = isCommentsDenied,
)

/** Posts that should never reach the UI at all. */
fun PostDto.isRenderable(): Boolean = isPublished && !isDeleted && !isBlocked

fun PagedResponse<PostDto>.toPostPage(json: Json, fallbackBlogUrl: String = ""): Page<Post> =
    Page(
        items = data.filter { it.isRenderable() }.map { it.toDomain(json, fallbackBlogUrl) },
        nextCursor = extra.offset?.takeIf { !extra.isLast && it.isNotBlank() },
        isLast = extra.isLast,
    )

fun PagedResponse<UserSubscriptionDto>.toSubscriptionPage(): Page<Subscription> =
    Page(
        items = data.mapNotNull { it.toDomainOrNull() },
        nextCursor = extra.offset?.takeIf { !extra.isLast && it.isNotBlank() },
        isLast = extra.isLast,
    )
