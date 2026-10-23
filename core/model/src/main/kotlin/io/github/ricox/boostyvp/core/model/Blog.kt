package io.github.ricox.boostyvp.core.model

data class Blog(
    /** URL slug, e.g. "somecreator". This is the id used by every blog endpoint. */
    val url: String,
    val title: String,
    val ownerName: String,
    val avatarUrl: String?,
    val coverUrl: String?,
    val subscriberCount: Int,
    val postCount: Int,
    val isSubscribed: Boolean,
    val hasAdultContent: Boolean,
    /**
     * Query string (without a leading `?`) that must be appended to some CDN
     * asset URLs. Time-limited -- never persist it.
     */
    val signedQuery: String?,
)

data class SubscriptionLevel(
    val id: Long,
    val name: String,
    val priceMinorUnits: Int,
    val currency: String,
    val isDeleted: Boolean,
)

/** One entry on the "my channels" screen. */
data class Subscription(
    val blog: Blog,
    val levelName: String?,
    /** Unix epoch seconds; null for a plain follow with no paid tier. */
    val expiresAt: Long?,
)
