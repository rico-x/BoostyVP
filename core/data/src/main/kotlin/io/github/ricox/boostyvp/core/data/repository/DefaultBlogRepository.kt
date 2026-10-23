package io.github.ricox.boostyvp.core.data.repository

import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.model.Page
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.network.BoostyApi
import io.github.ricox.boostyvp.core.network.mapper.toDomain
import io.github.ricox.boostyvp.core.network.mapper.toPostPage
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultBlogRepository @Inject constructor(
    private val api: BoostyApi,
    private val json: Json,
) : BlogRepository {
    override suspend fun blog(blogUrl: String): Blog = api.getBlog(blogUrl).toDomain()

    override suspend fun post(blogUrl: String, postId: String): Post =
        api.getPost(blogUrl, postId).toDomain(json, fallbackBlogUrl = blogUrl)

    override suspend fun postsPage(blogUrl: String, cursor: String?): Page<Post> =
        publicationPage(cursor) { next ->
            api.getBlogPosts(blogUrl, offset = next).toPostPage(json, blogUrl)
        }

    override suspend fun recentPosts(blogUrl: String, limit: Int): List<Post> =
        publicationPage(null) { cursor ->
            api.getBlogPosts(blogUrl, limit = limit, offset = cursor).toPostPage(json, blogUrl)
        }.items
}
