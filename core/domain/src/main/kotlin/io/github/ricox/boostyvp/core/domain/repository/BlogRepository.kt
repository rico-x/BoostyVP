package io.github.ricox.boostyvp.core.domain.repository

import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.model.Page
import io.github.ricox.boostyvp.core.model.Post

interface BlogRepository {
    suspend fun blog(blogUrl: String): Blog
    suspend fun post(blogUrl: String, postId: String): Post
    suspend fun postsPage(blogUrl: String, cursor: String?): Page<Post>
    suspend fun recentPosts(blogUrl: String, limit: Int = 12): List<Post>
}
