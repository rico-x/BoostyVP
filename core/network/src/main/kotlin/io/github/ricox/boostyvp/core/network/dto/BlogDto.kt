package io.github.ricox.boostyvp.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BlogDto(
    val blogUrl: String = "",
    val title: String = "",
    val owner: OwnerDto? = null,
    val coverUrl: String? = null,
    val count: BlogCountDto? = null,
    @SerialName("isSubscribed") val isSubscribed: Boolean = false,
    val hasAdultContent: Boolean = false,
    val signedQuery: String? = null,
    val subscriptionKind: String? = null,
)

@Serializable
data class OwnerDto(
    val id: Long = 0,
    val name: String = "",
    val avatarUrl: String? = null,
    val hasAvatar: Boolean = false,
)

@Serializable
data class BlogCountDto(
    val subscribers: Int = 0,
    val posts: Int = 0,
    val showcase: Int = 0,
)

@Serializable
data class SubscriptionLevelDto(
    val id: Long = 0,
    val name: String = "",
    val price: Int = 0,
    val currency: String? = null,
    val deleted: Boolean = false,
)

/** One entry from `/v1/user/subscriptions`. */
@Serializable
data class UserSubscriptionDto(
    val blog: BlogDto? = null,
    val level: SubscriptionLevelDto? = null,
    val nextPayTime: Long? = null,
)
