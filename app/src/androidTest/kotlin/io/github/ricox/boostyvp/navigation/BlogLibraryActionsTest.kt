package io.github.ricox.boostyvp.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.model.*
import io.github.ricox.boostyvp.core.ui.component.*
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.feature.blog.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class BlogLibraryActionsTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private var library by mutableStateOf(PersonalLibrary())
    private val post = Post("text", 0, "Текстовая публикация", "author", "Автор", 1,
        true, false, false, false, null, listOf(ContentBlock.Text("Text", null)), emptyList())
    private var opens = 0
    @After fun cleanup() { compose.runOnIdle { store.clear() } }

    @Test fun authorHeaderAddsAndRemovesFavoritesWithTheRemote() {
        screen()
        compose.waitUntilExactlyOneExists(hasTestTag("blog_favorite"), 5_000)
        key("blog_favorite")
        assertEquals(listOf(FavoriteAuthor("author", "Автор")), library.authors)
        compose.onNodeWithText("Убрать из избранных").assertExists()
        key("blog_favorite")
        assertTrue(library.authors.isEmpty())
    }
    @Test fun textPostsCanBeSavedAndMarkedReadAndAuthorFilterCanBeTurnedOff() {
        screen()
        compose.waitUntilExactlyOneExists(hasText(post.title), 5_000)
        compose.onNodeWithText(post.title).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        key("post_action_later")
        assertFalse(library.later.single().hasVideo)
        compose.onNodeWithText(post.title).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        key("post_action_watched")
        assertEquals(setOf("text"), library.watched)
        key("blog_unwatched")
        compose.waitUntilDoesNotExist(hasText(post.title), 5_000)
        compose.onNodeWithText("Непросмотренных публикаций пока нет").assertExists()
        key("blog_unwatched")
        compose.waitUntilExactlyOneExists(hasText(post.title), 5_000)
        compose.onNodeWithText(post.title).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText(post.title).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(1, opens)
    }
    private fun key(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }
    private fun screen() {
        val repository = object : BlogRepository {
            override suspend fun blog(blogUrl: String) = Blog("author", "Автор", "", null, null, 0, 1, true, false, null)
            override suspend fun post(blogUrl: String, postId: String) = post
            override suspend fun postsPage(blogUrl: String, cursor: String?) = Page(listOf(post), null, true)
            override suspend fun recentPosts(blogUrl: String, limit: Int) = listOf(post)
        }
        val model = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = BlogViewModel(repository) as T
        })[BlogViewModel::class.java]
        compose.setContent { BoostyVPTheme {
            val controls = LibraryControls(library, ready = true,
                setAuthor = { url, title, add -> library = library.copy(authors = if (add) listOf(FavoriteAuthor(url, title)) else emptyList()) },
                setLater = { saved, add -> library = library.copy(later = if (add) listOf(saved) else emptyList()) },
                setWatched = { id, watched -> library = library.copy(watched = if (watched) setOf(id) else emptySet()) })
            CompositionLocalProvider(LocalLibraryControls provides controls) {
                BlogScreen("author", onPostClick = { opens++ }, onBack = {}, viewModel = model)
            }
        } }
    }
}
