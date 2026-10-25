package io.github.ricox.boostyvp.feature.home

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.domain.repository.*
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.appearance.LocalCardSize
import io.github.ricox.boostyvp.core.ui.appearance.LocalInterfaceOptions
import io.github.ricox.boostyvp.core.ui.component.*

/** Search is explicitly limited to loaded metadata, never advertised as Boosty's global search. */
@Composable
fun CatalogSearchContent(onPostClick: (Post) -> Unit, onPostLongClick: (Post) -> Unit,
    blogUrl: String? = null, modifier: Modifier = Modifier) {
    val catalog = LocalCatalogControls.current
    var query by rememberSaveable(blogUrl) { mutableStateOf("") }
    var sort by rememberSaveable(blogUrl) { mutableStateOf(CatalogSort.Newest) }
    var kind by rememberSaveable(blogUrl) { mutableStateOf(CatalogKind.All) }
    var accessible by rememberSaveable(blogUrl) { mutableStateOf(false) }
    var author by rememberSaveable(blogUrl) { mutableStateOf(blogUrl) }
    val authors = catalog.posts.map { it.blogUrl }.distinct().sorted()
    val results = remember(catalog.posts, query, sort, kind, accessible, author) {
        filterCatalog(catalog.posts, query, sort, kind, accessible, author)
    }
    val density = LocalInterfaceOptions.current.density
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr(UiText.catalog_search_content_001, catalog.posts.size),
            style = MaterialTheme.typography.bodySmall)
        Text(tr(UiText.catalog_search_content_002), style = MaterialTheme.typography.bodyMedium)
        BasicTextField(query, { query = it.take(200) }, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp).testTag("catalog_query"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvActionButton(sort.label, { sort = CatalogSort.entries[(sort.ordinal + 1) % CatalogSort.entries.size] }, Modifier.testTag("catalog_sort"))
            TvActionButton(kind.label, { kind = CatalogKind.entries[(kind.ordinal + 1) % CatalogKind.entries.size] }, Modifier.testTag("catalog_kind"))
            TvActionButton(if (accessible) tr(UiText.catalog_search_content_003) else tr(UiText.catalog_search_content_004), { accessible = !accessible })
        }
        if (blogUrl == null) TvActionButton(tr(UiText.catalog_search_content_006, author ?: tr(UiText.catalog_search_content_005)), {
            val options = listOf<String?>(null) + authors
            author = options[(options.indexOf(author) + 1).coerceAtLeast(0) % options.size]
        })
        Text(tr(UiText.catalog_search_content_007, results.size),
            style = MaterialTheme.typography.bodySmall)
        LazyVerticalGrid(GridCells.Adaptive(LocalCardSize.current.widthDp.dp),
            modifier = Modifier.weight(1f).testTag("catalog_results"),
            horizontalArrangement = Arrangement.spacedBy(density.horizontalGapDp.dp),
            verticalArrangement = Arrangement.spacedBy(density.gridGapDp.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(results, key = { it.id }) { post -> PostCard(post, { onPostClick(post) }, onLongClick = { onPostLongClick(post) }) }
        }
    }
}

@Composable
internal fun QueueContent(onPostClick: (Post) -> Unit) {
    val controls = LocalCatalogControls.current
    var confirmClear by remember { mutableStateOf(false) }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(tr(UiText.catalog_search_content_008, controls.queue.size), style = MaterialTheme.typography.bodySmall) }
        items(controls.queue.size, key = { controls.queue[it].postId }) { index ->
            val post = controls.queue[index]
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TvActionButton("${index + 1}. ${post.title}", { onPostClick(post.toPost()) }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvActionButton(tr(UiText.catalog_search_content_009), { controls.move(post.postId, -1) }, enabled = index > 0)
                    TvActionButton(tr(UiText.catalog_search_content_010), { controls.move(post.postId, 1) }, enabled = index < controls.queue.lastIndex)
                    TvActionButton(tr(UiText.catalog_search_content_011), { controls.remove(post.postId) })
                }
            }
        }
        if (controls.queue.isNotEmpty()) item {
            TvActionButton(if (confirmClear) tr(UiText.catalog_search_content_012) else tr(UiText.catalog_search_content_013), {
                if (confirmClear) { controls.clear(); confirmClear = false } else confirmClear = true
            })
        }
    }
}
