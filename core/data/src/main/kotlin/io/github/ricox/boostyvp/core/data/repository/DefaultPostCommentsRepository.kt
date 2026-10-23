package io.github.ricox.boostyvp.core.data.repository

import io.github.ricox.boostyvp.core.domain.repository.PostCommentsRepository
import io.github.ricox.boostyvp.core.model.Page
import io.github.ricox.boostyvp.core.model.PostComment
import io.github.ricox.boostyvp.core.network.BoostyApi
import io.github.ricox.boostyvp.core.network.mapper.toDomain
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPostCommentsRepository @Inject constructor(
    private val api: BoostyApi,
    private val json: Json,
) : PostCommentsRepository {
    override suspend fun page(blogUrl: String, postId: String, cursor: String?, parentId: Long?): Page<PostComment> {
        val response = api.getComments(blogUrl, postId, offset = cursor, parentId = parentId)
        // The cursor comes from the RAW page even when all visible entries were
        // deleted. Never expose deleted/blocked content or derive item offsets.
        val next = response.data.minOfOrNull { it.intId }?.takeIf { it > 0 }?.toString()
            .takeUnless { response.extra.isLast || response.data.isEmpty() }
        check(next == null || next != cursor) { "Boosty returned a repeated comments cursor" }
        check(response.extra.isLast || response.data.isEmpty() || next != null) { "Missing comments cursor" }
        val items = response.data.filter { !it.isDeleted && !it.isBlocked && it.id.isNotBlank() }
            .distinctBy { it.id }.sortedByDescending { it.intId }.map {
                it.toDomain(json)
            }
        return Page(items, next, next == null)
    }
}
