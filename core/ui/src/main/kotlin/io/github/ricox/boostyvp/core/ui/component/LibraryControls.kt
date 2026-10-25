package io.github.ricox.boostyvp.core.ui.component

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.ricox.boostyvp.core.model.PersonalLibrary
import io.github.ricox.boostyvp.core.model.SavedPost

/** Callbacks are supplied by the signed-in app, not by preview/test cards. */
data class LibraryControls(
    val library: PersonalLibrary = PersonalLibrary(),
    val ready: Boolean = false,
    val failed: Boolean = false,
    val retry: () -> Unit = {},
    val setLater: (suspend (SavedPost, Boolean) -> Unit)? = null,
    val setAuthor: (suspend (String, String, Boolean) -> Unit)? = null,
    val setWatched: (suspend (String, Boolean) -> Unit)? = null,
    val setFavoritePost: (suspend (SavedPost, Boolean) -> Unit)? = null,
)

val LocalLibraryControls = staticCompositionLocalOf { LibraryControls() }
