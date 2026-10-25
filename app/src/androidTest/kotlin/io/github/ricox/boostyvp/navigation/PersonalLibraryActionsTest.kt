package io.github.ricox.boostyvp.navigation

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.navigation.compose.*
import androidx.tv.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ricox.boostyvp.core.model.*
import io.github.ricox.boostyvp.core.ui.component.*
import io.github.ricox.boostyvp.core.ui.appearance.CardSize
import io.github.ricox.boostyvp.core.ui.appearance.CardSizePreferences
import io.github.ricox.boostyvp.core.ui.paging.CursorPagingSource
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.feature.home.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PersonalLibraryActionsTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var library by mutableStateOf(PersonalLibrary())
    private var opens = 0
    private var openedAuthor: String? = null
    private var failWrite = false
    private val post = post("p")
    private fun post(id: String) = Post(id, 0, "Видео $id", "author", "Автор", 1, true,
        false, false, false, null, listOf(ContentBlock.Video("v$id", null, null, 600,
            null, null, null, null, null, emptyList(), true)), emptyList())

    @Before fun before() {
        check(context.packageName != "io.github.ricox.boostyvp")
        device.wakeUp()
        context.getSharedPreferences("interface_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        CardSizePreferences(context).save(CardSize.Small)
    }
    @After fun after() {
        scope.cancel()
        context.getSharedPreferences("interface_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun unmatchedOpeningReleaseCannotChooseAnItemAndFreshOkWorksAfterArrows() {
        screen()
        for (code in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            actions()
            val now = SystemClock.uptimeMillis()
            instrumentation.uiAutomation.injectInputEvent(KeyEvent(now - 800, now, KeyEvent.ACTION_UP, code, 0), true)
            compose.waitForIdle()
            compose.onNodeWithTag("post_actions").assertIsDisplayed()
            assertEquals(0, opens)
            device.pressDPadDown()
            compose.onNodeWithTag("post_action_author").assertIsFocused()
            device.pressDPadCenter()
            compose.onNodeWithTag("post_actions").assertDoesNotExist()
            assertEquals("author", openedAuthor)
        }
    }
    @Test fun savedPostCanBeOpenedAndRemovedWithoutChangingWatchedOrHistory() {
        screen(); actions(); choose("post_action_later")
        compose.onNodeWithTag("post_actions").assertDoesNotExist()
        assertEquals(listOf("p"), library.later.map { it.postId })
        section("sidebar_later")
        compose.onNodeWithTag("later_post_p").assertIsDisplayed()
        key("later_post_p", Key.DirectionCenter)
        assertEquals(1, opens)
        compose.onNodeWithTag("later_post_p").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        choose("post_action_later")
        compose.onNodeWithTag("sidebar_later").assertIsFocused()
        assertTrue(library.later.isEmpty())
        assertTrue(library.watched.isEmpty())
        section("sidebar_history")
        compose.onNodeWithTag("history_post_p").assertExists()
    }
    @Test fun favoriteAuthorsOpenAndCanBeRemovedIncludingTheLastOne() {
        screen(); actions(); choose("post_action_favorite")
        section("sidebar_favorites")
        key("favorite_author_author", Key.DirectionCenter)
        assertEquals("author", openedAuthor)
        key("favorite_remove_author", Key.DirectionCenter)
        compose.waitUntil(4_000) { library.authors.isEmpty() }
        compose.onNodeWithTag("sidebar_favorites").assertIsFocused()
        compose.onNodeWithText("Избранных авторов пока нет").assertIsDisplayed()
    }
    @Test fun returningFromAnAuthorPageRestoresTheSelectedSectionAndRemoteFocus() {
        library = PersonalLibrary(authors = listOf(FavoriteAuthor("author", "Автор")))
        screen(navigateAuthors = true)
        section("sidebar_favorites")
        key("favorite_author_author", Key.DirectionCenter)
        compose.onNodeWithText("Страница автора").assertExists()
        device.pressBack()
        compose.onNodeWithTag("sidebar_favorites").assertIsSelected().assertIsFocused()
        compose.onNodeWithTag("sidebar_favorites").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("favorite_author_author").assertIsFocused()
    }
    @Test fun watchedFilterCanBeUndoneWithoutTouchingResumeOrLater() {
        library = PersonalLibrary(later = listOf(SavedPost.from(post)))
        screen(); actions(); choose("post_action_watched")
        assertEquals(setOf("p"), library.watched)
        key("sidebar_unwatched", Key.DirectionCenter)
        compose.onNodeWithText("Непросмотренных публикаций пока нет").assertIsDisplayed()
        assertEquals(1, library.later.size)
        section("sidebar_later")
        compose.onNodeWithTag("later_post_p").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        choose("post_action_watched")
        assertTrue(library.watched.isEmpty())
        section("sidebar_subscriptions")
        compose.onNodeWithText("Видео p").assertExists()
    }
    @Test fun failedLibraryWriteKeepsTheMenuAndListAndAllowsRetry() {
        failWrite = true
        screen(); actions(); choose("post_action_later")
        compose.onNodeWithTag("post_actions").assertIsDisplayed()
        compose.onNodeWithTag("post_action_error").assertExists()
        assertTrue(library.later.isEmpty())
        failWrite = false
        choose("post_action_later")
        compose.onNodeWithTag("post_actions").assertDoesNotExist()
        assertEquals(1, library.later.size)
    }
    @Test fun filteredPagingSkipsEntireWatchedPagesInsteadOfClaimingTheFeedIsEmpty() {
        library = PersonalLibrary(watched = setOf("0", "1", "2", "3"))
        var pages = 0
        val feed = Pager(PagingConfig(pageSize = 2, initialLoadSize = 2, prefetchDistance = 1, enablePlaceholders = false)) {
            CursorPagingSource { cursor ->
                pages++
                val start = cursor?.toInt() ?: 0
                Page(listOf(post("$start"), post("${start + 1}")), if (start < 4) "${start + 2}" else null, start >= 4)
            }
        }.flow.cachedIn(scope)
        screen(feed)
        key("sidebar_unwatched", Key.DirectionCenter)
        section("sidebar_new")
        compose.waitUntilAtLeastOneExists(hasText("Видео 4"), 8_000)
        assertTrue("Must load beyond two entirely filtered pages", pages >= 3)
    }

    private fun section(tag: String) = key(tag, Key.DirectionCenter)
    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
    }
    private fun key(tag: String, key: Key) { focus(tag); compose.onNodeWithTag(tag).performKeyInput { pressKey(key) }; compose.waitForIdle() }
    private fun choose(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo()
        key(tag, Key.DirectionCenter)
    }
    private fun actions() {
        section("sidebar_subscriptions")
        compose.onNodeWithText("Видео p").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithTag("post_actions").assertIsDisplayed()
    }
    private fun screen(newPosts: Flow<PagingData<Post>> = flowOf(PagingData.from(emptyList())), navigateAuthors: Boolean = false) {
        val blog = Blog("author", "Автор", "", null, null, 0, 1, true, false, null)
        val history = flowOf<HistoryUiState>(HistoryUiState.Content(listOf(
            WatchProgress("p", "vp", 60000, 600000, 1, "author", "Видео p", "Автор", null))))
        compose.setContent { BoostyVPTheme {
            val controls = LibraryControls(library, ready = true,
                setLater = { saved, add ->
                    check(!failWrite)
                    library = library.copy(later = library.later.filterNot { it.postId == saved.postId } + if (add) listOf(saved) else emptyList())
                }, setAuthor = { url, title, add ->
                    check(!failWrite)
                    library = library.copy(authors = library.authors.filterNot { it.blogUrl == url } + if (add) listOf(FavoriteAuthor(url, title)) else emptyList())
                }, setWatched = { id, value -> check(!failWrite); library = library.copy(watched = if (value) library.watched + id else library.watched - id) })
            CompositionLocalProvider(LocalLibraryControls provides controls) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeLayout(HomeUiState.Content(listOf(HomeRow(blog, listOf(post)))),
                            onPostClick = { opens++ }, onBlogClick = {
                                openedAuthor = it
                                if (navigateAuthors) nav.navigate("author")
                            }, onSignOut = {}, onRefresh = {}, newPosts = newPosts, history = history)
                    }
                    composable("author") {
                        BackHandler { nav.popBackStack() }
                        Text("Страница автора")
                    }
                }
            }
        } }
    }
}
