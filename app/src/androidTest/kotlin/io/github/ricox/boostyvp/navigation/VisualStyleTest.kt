package io.github.ricox.boostyvp.navigation

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.tv.material3.MaterialTheme
import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.VideoSource
import io.github.ricox.boostyvp.core.ui.appearance.*
import io.github.ricox.boostyvp.core.ui.component.PostCard
import io.github.ricox.boostyvp.core.ui.component.TvActionButton
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.feature.blog.BlogHeader
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Only synthetic content and disposable debug preferences; never uses real account data. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class VisualStyleTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = InterfacePreferences(context)
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    @Before fun prepare() { device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP); clear() }
    @After fun clean() = clear()
    private fun clear() { context.getSharedPreferences("interface_preferences", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun allVisualPreferencesRoundTripWithoutResettingCardSizeOrPlayerTransport() {
        CardSizePreferences(context).save(CardSize.Small)
        val visual = InterfaceOptions(cardStyle = CardStyle.Light, focusColor = FocusColor.Accent,
            focusWidth = 4, focusZoom = false, animateFocus = false, fullTitle = true,
            authorHeader = AuthorHeaderSize.Minimal, theme = DarkTheme.Black, accent = AccentColor.Teal,
            playerLayout = PlayerPanelLayout.Lower, playerText = PlayerTextSize.Largest,
            playerThickSeekBar = true, playerClock = true, playerEndTime = true)
        val transport = io.github.ricox.boostyvp.core.ui.player.PlayerPreferences(context).load()
        prefs.save(visual)
        assertEquals(visual, InterfacePreferences(context).load())
        assertEquals(CardSize.Small, CardSizePreferences(context).load())
        assertEquals(transport, io.github.ricox.boostyvp.core.ui.player.PlayerPreferences(context).load())
    }

    @Test fun stylesAndFocusOptionsKeepTheSameRemoteTargetAndClick() {
        var clicked = ""
        compose.setContent { BoostyVPTheme { Row {
            PostCard(post("a", "Первая карточка"), { clicked = "a" }, Modifier.testTag("a"))
            PostCard(post("b", "Вторая карточка"), { clicked = "b" }, Modifier.testTag("b"))
        } } }
        focus("a")
        compose.runOnIdle { prefs.save(InterfaceOptions(cardStyle = CardStyle.Light, focusZoom = false,
            animateFocus = false, focusColor = FocusColor.Accent, focusWidth = 4)) }
        compose.onNodeWithTag("a").assertIsFocused().assertWidthIsEqualTo(216.dp)
        key("a", Key.DirectionRight)
        compose.onNodeWithTag("b").assertIsFocused()
        key("b", Key.DirectionCenter)
        assertEquals("b", clicked)
        compose.runOnIdle { prefs.save(prefs.load().copy(cardStyle = CardStyle.Filled, animateFocus = true)) }
        compose.onNodeWithTag("b").assertIsFocused()
    }

    @Test fun fullTitleIsStaticSelectedOnlyAndNeverStealsKeys() {
        prefs.save(InterfaceOptions(fullTitle = true))
        val long = "Очень длинное полное название публикации, которое не помещается в две строки маленькой карточки и должно читаться целиком"
        compose.setContent { BoostyVPTheme { Row {
            PostCard(post("a", long), {}, Modifier.testTag("a"))
            PostCard(post("b", "Другое название"), {}, Modifier.testTag("b"))
        } } }
        focus("a")
        compose.waitUntilExactlyOneExists(hasTestTag("focused_full_title"), 3_000)
        compose.onNodeWithTag("focused_full_title").assertTextEquals(long).assertIsDisplayed()
        compose.onNodeWithTag("a").assertIsFocused()
        device.takeScreenshot(File(context.getExternalFilesDir(null), "visual-full-title.png"))
        key("a", Key.DirectionRight)
        compose.onNodeWithTag("focused_full_title").assertDoesNotExist()
        compose.onNodeWithTag("b").assertIsFocused()
        compose.runOnIdle { prefs.save(prefs.load().copy(fullTitle = false)) }
        compose.onNodeWithTag("focused_full_title").assertDoesNotExist()
        compose.onNodeWithTag("b").assertIsFocused()
    }

    @Test fun fullTitleHidesBehindAnotherWindowAndReturnsWithCardFocus() {
        prefs.save(InterfaceOptions(fullTitle = true))
        var open by mutableStateOf(false)
        compose.setContent { BoostyVPTheme {
            PostCard(post("a", "Очень длинное полное название публикации ".repeat(5)), { open = true }, Modifier.testTag("a"))
            if (open) Dialog(onDismissRequest = { open = false }) { TvActionButton("Закрыть окно", { open = false }) }
        } }
        focus("a")
        compose.waitUntilExactlyOneExists(hasTestTag("focused_full_title"), 3_000)
        key("a", Key.DirectionCenter)
        compose.onNodeWithTag("focused_full_title").assertDoesNotExist()
        device.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("a").assertIsFocused()
        compose.waitUntilExactlyOneExists(hasTestTag("focused_full_title"), 3_000)
        compose.onNodeWithTag("focused_full_title").assertIsDisplayed()
    }

    @Test fun fourAuthorHeadersReallyGetShorterAndBackStillWorks() {
        var variant by mutableStateOf(AuthorHeaderSize.Spacious)
        var back = 0
        compose.setContent { BoostyVPTheme {
            CompositionLocalProvider(LocalInterfaceOptions provides InterfaceOptions(authorHeader = variant)) {
                Column(Modifier.fillMaxWidth()) {
                    BlogHeader(Blog("test", "Описание канала", "Имя автора", null, null, 0, 120, true, false, null),
                        "test", { back++ })
                }
            }
        } }
        var previous = Float.MAX_VALUE
        AuthorHeaderSize.entries.forEach { option ->
            compose.runOnIdle { variant = option }
            val height = compose.onNodeWithTag("author_header").fetchSemanticsNode().boundsInRoot.height
            assertTrue("${option.id}: $height must be smaller than $previous", height < previous)
            previous = height
            compose.onNodeWithText("Имя автора").assertIsDisplayed()
            compose.onNodeWithText("Вы подписаны").assertIsDisplayed()
            // Semantics can settle one frame before the first screenshot buffer.
            android.os.SystemClock.sleep(150)
            device.takeScreenshot(File(context.getExternalFilesDir(null), "visual-author-${option.id}.png"))
            compose.onNodeWithText("Назад").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.onNodeWithText("Назад").performKeyInput { pressKey(Key.DirectionCenter) }
        }
        assertEquals(4, back)
    }

    @Test fun themesAndAccentsApplyImmediatelyWithoutLosingFocus() {
        var background = Color.Unspecified
        var accent = Color.Unspecified
        compose.setContent { BoostyVPTheme {
            background = MaterialTheme.colorScheme.background
            accent = MaterialTheme.colorScheme.primary
            PostCard(post("a", "Карточка"), {}, Modifier.testTag("a"))
        } }
        focus("a")
        DarkTheme.entries.forEach { theme -> AccentColor.entries.forEach { color ->
            compose.runOnIdle { prefs.save(prefs.load().copy(theme = theme, accent = color)) }
            compose.waitForIdle()
            assertEquals(Color(theme.background), background)
            assertEquals(Color(color.argb), accent)
            compose.onNodeWithTag("a").assertIsFocused()
        } }
    }

    @Test fun newSettingsAreReachableByRemoteAndSaved() {
        compose.setContent { BoostyVPTheme { InterfaceSettingsDialog {} } }
        val settings = mapOf("catalog" to listOf("card_style"),
            "focus" to listOf("focus_color", "focus_width", "focus_zoom", "focus_animation", "full_title"),
            "author" to listOf("author_header"), "theme" to listOf("theme", "accent"),
            "player" to listOf("player_layout", "player_text", "player_seek_bar", "player_clock", "player_end_time"))
        settings.forEach { (category, options) ->
          focus("interface_category_$category")
          key("interface_category_$category", Key.DirectionCenter)
          options.forEach { suffix ->
            val tag = "interface_$suffix"
            compose.onNodeWithTag("interface_settings_list").performScrollToNode(hasTestTag(tag))
            focus(tag)
            key(tag, Key.DirectionRight)
            compose.onNodeWithTag(tag).assertIsFocused()
          }
        }
        val result = prefs.load()
        assertEquals(CardStyle.Light, result.cardStyle)
        assertEquals(4, result.focusWidth)
        assertTrue(result.fullTitle && result.playerClock && result.playerEndTime && result.playerThickSeekBar)
        assertEquals(AuthorHeaderSize.Reduced, result.authorHeader)
        assertEquals(PlayerPanelLayout.Lower, result.playerLayout)
        assertEquals(PlayerTextSize.Large, result.playerText)
        assertEquals(DarkTheme.Black, result.theme)
        assertEquals(AccentColor.Blue, result.accent)
    }

    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
    }
    private fun key(tag: String, key: Key) {
        compose.onNodeWithTag(tag).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
    private fun post(id: String, title: String) = Post(id, 0, title, "test", "Тестовый автор", 1_700_000_000,
        true, false, false, false, null,
        listOf(ContentBlock.Video("v$id", null, null, 754, null, null, null, null, null,
            listOf(VideoSource(VideoSource.Kind.Progressive, "about:blank", 1080, "fixture")), true)), emptyList())
}
