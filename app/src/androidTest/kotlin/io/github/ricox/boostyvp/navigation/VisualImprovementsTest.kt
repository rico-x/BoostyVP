package io.github.ricox.boostyvp.navigation

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
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
import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.VideoSource
import io.github.ricox.boostyvp.core.ui.appearance.CardSize
import io.github.ricox.boostyvp.core.ui.appearance.CardSizePreferences
import io.github.ricox.boostyvp.core.ui.appearance.CardTextSize
import io.github.ricox.boostyvp.core.ui.appearance.CatalogDensity
import io.github.ricox.boostyvp.core.ui.appearance.InterfaceOptions
import io.github.ricox.boostyvp.core.ui.appearance.InterfacePreferences
import io.github.ricox.boostyvp.core.ui.appearance.InterfaceSettingsDialog
import io.github.ricox.boostyvp.core.ui.appearance.LocalCardSize
import io.github.ricox.boostyvp.core.ui.component.PostCard
import io.github.ricox.boostyvp.core.ui.component.PostRow
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.feature.home.HomeLayout
import io.github.ricox.boostyvp.feature.home.HomeRow
import io.github.ricox.boostyvp.feature.home.HomeUiState
import java.io.File
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated debug preferences and synthetic posts; never touches the real account or video. */
@RunWith(AndroidJUnit4::class)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class VisualImprovementsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val preferences get() = InterfacePreferences(context)

    @Before fun prepare() {
        device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
        clearPresentationPreferences()
    }

    @After fun restoreDefaults() = clearPresentationPreferences()

    private fun clearPresentationPreferences() {
        context.getSharedPreferences("interface_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun sidebarCollapsesInContentAndExpandsOnRemoteReturn() {
        screen()
        focus("sidebar_subscriptions")
        val expanded = compose.onNodeWithTag("home_sidebar").fetchSemanticsNode().boundsInRoot.width
        val viewport = compose.onNodeWithTag("catalog_viewport").fetchSemanticsNode().boundsInRoot
        key("sidebar_subscriptions", Key.DirectionRight)
        compose.onNodeWithText("Автор 0").assertIsFocused()
        val collapsed = compose.onNodeWithTag("home_sidebar").fetchSemanticsNode().boundsInRoot.width
        assertTrue(collapsed < expanded)
        assertEquals("Menu overlays the catalog instead of resizing it", viewport,
            compose.onNodeWithTag("catalog_viewport").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("Подписки").assertExists()
        compose.onNodeWithText("Подписки").assertDoesNotExist()
        compose.onNodeWithText("Автор 0").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar_subscriptions").assertIsFocused()
        compose.onNodeWithText("Подписки").assertIsDisplayed()
        assertEquals(expanded, compose.onNodeWithTag("home_sidebar").fetchSemanticsNode().boundsInRoot.width)
        assertEquals(viewport, compose.onNodeWithTag("catalog_viewport").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun autoCollapseCanBeDisabledWithoutChangingRemoteRoutes() {
        preferences.save(InterfaceOptions(autoCollapseSidebar = false))
        screen()
        focus("sidebar_subscriptions")
        val before = compose.onNodeWithTag("home_sidebar").fetchSemanticsNode().boundsInRoot.width
        key("sidebar_subscriptions", Key.DirectionRight)
        compose.onNodeWithText("Автор 0").assertIsFocused()
        assertEquals(before, compose.onNodeWithTag("home_sidebar").fetchSemanticsNode().boundsInRoot.width)
        compose.onNodeWithText("Подписки").assertIsDisplayed()
    }

    @Test fun remoteSettingsPersistAndNestedSizeBarReturnsToTheSameButton() {
        CardSizePreferences(context).save(CardSize.Medium)
        screen()
        openSettings()
        compose.onNodeWithTag("interface_card_size").assertIsFocused()
        key("interface_card_size", Key.DirectionCenter)
        compose.onNodeWithTag("card_size_slider").assertIsFocused()
        key("card_size_slider", Key.DirectionRight)
        device.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("interface_card_size").assertIsFocused()
        key("interface_card_size", Key.DirectionDown)
        key("interface_sidebar", Key.DirectionLeft)
        assertFalse(preferences.load().autoCollapseSidebar)
        focus("interface_category_text")
        key("interface_category_text", Key.DirectionCenter)
        key("interface_text_size", Key.DirectionRight)
        assertEquals(CardTextSize.Large, preferences.load().textSize)
        key("interface_text_size", Key.DirectionDown)
        key("interface_metadata_size", Key.DirectionDown)
        key("interface_title_lines", Key.DirectionRight)
        focus("interface_category_catalog")
        key("interface_category_catalog", Key.DirectionCenter)
        key("interface_card_size", Key.DirectionDown)
        key("interface_sidebar", Key.DirectionDown)
        key("interface_density", Key.DirectionLeft)
        assertEquals(CatalogDensity.Compact, preferences.load().density)
        assertEquals(3, preferences.load().titleLines)
        // Saving new settings must not erase the existing selected thumbnail size.
        assertEquals(CardSize.Large, CardSizePreferences(context).load())
        device.takeScreenshot(File(context.getExternalFilesDir(null), "visual-settings.png"))
        device.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("sidebar_interface").assertIsFocused()
        openSettings()
        assertEquals(CardTextSize.Large, InterfacePreferences(context).load().textSize)
        compose.onNodeWithTag("interface_preview").assertWidthIsEqualTo(216.dp)
    }

    @Test fun cardTextAndThumbnailSizesAreIndependent() {
        var size by mutableStateOf(CardSize.Large)
        compose.setContent {
            BoostyVPTheme {
                CompositionLocalProvider(LocalCardSize provides size) {
                    PostCard(post("one", "Название для проверки текста"), {}, Modifier.testTag("sizing_card"))
                }
            }
        }
        fun titleHeight() = compose.onNodeWithText("Название для проверки текста", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.height
        val normal = titleHeight()
        compose.runOnIdle { preferences.save(InterfaceOptions(textSize = CardTextSize.Largest)) }
        compose.waitForIdle()
        val large = titleHeight()
        assertTrue(large > normal)
        compose.onNodeWithTag("sizing_card").assertWidthIsEqualTo(216.dp)
        compose.runOnIdle { size = CardSize.Smallest }
        compose.waitForIdle()
        compose.onNodeWithTag("sizing_card").assertWidthIsEqualTo(96.dp)
        assertEquals(large, titleHeight())
    }

    @Test fun metadataCanBeHiddenButAccessBadgesRemain() {
        compose.setContent {
            BoostyVPTheme {
                Column {
                    PostCard(post("author", "В ряду автора"), {}, inAuthorRow = true)
                    PostCard(post("mixed", "В общей ленте"), {})
                    PostCard(post("locked", "Недоступное видео").copy(hasAccess = false), {})
                }
            }
        }
        compose.runOnIdle { preferences.save(InterfaceOptions(hideRepeatedAuthor = true)) }
        compose.waitForIdle()
        compose.onAllNodesWithText("Тестовый автор", useUnmergedTree = true).assertCountEquals(2)
        compose.runOnIdle { preferences.save(InterfaceOptions(showAuthor = false, showDate = false, showDuration = false)) }
        compose.waitForIdle()
        compose.onAllNodesWithText("Тестовый автор", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("12:34", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("По подписке").assertExists()
    }

    @Test fun densityChangesGapsNotCardOrTextSizeAndRetainsFocus() {
        compose.setContent {
            BoostyVPTheme {
                PostRow("Ряд", listOf(post("a", "Карточка 0"), post("b", "Карточка 1")), {})
            }
        }
        fun gap(): Float {
            val a = compose.onNodeWithText("Карточка 0").fetchSemanticsNode().boundsInRoot
            val b = compose.onNodeWithText("Карточка 1").fetchSemanticsNode().boundsInRoot
            return b.left - a.right
        }
        val normal = gap()
        compose.onNodeWithText("Карточка 1").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.runOnIdle { preferences.save(InterfaceOptions(density = CatalogDensity.Compact)) }
        compose.waitForIdle()
        assertTrue(gap() < normal)
        compose.onNodeWithText("Карточка 1").assertIsFocused()
        compose.onNodeWithText("Карточка 0").assertWidthIsEqualTo(216.dp)
    }

    @Test fun newGridReflowsAndPlayerReturnKeepsTheSelectedCardAndCollapsedMenu() {
        CardSizePreferences(context).save(CardSize.Smallest)
        preferences.save(InterfaceOptions(density = CatalogDensity.Compact))
        screen()
        focus("sidebar_new")
        key("sidebar_new", Key.DirectionCenter)
        key("sidebar_new", Key.DirectionRight)
        compose.onNodeWithText("Свежее 0").assertIsFocused()
        repeat(3) { index -> compose.onNodeWithText("Свежее $index").performKeyInput { pressKey(Key.DirectionRight) } }
        compose.onNodeWithText("Свежее 3").assertIsFocused()
        compose.runOnIdle { preferences.save(InterfaceOptions(density = CatalogDensity.Spacious)) }
        compose.waitForIdle()
        compose.onNodeWithText("Свежее 3").assertIsFocused()
        val collapsed = compose.onNodeWithTag("home_sidebar").fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithText("Свежее 3").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("visual_test_player").assertExists()
        device.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Свежее 3").assertIsFocused().assertIsDisplayed()
        assertEquals(collapsed, compose.onNodeWithTag("home_sidebar").fetchSemanticsNode().boundsInRoot.width)
    }

    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
    }

    private fun key(tag: String, key: Key) {
        compose.onNodeWithTag(tag).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun openSettings() { focus("sidebar_interface"); key("sidebar_interface", Key.DirectionCenter) }

    private fun screen() { compose.setContent { BoostyVPTheme { FixtureHome() } } }

    @Composable
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun FixtureHome() {
        val nav = rememberNavController()
        NavHost(nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                var settingsOpen by remember { mutableStateOf(false) }
                HomeLayout(subscriptions, { nav.navigate(Routes.player(it.blogUrl, it.id)) }, {}, {}, {},
                    newPosts = newFlow, onInterfaceSettings = { settingsOpen = true })
                if (settingsOpen) InterfaceSettingsDialog { settingsOpen = false }
            }
            playerDestination { _, _ ->
                Box(Modifier.fillMaxSize().testTag("visual_test_player")) {
                    AndroidView(factory = { context -> PlayerView(context).apply { isFocusable = true; requestFocus() } },
                        modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    private val subscriptions = HomeUiState.Content((0..1).map { row ->
        HomeRow(Blog("blog$row", "Канал $row", "Автор $row", null, null, 0, 10, true, false, null),
            (0..9).map { column -> post("$row.$column", "Видео $row.$column") })
    })
    private val newFlow = flowOf(PagingData.from((0 until 30).map { post("new$it", "Свежее $it") }))

    private fun post(id: String, title: String) = Post(id, 0, title, "test", "Тестовый автор", 1_700_000_000,
        true, false, false, false, null,
        listOf(ContentBlock.Video("v$id", null, null, 754, null, null, null, null, null,
            listOf(VideoSource(VideoSource.Kind.Progressive, "about:blank", 1080, "fixture")), true)), emptyList())
}
