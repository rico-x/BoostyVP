package io.github.ricox.boostyvp.core.model

data class PostComment(
    val id: String,
    val intId: Long,
    val authorName: String,
    val createdAt: Long,
    val blocks: List<ContentBlock>,
    val replyCount: Int = 0,
)
