package io.github.ricox.boostyvp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.feature.home.HomeLayout
import io.github.ricox.boostyvp.feature.home.HomeRow
import io.github.ricox.boostyvp.feature.home.HomeUiState
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The "Новое" section of the real home layout, driven only by remote keys. */
@RunWith(AndroidJUnit4::class)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class NewFeedFocusTest {
    @get:Rule val compose = createComposeRule()
    private var subscriptionRefreshes = 0
    private var opened: String? = null

    @Before fun wakeDevice() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .pressKeyCode(android.view.KeyEvent.KEYCODE_WAKEUP)
    }

    @Test fun menuSwitchesToTheDateOrderedGridAndBack() {
        screen()
        openNewSection()
        compose.onNodeWithTag("sidebar_new").assertIsSelected()
        compose.onNodeWithText("Свежие публикации всех подписок по дате · Долгое OK — действия").assertIsDisplayed()
        compose.onNodeWithText("Видео 0.0").assertDoesNotExist()

        compose.onNodeWithTag("sidebar_new").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Свежее 0").assertIsFocused()
        compose.onNodeWithText("Свежее 0").performKeyInput { pressKey(Key.DirectionDown) }
        val below = focusedCard()
        compose.onNodeWithText(below).performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar_new").assertIsFocused()

        compose.onNodeWithTag("sidebar_new").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("sidebar_subscriptions").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("sidebar_subscriptions").assertIsSelected().assertIsFocused()
        compose.onNodeWithText("Видео 0.0").assertExists()
    }

    @Test fun leftFromAnyFirstColumnCardReturnsToTheSelectedMenuItem() {
        screen()
        openNewSection()
        compose.onNodeWithTag("sidebar_new").performKeyInput { pressKey(Key.DirectionRight) }
        repeat(3) { compose.onNodeWithText(focusedCard()).performKeyInput { pressKey(Key.DirectionDown) } }
        val focused = focusedCard()
        compose.onNodeWithText(focused).assertIsDisplayed()
        compose.onNodeWithText(focused).performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar_new").assertIsFocused()
    }

    @Test fun backFromNewReturnsToSubscriptionsMenu() {
        screen()
        openNewSection()
        compose.onNodeWithTag("sidebar_new").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Свежее 0").assertIsFocused()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Ваши подписки").assertIsDisplayed()
        compose.onNodeWithTag("sidebar_subscriptions").assertIsSelected().assertIsFocused()
    }

    @Test fun refreshInNewDoesNotReloadSubscriptions() {
        screen()
        openNewSection()
        compose.onNodeWithText("Обновить").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Обновить").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(0, subscriptionRefreshes) }
        compose.onNodeWithText("Свежее 0").assertExists()
    }

    @Test fun returningFromPlayerKeepsSectionAndSelectedCard() {
        screen()
        openNewSection()
        compose.onNodeWithTag("sidebar_new").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Свежее 0").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Свежее 1").performKeyInput { pressKey(Key.DirectionDown) }
        val selected = focusedCard()
        compose.onNodeWithText(selected).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(selected, opened) }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("native_player").fetchSemanticsNodes().isNotEmpty()
        }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("native_player").assertDoesNotExist()
        compose.onNodeWithTag("sidebar_new").assertIsSelected()
        compose.onNodeWithText(selected).assertIsFocused().assertIsDisplayed()
    }

    private fun openNewSection() {
        compose.onNodeWithTag("sidebar_subscriptions").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("sidebar_subscriptions").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("sidebar_new").assertIsFocused()
        compose.onNodeWithTag("sidebar_new").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Свежее 0").assertExists()
    }

    /** The grid's column count depends on the TV; find whichever card has focus. */
    private fun focusedCard(): String = newPosts.map { it.title }.first { title ->
        runCatching { compose.onNodeWithText(title).assertIsFocused() }.isSuccess
    }

    private fun screen() {
        compose.setContent { BoostyVPTheme { TestHome() } }
    }

    @Composable
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun TestHome() {
        val nav = rememberNavController()
        NavHost(nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeLayout(
                    state = subscriptions,
                    onPostClick = {
                        opened = it.title
                        nav.navigate(Routes.player(it.blogUrl, it.id))
                    },
                    onBlogClick = {}, onSignOut = {}, onRefresh = { subscriptionRefreshes++ },
                    newPosts = flowOf(PagingData.from(newPosts)),
                )
            }
            playerDestination { _, _, _ ->
                Box(Modifier.fillMaxSize().testTag("native_player")) {
                    AndroidView(factory = { context ->
                        PlayerView(context).apply { isFocusable = true; requestFocus() }
                    }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    private val subscriptions = HomeUiState.Content((0..1).map { row ->
        HomeRow(
            blog = Blog("blog$row", "Канал $row", "Автор $row", null, null, 0, 8, true, false, null),
            posts = (0..3).map { column ->
                Post("$row.$column", column.toLong(), "Видео $row.$column", "blog$row", "Канал $row", 0,
                    true, false, false, false, null, emptyList(), emptyList())
            },
        )
    })

    // Interleaved authors, as Boosty's own feed returns them.
    private val newPosts = (0 until 24).map { index ->
        Post("new$index", index.toLong(), "Свежее $index", "blog${index % 3}", "Канал ${index % 3}", 0,
            true, false, false, false, null, emptyList(), emptyList())
    }
}
