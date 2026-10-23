package io.github.ricox.boostyvp.core.network.mapper

import io.github.ricox.boostyvp.core.model.PostComment
import io.github.ricox.boostyvp.core.network.dto.CommentDto
import kotlinx.serialization.json.Json

fun CommentDto.toDomain(json: Json): PostComment = PostComment(
    id, intId, author?.name.orEmpty(), createdAt, data.toDomain(json), replyCount.coerceAtLeast(0),
)
