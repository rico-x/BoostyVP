package io.github.ricox.boostyvp.core.ui.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.ricox.boostyvp.core.model.Page

/**
 * A PagingSource for Boosty's cursor pagination.
 *
 * Boosty's `extra.offset` is an opaque string, despite the name -- something
 * like "1755500000:123456". It is NOT an index, so the usual
 * `PagingSource<Int, T>` with `offset += limit` produces wrong pages and
 * duplicate items. The key type here is [String] and the cursor is echoed back
 * untouched.
 *
 * [getRefreshKey] deliberately returns null: there is no way to resume from an
 * arbitrary cursor mid-list, so a refresh restarts from the top. That is the
 * correct behaviour for a feed anyway.
 */
class CursorPagingSource<T : Any>(
    private val fetch: suspend (cursor: String?) -> Page<T>,
) : PagingSource<String, T>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, T> = try {
        val page = fetch(params.key)
        check(page.isLast || page.nextCursor == null || page.nextCursor != params.key) {
            "Boosty returned a repeated posts cursor"
        }
        LoadResult.Page(
            data = page.items,
            prevKey = null, // forward-only
            nextKey = if (page.isLast) null else page.nextCursor,
        )
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<String, T>): String? = null
}
