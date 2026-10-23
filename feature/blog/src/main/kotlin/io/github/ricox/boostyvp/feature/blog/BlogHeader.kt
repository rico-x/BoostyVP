package io.github.ricox.boostyvp.feature.blog

import androidx.compose.runtime.Composable
import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.ui.component.AuthorHeader

/** The settings preview and the real page use exactly the same header presentation. */
@Composable
fun BlogHeader(blog: Blog?, blogUrl: String, onBack: () -> Unit) = AuthorHeader(blog, blogUrl, onBack)
