package io.github.ricox.boostyvp.core.domain.repository

import io.github.ricox.boostyvp.core.model.Page
import io.github.ricox.boostyvp.core.model.PostComment

interface PostCommentsRepository {
    suspend fun page(blogUrl: String, postId: String, cursor: String? = null,
        parentId: Long? = null): Page<PostComment>
}
