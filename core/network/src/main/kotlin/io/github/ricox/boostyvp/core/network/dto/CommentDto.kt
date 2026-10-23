package io.github.ricox.boostyvp.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class CommentDto(
    val id: String = "",
    val intId: Long = 0,
    val author: OwnerDto? = null,
    val createdAt: Long = 0,
    val data: List<ContentBlockDto> = emptyList(),
    val replyCount: Int = 0,
    val isDeleted: Boolean = false,
    val isBlocked: Boolean = false,
)
