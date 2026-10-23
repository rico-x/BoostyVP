package io.github.ricox.boostyvp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.feature.home.HomeLayout
import io.github.ricox.boostyvp.feature.home.HomeRow
import io.github.ricox.boostyvp.feature.home.HomeUiState
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the actual home layout, including its full-width author headings. */
@RunWith(AndroidJUnit4::class)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class HomeRowFocusTest {
    @get:Rule val compose = createComposeRule()

    @Before fun wakeDevice() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .pressKeyCode(android.view.KeyEvent.KEYCODE_WAKEUP)
    }

    @Test fun rightFromSidebarAndDownFromHeadingStartAtFirstVideo() {
        screen()
        compose.onNodeWithTag("sidebar_subscriptions").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("sidebar_subscriptions").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Автор 0").assertIsFocused()
        compose.onNodeWithText("Автор 0").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Видео 0.0").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithText("Видео 0.0").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar_subscriptions").assertIsFocused()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "home-style.png"))
    }

    @Test fun reenteringFeedAfterScrollDoesNotUseARecycledFocusNode() {
        screen()
        firstVideo()
        repeat(3) { row ->
            compose.onNodeWithText("Видео $row.0").performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithText("Автор ${row + 1}").performKeyInput { pressKey(Key.DirectionDown) }
        }
        compose.onNodeWithText("Видео 3.0").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar_subscriptions").assertIsFocused()
        compose.onNodeWithTag("sidebar_subscriptions").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("sidebar_subscriptions").assertIsNotFocused()
    }

    @Test fun verticalReentryResetsAPreviouslyScrolledRowToItsStart() {
        screen()
        firstVideo()
        repeat(5) { index ->
            compose.onNodeWithText("Видео 0.$index").performKeyInput { pressKey(Key.DirectionRight) }
        }
        compose.onNodeWithText("Видео 0.5").assertIsFocused()
        compose.onNodeWithText("Видео 0.5").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Автор 1").assertIsFocused()
        compose.onNodeWithText("Автор 1").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Видео 0.0").assertIsFocused().assertIsDisplayed()
        // The same reset applies when entering through this row's own heading.
        compose.onNodeWithText("Видео 0.0").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Видео 0.1").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Автор 0").assertIsFocused()
        compose.onNodeWithText("Автор 0").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Видео 0.0").assertIsFocused().assertIsDisplayed()
    }

    @Test fun returningFromPlayerKeepsTheSelectedVideo() {
        screen()
        firstVideo()
        repeat(4) { index ->
            compose.onNodeWithText("Видео 0.$index").performKeyInput { pressKey(Key.DirectionRight) }
        }
        compose.onNodeWithText("Видео 0.4").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Видео 0.4").assertIsFocused().assertIsDisplayed()
    }

    private fun firstVideo() {
        compose.onNodeWithText("Автор 0").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Автор 0").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Видео 0.0").assertIsFocused()
    }

    private fun screen() {
        compose.setContent {
            BoostyVPTheme { TestHome() }
        }
    }

    @Composable
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun TestHome() {
        val nav = rememberNavController()
        NavHost(nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeLayout(
                    state = fixture,
                    onPostClick = { nav.navigate(Routes.player(it.blogUrl, it.id)) },
                    onBlogClick = {}, onSignOut = {}, onRefresh = {},
                )
            }
            playerDestination { _, _ ->
                Box(Modifier.fillMaxSize()) {
                    AndroidView(factory = { context ->
                        PlayerView(context).apply { isFocusable = true; requestFocus() }
                    }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    private val fixture = HomeUiState.Content((0..3).map { row ->
        HomeRow(
            blog = Blog("blog$row", "Канал $row", "Автор $row", null, null, 0, 8, true, false, null),
            posts = (0..7).map { column ->
                Post("$row.$column", column.toLong(), "Видео $row.$column", "blog$row", "Канал $row", 0,
                    true, false, false, false, null, emptyList(), emptyList())
            },
        )
    })
}
