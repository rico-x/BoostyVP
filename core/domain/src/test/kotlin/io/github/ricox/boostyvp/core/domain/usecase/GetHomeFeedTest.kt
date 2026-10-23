package io.github.ricox.boostyvp.core.domain.usecase

import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.domain.repository.FeedRepository
import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.model.Page
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.Subscription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class GetHomeFeedTest {
    private fun blog(id: String) = Blog(id, id, id, null, null, 0, 0, true, false, null)
    private fun post(id: String) = Post(id, 0, id, id, id, 0, true, false, false, false, null, emptyList(), emptyList())

    private fun loader(count: Int, fetch: suspend (String) -> List<Post>): GetHomeFeed {
        val feeds = object : FeedRepository {
            override suspend fun subscriptions(limit: Int) = (1..count).map {
                Subscription(blog(it.toString()), null, null)
            }
            override suspend fun feedPage(cursor: String?): Page<Post> = error("unused")
        }
        val blogs = object : BlogRepository {
            override suspend fun recentPosts(blogUrl: String, limit: Int) = fetch(blogUrl)
            override suspend fun blog(blogUrl: String): Blog = error("unused")
            override suspend fun post(blogUrl: String, postId: String): Post = error("unused")
            override suspend fun postsPage(blogUrl: String, cursor: String?): Page<Post> = error("unused")
        }
        return GetHomeFeed(feeds, blogs)
    }

    @Test fun `no subscriptions differs from authors with no videos`() = runTest {
        assertFalse(loader(0) { error("unused") }().hasSubscriptions)
        val empty = loader(2) { emptyList() }()
        assertTrue(empty.hasSubscriptions)
        assertTrue(empty.rows.isEmpty())
    }

    @Test fun `complete outage propagates so the screen can offer a retry`() = runTest {
        val failure = runCatching { loader(3) { throw IOException("offline") }() }.exceptionOrNull()
        assertTrue(failure is IOException)
    }

    @Test fun `one failed author does not hide successful rows`() = runTest {
        val feed = loader(3) { if (it == "2") throw IOException() else listOf(post(it)) }()
        assertEquals(listOf("1", "3"), feed.rows.map { it.blog.url })
    }

    @Test fun `cancellation propagates instead of becoming an empty feed`() = runTest {
        val failure = runCatching { loader(3) { throw CancellationException("cancelled") }() }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    @Test fun `all authors are loaded with at most four requests in flight`() = runTest {
        var active = 0
        var peak = 0
        val feed = loader(25) {
            active++
            peak = maxOf(peak, active)
            delay(10)
            active--
            listOf(post(it))
        }()
        assertEquals(25, feed.rows.size)
        assertEquals(4, peak)
    }
}
