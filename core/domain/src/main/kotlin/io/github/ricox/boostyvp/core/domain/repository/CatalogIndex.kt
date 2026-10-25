package io.github.ricox.boostyvp.core.domain.repository

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.SavedPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bounded, memory-only metadata cache. No bodies, media URLs, tokens or cross-account cards. */
class CatalogIndex(private val capacity: Int = 600) {
    private val entries = linkedMapOf<String, Post>()
    private val mutable = MutableStateFlow<List<Post>>(emptyList())
    val posts = mutable.asStateFlow()
    var account: String? = null
        private set

    @Synchronized fun activate(key: String?) {
        if (account == key) return
        account = key
        entries.clear()
        mutable.value = emptyList()
    }

    @Synchronized fun record(owner: String?, posts: List<Post>) {
        if (owner == null || owner != account) return
        posts.forEach { post ->
            entries.remove(post.id)
            entries[post.id] = SavedPost.from(post).toPost().copy(
                hasAccess = post.hasAccess, isWaitingVideo = post.isWaitingVideo,
                hasAdultContent = post.hasAdultContent,
            )
        }
        while (entries.size > capacity) entries.remove(entries.keys.first())
        mutable.value = entries.values.toList()
    }
}

enum class CatalogSort(private val labelText: () -> String) { Newest( { tr(UiText.catalog_index_001) }), Oldest( { tr(UiText.catalog_index_002) }), Title( { tr(UiText.catalog_index_003) }) ;
    val label: String get() = labelText()
}
enum class CatalogKind(private val labelText: () -> String) { All( { tr(UiText.catalog_index_004) }), Video( { tr(UiText.catalog_index_005) }), Text( { tr(UiText.catalog_index_006) }) ;
    val label: String get() = labelText()
}

fun filterCatalog(posts: List<Post>, query: String, sort: CatalogSort, kind: CatalogKind,
    onlyAccessible: Boolean = false, blogUrl: String? = null): List<Post> {
    val words = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    val selected = posts.filter { post ->
        (blogUrl == null || post.blogUrl == blogUrl) && (!onlyAccessible || post.hasAccess) &&
            (kind == CatalogKind.All || (kind == CatalogKind.Video) == post.hasVideo) &&
            words.all { word -> post.title.contains(word, true) || post.blogTitle.contains(word, true) || post.blogUrl.contains(word, true) }
    }
    return when (sort) {
        CatalogSort.Newest -> selected.sortedWith(compareByDescending<Post> { it.publishedAt }.thenBy { it.id })
        CatalogSort.Oldest -> selected.sortedWith(compareBy<Post> { it.publishedAt }.thenBy { it.id })
        CatalogSort.Title -> selected.sortedBy { it.title.lowercase() }
    }
}
