package io.github.ricox.boostyvp.navigation

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ricox.boostyvp.core.model.*
import io.github.ricox.boostyvp.core.ui.appearance.*
import io.github.ricox.boostyvp.core.ui.component.*
import io.github.ricox.boostyvp.core.ui.player.*
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.core.data.library.*
import io.github.ricox.boostyvp.feature.home.*
import io.github.ricox.boostyvp.integration.*
import io.github.ricox.boostyvp.localization.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class BilingualLibraryUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val previous = Language.selected
    private var page by mutableStateOf("menu")
    private val post = Post("synthetic-favorite", 0, "A complete publication title", "creator", "Example creator", 1_700_000_000,
        true, false, false, false, null, listOf(ContentBlock.Video("v", null, null, 600, 1920, 1080,
            null, null, null, emptyList(), true), ContentBlock.Text("<p>First paragraph</p><p>Second paragraph</p>", null)), emptyList())
    @Before fun prepare() {
        check(context.packageName.endsWith(".debug")); device.wakeUp()
        context.getSharedPreferences("interface_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun cleanup() {
        Language.selected = previous
        context.getSharedPreferences("interface_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun activate(tag: String) {
        val node = compose.onNodeWithTag(tag)
        try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }
        node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        node.performKeyInput { pressKey(Key.DirectionCenter) }; compose.waitForIdle()
    }
    private fun assertNoTextOverflow() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
            and !hasAnyAncestor(hasTestTag("interface_preview_panel")), useUnmergedTree = true)
        val count = nodes.fetchSemanticsNodes().size
        assertTrue("No measurable UI labels", count > 0)
        for (index in 0 until count) {
            val layouts = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach { assertFalse("Truncated UI text: ${it.layoutInput.text}", it.hasVisualOverflow) }
        }
    }
    private fun settings(code: String) {
        Language.selected = code
        compose.setContent { BoostyVPTheme { key(page) { when(page) {
            "menu" -> MenuSettingsDialog({})
            "visual" -> InterfaceSettingsDialog({})
            "language" -> LanguageSettingsDialog({})
            else -> PlayerSettingsDialog(PlayerOptions(), {}, {})
        } } } }
        for (section in listOf("menu", "visual", "language", "player")) {
            compose.runOnIdle { page = section }; compose.waitForIdle(); assertNoTextOverflow()
            if (section == "visual") InterfaceSection.entries.forEach { category ->
                activate("interface_category_${category.id}"); assertNoTextOverflow()
            }
        }
    }
    @Test fun russianSettingsHaveCompleteReadableLabels() = settings("ru")
    @Test fun englishSettingsHaveCompleteReadableLabels() = settings("en")
    private fun focusPreview(code: String) {
        Language.selected = code
        compose.setContent { BoostyVPTheme { InterfaceSettingsDialog({}) } }
        activate("interface_category_focus")
        compose.onNodeWithText(tr(UiText.interface_preview_020), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("interface_focus_scale").assertTextEquals(tr(UiText.focus_preview_scale, 100, 102))
        compose.onNodeWithTag("interface_focus_duration").assertTextEquals(tr(UiText.focus_preview_transition, 120))
        // Both real visual states appear automatically, leaving the remote on the control.
        compose.waitUntilAtLeastOneExists(hasText(tr(UiText.focus_preview_idle)), 4_000)
        compose.waitUntilAtLeastOneExists(hasText(tr(UiText.focus_preview_selected)), 4_000)
        compose.onNodeWithTag("interface_focus_color").assertIsFocused()
        activate("interface_focus_zoom")
        assertFalse(InterfacePreferences(context).load().focusZoom)
        compose.onNodeWithTag("interface_focus_scale").assertTextEquals(tr(UiText.focus_preview_scale, 100, 100))
        activate("interface_focus_animation")
        assertFalse(InterfacePreferences(context).load().animateFocus)
        compose.onNodeWithTag("interface_focus_duration").assertTextEquals(tr(UiText.focus_preview_transition, 0))
        compose.onNodeWithTag("interface_focus_animation").assertIsFocused()
        assertNoTextOverflow()
    }
    @Test fun russianFocusPreviewDemonstratesBothStatesAndSavesImmediately() = focusPreview("ru")
    @Test fun englishFocusPreviewDemonstratesBothStatesAndSavesImmediately() = focusPreview("en")
    private fun sidebar(code: String) {
        Language.selected = code
        compose.setContent { BoostyVPTheme {
            HomeLayout(HomeUiState.NoSubscriptions, {}, {}, {}, {}, onUnifiedSettings = {})
        } }
        val tags = listOf("sidebar_new", "sidebar_history", "sidebar_later", "sidebar_favorites", "sidebar_favorite_videos", "sidebar_search", "sidebar_queue")
        for (density in MenuDensity.entries) {
            compose.runOnIdle { MenuPreferences(context).save(MenuOptions(density = density, textSp = 14)) }
            compose.onNodeWithTag("sidebar_subscriptions").performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.onNodeWithTag("sidebar_subscriptions").assertHeightIsEqualTo(density.heightDp.dp)
            tags.forEach { tag ->
                device.pressDPadDown(); compose.waitForIdle()
                compose.onNodeWithTag(tag).assertIsFocused().assertIsDisplayed()
            }
            assertNoTextOverflow()
        }
        // Font size and width are independent. Narrow + large text must wrap,
        // not silently truncate a destination that the user needs to identify.
        for (width in listOf(192, 224, 256)) for (font in listOf(12, 14, 16, 18)) {
            compose.runOnIdle { MenuPreferences(context).save(MenuOptions(
                density = MenuDensity.Tight, textSp = font, widthDp = width)) }
            compose.waitForIdle()
            assertNoTextOverflow()
        }
    }
    @Test fun russianSidebarKeepsEveryDestinationReachableAtEveryDensity() = sidebar("ru")
    @Test fun englishSidebarKeepsEveryDestinationReachableAtEveryDensity() = sidebar("en")
    @Test fun menuCanBeReorderedHiddenAndRestoredWithoutHidingSettings() {
        Language.selected = "en"
        compose.setContent { BoostyVPTheme { MenuSettingsDialog({}) } }
        activate("menu_visibility_history")
        assertTrue("history" in MenuPreferences(context).load().hidden)
        activate("menu_up_videos")
        assertTrue(MenuPreferences(context).load().order.indexOf("videos") < MenuOptions.DefaultOrder.indexOf("videos"))
        activate("menu_reset")
        assertEquals(MenuOptions(), MenuPreferences(context).load())
    }
    private fun details(code: String) {
        Language.selected = code
        var opened = 0
        compose.setContent { BoostyVPTheme { CompositionLocalProvider(LocalPostResolver provides { _: Post -> post }) {
            PostDetailsDialog(post, { opened++ }, null, {})
        } } }
        compose.waitUntilDoesNotExist(hasText(tr(UiText.post_details_dialog_008)), 5_000)
        compose.onNodeWithText(post.title).assertExists()
        compose.onNodeWithText("First paragraph\n\nSecond paragraph").assertExists()
        assertNoTextOverflow(); activate("post_details_open"); assertEquals(1, opened)
    }
    @Test fun russianPublicationDetailsLoadTextAndOpenVideo() = details("ru")
    @Test fun englishPublicationDetailsLoadTextAndOpenVideo() = details("en")
    @Test fun favoriteVideoActionIsIndependentOfWatchLaterAndClosesOnlyAfterSaving() {
        Language.selected = "en"
        var library by mutableStateOf(PersonalLibrary())
        var dismissed = false
        compose.setContent { BoostyVPTheme { CompositionLocalProvider(LocalLibraryControls provides LibraryControls(
            library, ready = true, setFavoritePost = { value, add -> library = library.copy(favorites = if (add) listOf(value) else emptyList()) })) {
            if (!dismissed) PostActionsDialog(post, {}, { dismissed = true })
        } } }
        activate("post_action_favorite_video")
        assertEquals(listOf(post.id), library.favorites.map { it.postId }); assertTrue(library.later.isEmpty())
        compose.onNodeWithTag("post_actions").assertDoesNotExist()
    }
    private fun privacy(code: String) {
        Language.selected = code
        var state by mutableStateOf(LibraryPrivacyState("synthetic"))
        var cleared = emptySet<LibraryData>()
        compose.setContent { BoostyVPTheme { LibraryPrivacyDialogContent(state, false, "",
            { state = state.copy(recording = it) }, { _, data -> cleared = data }, {}) } }
        activate("privacy_history"); assertFalse(state.recording.history)
        assertTrue(state.recording.resume); assertTrue(state.recording.watched)
        activate("privacy_select_History"); activate("privacy_select_Videos"); activate("privacy_delete")
        assertTrue(cleared.isEmpty()); assertNoTextOverflow()
        compose.onNodeWithText(tr(UiText.library_privacy_dialog_012)).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText(tr(UiText.library_privacy_dialog_012)).performKeyInput { pressKey(Key.DirectionCenter) }
        assertTrue(cleared.isEmpty()); activate("privacy_delete"); activate("privacy_confirm_delete")
        assertEquals(setOf(LibraryData.History, LibraryData.Videos), cleared)
    }
    @Test fun russianPrivacyRequiresExplicitScopedConfirmation() = privacy("ru")
    @Test fun englishPrivacyRequiresExplicitScopedConfirmation() = privacy("en")
}
