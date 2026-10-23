package io.github.ricox.boostyvp.core.data.repository

import io.github.ricox.boostyvp.core.model.Page
import io.github.ricox.boostyvp.core.model.Post

/** Keep text/image posts too; skip only genuinely empty pages. */
internal suspend fun publicationPage(
    cursor: String?,
    fetch: suspend (String?) -> Page<Post>,
): Page<Post> {
    var next = cursor
    val visited = mutableSetOf(cursor)
    while (true) {
        val page = fetch(next)
        check(page.isLast || page.nextCursor == null || page.nextCursor !in visited) {
            "Boosty returned a repeated posts cursor"
        }
        if (page.items.isNotEmpty() || page.isLast || page.nextCursor == null) {
            return page
        }
        next = page.nextCursor
        check(visited.add(next)) { "Boosty returned a repeated posts cursor" }
    }
}
