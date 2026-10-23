package io.github.ricox.boostyvp.navigation

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ricox.boostyvp.core.model.*
import io.github.ricox.boostyvp.core.ui.appearance.CardSize
import io.github.ricox.boostyvp.core.ui.appearance.CardSizePreferences
import io.github.ricox.boostyvp.core.ui.appearance.InterfacePreferences
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.feature.home.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic history, real remote/window path. No real account, media URL or library. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class, androidx.media3.common.util.UnstableApi::class)
class HistoryActionsTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val entries = (0..29).map { index -> WatchProgress("h$index", "v$index", 30_000,
        600_000, (100 - index).toLong(), "author", "История $index", "Тестовый автор", null) }
    private val history = MutableStateFlow<HistoryUiState>(HistoryUiState.Content(entries))
    private var opened: Triple<String, String, Boolean>? = null
    private var author: String? = null
    private var removeSucceeds = true
    private var retries = 0

    @Before fun prepare() {
        check(context.packageName != "io.github.ricox.boostyvp")
        device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
        clear()
        CardSizePreferences(context).save(CardSize.Small)
    }
    @After fun cleanup() = clear()
    private fun clear() { context.getSharedPreferences("interface_preferences", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun holdingOkOpensActionsWithoutStartingVideoAndBackRestoresTheCard() {
        screen(); openHistory()
        compose.onNodeWithTag("history_post_h0").assertIsFocused()
        val down = SystemClock.uptimeMillis()
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(down, down, KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER, 0), true)
        SystemClock.sleep(650)
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER, 1, 0, -1, 0, KeyEvent.FLAG_LONG_PRESS), true)
        SystemClock.sleep(80)
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
            KeyEvent.KEYCODE_DPAD_CENTER, 0), true)
        compose.waitUntilExactlyOneExists(hasTestTag("post_actions"), 4_000)
        compose.onNodeWithTag("post_action_open").assertIsFocused()
        assertNull("A held OK and its release must not play the video", opened)
        device.takeScreenshot(File(context.getExternalFilesDir(null), "history-actions.png"))
        device.pressBack()
        compose.onNodeWithTag("post_actions").assertDoesNotExist()
        compose.onNodeWithTag("history_post_h0").assertIsFocused()
    }

    @Test fun menuKeyOpensActionsAndShortOkStillOpensNormally() {
        screen(); openHistory()
        device.pressKeyCode(KeyEvent.KEYCODE_MENU)
        compose.onNodeWithTag("post_actions").assertIsDisplayed()
        device.pressBack()
        key("history_post_h0", Key.DirectionCenter)
        compose.onNodeWithTag("history_test_player").assertExists()
        assertEquals(Triple("author", "h0", false), opened)
        device.pressBack()
        compose.onNodeWithTag("history_post_h0").assertIsFocused()
    }

    @Test fun holdingOkWithoutKeyRepeatsStillOpensActions() {
        screen(); openHistory()
        val down = SystemClock.uptimeMillis()
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(down, down, KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER, 0), true)
        compose.mainClock.advanceTimeBy(700)
        compose.waitUntilExactlyOneExists(hasTestTag("post_actions"), 4_000)
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
            KeyEvent.KEYCODE_DPAD_CENTER, 0), true)
        compose.onNodeWithTag("post_action_open").assertIsFocused()
        assertNull(opened)
    }

    @Test fun leavingTheCardCancelsItsPendingHeldOk() {
        screen(); openHistory()
        val down = SystemClock.uptimeMillis()
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(down, down, KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER, 0), true)
        device.pressDPadDown()
        compose.waitForIdle()
        compose.onNodeWithTag("history_post_h0").assertIsNotFocused()
        compose.mainClock.advanceTimeBy(700)
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
            KeyEvent.KEYCODE_DPAD_CENTER, 0), true)
        compose.onNodeWithTag("post_actions").assertDoesNotExist()
        assertNull(opened)
    }

    @Test fun authorActionOpensTheHistoryPostsAuthor() {
        screen(); openHistory(); actions("h0")
        focus("post_action_author"); key("post_action_author", Key.DirectionCenter)
        assertEquals("author", author)
        compose.onNodeWithTag("post_actions").assertDoesNotExist()
        assertNull(opened)
    }

    @Test fun historyLeavesEveryMenuItemReachableWithFullTitleReadingSpace() {
        val prefs = InterfacePreferences(context)
        prefs.save(prefs.load().copy(fullTitle = true))
        screen()
        compose.onNodeWithText("Выйти").assertIsDisplayed()
        val sidebar = compose.onNodeWithTag("home_sidebar").fetchSemanticsNode().boundsInRoot
        val catalog = compose.onNodeWithTag("catalog_viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("Title reading space must not shorten the menu", sidebar.bottom > catalog.bottom)
        focus("sidebar_subscriptions")
        repeat(6) { device.pressDPadDown(); compose.waitForIdle() }
        compose.onNodeWithText("Выйти").assertIsFocused().assertIsDisplayed()
    }

    @Test fun restartActionPassesAnExplicitOneTimeStartArgument() {
        screen(); openHistory(); actions("h0")
        focus("post_action_restart"); key("post_action_restart", Key.DirectionCenter)
        compose.onNodeWithTag("history_test_player").assertExists()
        assertEquals(Triple("author", "h0", true), opened)
        device.pressBack()
        compose.onNodeWithTag("history_post_h0").assertIsFocused()
    }

    @Test fun historyReturningFromPlayerFindsTheSamePostAfterItMovesToTheFront() {
        screen(); openHistory()
        compose.onNodeWithTag("history_grid").performScrollToIndex(24)
        focus("history_post_h24")
        key("history_post_h24", Key.DirectionCenter)
        compose.onNodeWithTag("history_test_player").assertExists()
        compose.runOnIdle {
            val watched = entries[24].copy(updatedAt = 1_000)
            history.value = HistoryUiState.Content(listOf(watched) + entries.filterNot { it.postId == watched.postId })
        }
        device.pressBack()
        compose.onNodeWithTag("history_post_h24").assertIsFocused().assertIsDisplayed()
    }

    @Test fun removingHistoryDoesNotRemoveContinueWatchingAndLeavesAWorkingRemoteFocus() {
        screen(); openHistory(); actions("h0")
        focus("post_action_remove"); key("post_action_remove", Key.DirectionCenter)
        compose.waitUntilDoesNotExist(hasTestTag("post_actions"), 4_000)
        compose.onNodeWithTag("history_post_h0").assertDoesNotExist()
        compose.onNodeWithTag("sidebar_history").assertIsFocused()
        device.pressBack()
        compose.onNodeWithTag("continue_watching").assertIsDisplayed()
        compose.onNodeWithText("История 0").assertExists()
    }

    @Test fun failedRemovalStaysInTheMenuWithRetryAndDoesNotHideTheCard() {
        removeSucceeds = false
        screen(); openHistory(); actions("h0")
        focus("post_action_remove"); key("post_action_remove", Key.DirectionCenter)
        compose.onNodeWithTag("post_action_error").assertIsDisplayed()
        compose.onNodeWithTag("post_action_remove").assertIsFocused()
        device.pressBack()
        compose.onNodeWithTag("history_post_h0").assertIsFocused()
    }

    @Test fun historyWorksWithoutTheOnlineFeedAndOffersAnEmptyStateAndRetry() {
        history.value = HistoryUiState.Content(emptyList())
        screen(offline = true)
        focus("sidebar_history"); key("sidebar_history", Key.DirectionCenter)
        compose.onNodeWithText("История пока пуста").assertIsDisplayed()
        compose.runOnIdle { history.value = HistoryUiState.Failed }
        compose.onNodeWithText("Не удалось открыть историю").assertIsDisplayed()
        compose.onNodeWithText("Попробовать снова").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Попробовать снова").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("history_post_h0").assertExists()
        assertEquals(1, retries)
    }

    @Test fun fullTitleCanBeReadFromActionsWithoutASeparateInfoButton() {
        history.value = HistoryUiState.Content(listOf(entries.first().copy(title = "Длинное название публикации. ".repeat(100))))
        screen(); openHistory(); actions("h0")
        focus("post_action_title"); key("post_action_title", Key.DirectionCenter)
        compose.onNodeWithTag("full_title_dialog").assertIsDisplayed()
        compose.onNodeWithTag("full_title_text").assertIsFocused()
        key("full_title_text", Key.DirectionDown)
        device.pressBack()
        compose.onNodeWithTag("post_actions").assertIsDisplayed()
        device.pressBack()
        compose.onNodeWithTag("history_post_h0").assertIsFocused()
        assertNull(opened)
    }

    private fun openHistory() {
        focus("sidebar_history"); key("sidebar_history", Key.DirectionCenter)
        key("sidebar_history", Key.DirectionRight)
        compose.onNodeWithTag("history_post_h0").assertIsFocused()
    }
    private fun actions(post: String) {
        compose.onNodeWithTag("history_post_$post").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithTag("post_actions").assertIsDisplayed()
    }
    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
    }
    private fun key(tag: String, key: Key) {
        compose.onNodeWithTag(tag).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun screen(offline: Boolean = false) {
        compose.setContent { BoostyVPTheme {
            val nav = rememberNavController()
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeLayout(if (offline) HomeUiState.Error("Offline") else HomeUiState.Content(emptyList()),
                        onPostClick = { nav.navigate(Routes.player(it.blogUrl, it.id)) },
                        onPostFromStart = { nav.navigate(Routes.player(it.blogUrl, it.id, true)) },
                        onBlogClick = { author = it }, onSignOut = {}, onRefresh = {},
                        continueWatching = listOf(card(entries.first())), history = history,
                        onHistoryRetry = { retries++; history.value = HistoryUiState.Content(entries) },
                        onRemoveHistory = { post ->
                            if (removeSucceeds) {
                                val before = (history.value as HistoryUiState.Content).entries
                                history.value = HistoryUiState.Content(before.filterNot { it.postId == post.id })
                            }
                            removeSucceeds
                        })
                }
                playerDestination { blog, post, fromStart ->
                    opened = Triple(blog, post, fromStart)
                    Box(Modifier.fillMaxSize().testTag("history_test_player")) {
                        AndroidView(factory = { PlayerView(it).apply { isFocusable = true; requestFocus() } },
                            modifier = Modifier.fillMaxSize())
                    }
                }
            }
        } }
    }
    private fun card(p: WatchProgress) = Post(p.postId, 0, p.title, p.blogUrl, p.blogTitle, 0,
        true, false, false, false, null, listOf(ContentBlock.Video(p.videoId, null, null, 600,
            null, null, null, null, null, emptyList(), true)), emptyList())
}
