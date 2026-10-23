package io.github.ricox.boostyvp.core.data.repository

import io.github.ricox.boostyvp.core.domain.repository.FeedRepository
import io.github.ricox.boostyvp.core.model.Page
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.Subscription
import io.github.ricox.boostyvp.core.network.BoostyApi
import io.github.ricox.boostyvp.core.network.mapper.toPostPage
import io.github.ricox.boostyvp.core.network.mapper.toSubscriptionPage
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultFeedRepository @Inject constructor(
    private val api: BoostyApi,
    private val json: Json,
) : FeedRepository {
    override suspend fun feedPage(cursor: String?): Page<Post> =
        videoPostPage(cursor) { next -> api.getFeed(offset = next).toPostPage(json) }

    override suspend fun subscriptions(limit: Int): List<Subscription> {
        require(limit > 0)
        val subscriptions = linkedMapOf<String, Subscription>()
        val visited = mutableSetOf<String>()
        var cursor: String? = null
        do {
            val page = api.getSubscriptions(limit = limit, offset = cursor).toSubscriptionPage()
            page.items.forEach { subscriptions[it.blog.url] = it }
            cursor = page.nextCursor.takeUnless { page.isLast }
            check(cursor == null || visited.add(cursor)) { "Boosty returned a repeated subscriptions cursor" }
        } while (cursor != null)
        return subscriptions.values.toList()
    }
}
