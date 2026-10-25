package io.github.ricox.boostyvp.core.ui.component

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.SavedPost

data class CatalogControls(
    val posts: List<Post> = emptyList(),
    val queue: List<SavedPost> = emptyList(),
    val ready: Boolean = false,
    val enqueue: (SavedPost) -> Unit = {},
    val remove: (String) -> Unit = {},
    val move: (String, Int) -> Unit = { _, _ -> },
    val clear: () -> Unit = {},
)
val LocalCatalogControls = staticCompositionLocalOf { CatalogControls() }
