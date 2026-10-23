package io.github.ricox.boostyvp.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.component.LocalWatchProgress
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.feature.home.HomeLayout
import io.github.ricox.boostyvp.feature.home.HomeRow
import io.github.ricox.boostyvp.feature.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Продолжить просмотр" above the author rows and progress bars on cards. */
@RunWith(AndroidJUnit4::class)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class ContinueWatchingTest {
    @get:Rule val compose = createComposeRule()
    private var opened: String? = null

    @Before fun wakeDevice() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .pressKeyCode(android.view.KeyEvent.KEYCODE_WAKEUP)
    }

    @Test fun rowComesFirstAndKeepsTheRemoteRoute() {
        screen(continueWatching = started)
        compose.onNodeWithTag("continue_watching").assertIsDisplayed()
        compose.onNodeWithTag("sidebar_subscriptions").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("sidebar_subscriptions").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Начатое 0").assertIsFocused()
        compose.onNodeWithText("Начатое 0").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Начатое 1").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Автор 0").assertIsFocused()
        compose.onNodeWithText("Автор 0").performKeyInput { pressKey(Key.DirectionUp) }
        // Vertical entry starts the row at its first card, as for author rows.
        compose.onNodeWithText("Начатое 0").assertIsFocused()
        compose.onNodeWithText("Начатое 0").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar_subscriptions").assertIsFocused()
    }

    @Test fun startedCardOpensItsPost() {
        screen(continueWatching = started)
        compose.onNodeWithText("Начатое 1").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Начатое 1").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("started1", opened) }
    }

    @Test fun aSingleStartedVideoUsesACompactHorizontalCardWithoutChangingRemoteActions() {
        screen(continueWatching = started.take(1))
        compose.onNodeWithTag("sidebar_subscriptions").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("sidebar_subscriptions").performKeyInput { pressKey(Key.DirectionRight) }
        val card = compose.onNodeWithText("Начатое 0").assertIsFocused().assertWidthIsEqualTo(380.dp)
        val bounds = card.fetchSemanticsNode().boundsInRoot
        assertTrue("One item must not consume a full video row", bounds.width > bounds.height * 2)
        card.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("started0", opened) }
        card.performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar_subscriptions").assertIsFocused()
    }

    @Test fun withoutStartedVideosTheHomeIsUnchanged() {
        screen(continueWatching = emptyList())
        compose.onNodeWithTag("continue_watching").assertDoesNotExist()
        compose.onNodeWithTag("sidebar_subscriptions").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("sidebar_subscriptions").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Автор 0").assertIsFocused()
    }

    @Test fun cardsShowHowMuchWasWatched() {
        screen(continueWatching = started, watched = mapOf("started0" to 0.25f, "0.1" to 0.75f))
        // Only the two posts with progress get a bar, each with its own fraction.
        val bars = compose.onAllNodesWithTag("watch_progress", useUnmergedTree = true)
        bars.assertCountEquals(2)
        val values = bars.fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.ProgressBarRangeInfo].current }.sorted()
        assertEquals(listOf(0.25f, 0.75f), values)
    }

    private fun screen(continueWatching: List<Post>, watched: Map<String, Float> = emptyMap()) {
        compose.setContent {
            BoostyVPTheme {
                CompositionLocalProvider(LocalWatchProgress provides watched) {
                    HomeLayout(
                        state = subscriptions, onPostClick = { opened = it.id },
                        onBlogClick = {}, onSignOut = {}, onRefresh = {},
                        continueWatching = continueWatching,
                    )
                }
            }
        }
    }

    private val started = (0..3).map { index ->
        val video = ContentBlock.Video("v$index", null, null, 600, null, null, null, null, null, emptyList(), true)
        Post("started$index", 0, "Начатое $index", "blog$index", "Канал $index", 0,
            true, false, false, false, null, listOf(video), emptyList())
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
}
