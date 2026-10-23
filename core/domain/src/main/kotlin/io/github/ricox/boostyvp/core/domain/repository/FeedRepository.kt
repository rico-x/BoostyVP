package io.github.ricox.boostyvp.core.domain.repository

import io.github.ricox.boostyvp.core.model.Page
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.Subscription

interface FeedRepository {
    suspend fun feedPage(cursor: String?): Page<Post>
    suspend fun subscriptions(limit: Int = 100): List<Subscription>
}
