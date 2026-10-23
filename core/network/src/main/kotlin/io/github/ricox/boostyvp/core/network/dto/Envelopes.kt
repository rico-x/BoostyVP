package io.github.ricox.boostyvp.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Every Boosty list endpoint answers with this shape.
 *
 * [PagingExtraDto.offset] is an opaque cursor string, not a number -- see the
 * note on that field.
 */
@Serializable
data class PagedResponse<T>(
    val data: List<T> = emptyList(),
    val extra: PagingExtraDto = PagingExtraDto(),
)

@Serializable
data class PagingExtraDto(
    /**
     * Opaque continuation cursor, e.g. "1755500000:123456". Send it back
     * verbatim as the next request's `offset` parameter. Never parse it, never
     * do arithmetic on it.
     */
    val offset: String? = null,
    @SerialName("isLast") val isLast: Boolean = true,
)
