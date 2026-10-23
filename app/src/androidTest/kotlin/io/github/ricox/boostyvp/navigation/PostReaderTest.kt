package io.github.ricox.boostyvp.navigation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ricox.boostyvp.core.model.*
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.feature.player.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PostReaderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var list: LazyListState
    private var toggles = 0
    private var loads = 0
    private var replies = 0
    private val article = Post("text", 0, "Текстовая публикация", "test", "Тестовый автор", 0,
        true, false, false, false, null, listOf(ContentBlock.Text("Длинный абзац для чтения с пульта. ".repeat(500), null)),
        emptyList(), 3)
    private val root = PostComment("one", 20, "Автор комментария", 0,
        listOf(ContentBlock.Text("Тестовый комментарий", null)), 1)

    @Before fun wake() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressKeyCode(android.view.KeyEvent.KEYCODE_WAKEUP)
    }

    @Test fun longParagraphScrollsBothWaysWithRemote() {
        screen()
        compose.onNodeWithTag("post_reader").assertIsFocused()
        repeat(4) { compose.onNodeWithTag("post_reader").performKeyInput { pressKey(Key.DirectionDown) }; compose.waitForIdle() }
        compose.runOnIdle { assertTrue(list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0) }
        repeat(4) { compose.onNodeWithTag("post_reader").performKeyInput { pressKey(Key.DirectionUp) }; compose.waitForIdle() }
        compose.runOnIdle { assertEquals(0, list.firstVisibleItemIndex); assertEquals(0, list.firstVisibleItemScrollOffset) }
    }

    @Test fun commentsAreInitiallyHiddenAndCanExpandAndCollapseFromRemote() {
        screen()
        compose.onNodeWithText("Тестовый комментарий").assertDoesNotExist()
        compose.onNodeWithTag("post_reader").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("comments_toggle").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Тестовый комментарий").assertIsDisplayed()
        compose.onNodeWithTag("post_reader").assertIsFocused()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "post-comments-style.png"))
        compose.onNodeWithTag("comments_toggle").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("comments_toggle").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Тестовый комментарий").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, toggles); assertEquals(0, list.firstVisibleItemIndex) }
    }

    @Test fun visibleReplyActionIsReachableAndReturnsToReading() {
        screen(expandedInitially = true)
        compose.onNodeWithTag("post_reader").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("replies_one").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, replies) }
        compose.onNodeWithTag("replies_one").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("post_reader").assertIsFocused()
    }

    @Test fun loadingNextCommentsPageReturnsFocusToReading() {
        screen(expandedInitially = true)
        compose.onNodeWithTag("post_reader").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("replies_one").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("comments_more").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("post_reader").assertIsFocused()
        compose.runOnIdle { assertEquals(1, loads) }
    }

    @Test fun lockedArticleShowsOnlyTeaserAndCannotExpandComments() {
        screen(value = article.copy(hasAccess = false, blocks = listOf(ContentBlock.Text("SECRET BODY", null)),
            teaser = listOf(ContentBlock.Text("Только анонс", null))), expandedInitially = true)
        compose.onNodeWithText("Только анонс").assertIsDisplayed()
        compose.onNodeWithText("SECRET BODY").assertDoesNotExist()
        compose.onNodeWithText("Тестовый комментарий").assertDoesNotExist()
        compose.onNodeWithTag("comments_toggle").assertIsNotEnabled()
    }

    @Test fun wideImageStaysInsideItsItemAndNeverOverlapsParagraphs() = assertImageLayout(1600, 900)
    @Test fun portraitImageStaysInsideItsItemAndNeverOverlapsParagraphs() = assertImageLayout(600, 1200)

    private fun assertImageLayout(width: Int, height: Int) {
        screen(value = article.copy(blocks = listOf(
            ContentBlock.Text("Перед иллюстрацией", null),
            ContentBlock.Image("image", "http://127.0.0.1:9/test-wide.png", width, height),
            ContentBlock.Text("После иллюстрации", null),
        )))
        val image = compose.onNodeWithTag("reader_image_content").fetchSemanticsNode().boundsInRoot
        val item = compose.onNodeWithTag("reader_image").fetchSemanticsNode().boundsInRoot
        val before = compose.onNodeWithText("Перед иллюстрацией").fetchSemanticsNode().boundsInRoot
        val after = compose.onNodeWithText("После иллюстрации").fetchSemanticsNode().boundsInRoot
        assertTrue("Image draws only in its measured item", image.top >= item.top && image.bottom <= item.bottom)
        assertTrue("Previous paragraph ends before the image", before.bottom <= image.top)
        assertTrue("Next paragraph starts below the image", after.top >= image.bottom)
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue(image.height <= 280f * density + 1)
        assertTrue(compose.onNodeWithTag("post_reader").fetchSemanticsNode().boundsInRoot.width <= 720f * density + 1)
    }

    private fun screen(value: Post = article, expandedInitially: Boolean = false) {
        compose.setContent {
            BoostyVPTheme {
                list = rememberLazyListState()
                var state by remember { mutableStateOf(PostCommentsState(expanded = expandedInitially,
                    root = CommentListState(listOf(root), loaded = true, nextCursor = "20"))) }
                PostReaderLayout(value, state, onToggleComments = {
                    toggles++; state = state.copy(expanded = !state.expanded)
                }, onLoadMore = {
                    loads++
                    state = state.copy(root = state.root.copy(loading = true))
                }, onToggleReplies = { replies++ }, onClose = {}, list = list)
            }
        }
        compose.waitForIdle()
    }
}
