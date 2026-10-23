package io.github.ricox.boostyvp.core.domain.usecase

import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.domain.repository.FeedRepository
import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.model.Post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class BlogPosts(val blog: Blog, val posts: List<Post>)
data class HomeFeed(val hasSubscriptions: Boolean, val rows: List<BlogPosts>)

/** A failed author may be omitted, but a complete outage must remain retryable. */
class GetHomeFeed(
    private val feeds: FeedRepository,
    private val blogs: BlogRepository,
) {
    suspend operator fun invoke(): HomeFeed = coroutineScope {
        val subscriptions = feeds.subscriptions()
        val requests = Semaphore(4)
        val results = subscriptions.map { subscription ->
            async {
                requests.withPermit {
                    try {
                        Result.success(BlogPosts(subscription.blog, blogs.recentPosts(subscription.blog.url)))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure<BlogPosts>(e)
                    }
                }
            }
        }.awaitAll()
        if (results.isNotEmpty() && results.all { it.isFailure }) {
            throw requireNotNull(results.first().exceptionOrNull())
        }
        HomeFeed(
            hasSubscriptions = subscriptions.isNotEmpty(),
            rows = results.mapNotNull { it.getOrNull() }.filter { it.posts.isNotEmpty() },
        )
    }
}
