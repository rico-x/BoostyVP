package io.github.ricox.boostyvp.navigation

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ricox.boostyvp.core.model.Blog
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.appearance.CardSize
import io.github.ricox.boostyvp.core.ui.appearance.CardSizePreferences
import io.github.ricox.boostyvp.core.ui.appearance.CardSizeSettingsDialog
import io.github.ricox.boostyvp.core.ui.appearance.LocalCardSize
import io.github.ricox.boostyvp.core.ui.component.PostCard
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.feature.home.HomeLayout
import io.github.ricox.boostyvp.feature.home.HomeRow
import io.github.ricox.boostyvp.feature.home.HomeUiState
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only the disposable .debug package. Never touches the signed app's settings or account. */
@RunWith(AndroidJUnit4::class)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class CardSizeSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun prepare() {
        device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
        context.getSharedPreferences("interface_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun restoreDefaultForOtherUiTests() {
        CardSizePreferences(context).save(CardSize.Default)
    }

    @Test fun remoteBarVisitsAllFiveSizesWithoutEscapingAtTheEnds() {
        screen()
        activate("Интерфейс")
        val slider = compose.onNodeWithTag("card_size_slider")
        slider.assertIsFocused()
        compose.onNodeWithTag("card_size_preview_0").assertWidthIsEqualTo(216.dp)
        repeat(3) {
            slider.performKeyInput { pressKey(Key.DirectionLeft) }
            compose.waitForIdle()
        }
        compose.onNodeWithTag("card_size_preview_0").assertWidthIsEqualTo(96.dp)
        slider.performKeyInput { pressKey(Key.DirectionLeft) }
        slider.assertIsFocused()
        assertEquals(CardSize.Smallest, CardSizePreferences(context).load())
        CardSize.entries.drop(1).forEach { size ->
            slider.performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithTag("card_size_preview_0").assertWidthIsEqualTo(size.widthDp.dp)
        }
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        slider.assertIsFocused()
        assertEquals(CardSize.Largest, CardSizePreferences(context).load())
        device.takeScreenshot(File(context.getExternalFilesDir(null), "card-size-largest.png"))
        slider.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Интерфейс").assertIsFocused()
        // Setting propagated to the existing row, not just the dialog preview.
        compose.onNodeWithText("Видео 0").assertWidthIsEqualTo(296.dp)
    }

    @Test fun backKeepsChoiceReopenRestoresItAndResetKeepsFocus() {
        screen()
        activate("Интерфейс")
        compose.onNodeWithTag("card_size_slider").performKeyInput { pressKey(Key.DirectionLeft) }
        device.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Интерфейс").assertIsFocused()
        assertEquals(CardSize.Medium, CardSizePreferences(context).load())
        activate("Интерфейс")
        compose.onNodeWithTag("card_size_preview_0").assertWidthIsEqualTo(160.dp)
        compose.onNodeWithTag("card_size_slider").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("По умолчанию").assertIsFocused()
        compose.onNodeWithText("По умолчанию").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("По умолчанию").assertIsFocused()
        compose.onNodeWithTag("card_size_preview_0").assertWidthIsEqualTo(216.dp)
        compose.onNodeWithText("По умолчанию").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Закрыть").assertIsFocused()
        compose.onNodeWithText("Закрыть").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(CardSize.Default, CardSizePreferences(context).load())
    }

    @Test fun newPreferenceInstanceAndRecreatedUiKeepChoice() {
        CardSizePreferences(context).save(CardSize.Small)
        assertEquals(CardSize.Small, CardSizePreferences(context).load())
        screen()
        compose.onNodeWithText("Видео 0").assertWidthIsEqualTo(124.dp)
        activate("Интерфейс")
        compose.onNodeWithTag("card_size_preview_0").assertWidthIsEqualTo(124.dp)
        compose.onNodeWithTag("card_size_count").assertIsDisplayed()
        device.takeScreenshot(File(context.getExternalFilesDir(null), "card-size-small.png"))
    }

    @Test fun resizingAnAdaptiveGridChangesColumnsWithoutLosingRemoteFocus() {
        lateinit var preferences: CardSizePreferences
        var density = 1f
        compose.setContent {
            BoostyVPTheme {
                density = LocalDensity.current.density
                preferences = remember { CardSizePreferences(context) }
                LazyVerticalGrid(columns = GridCells.Adaptive(LocalCardSize.current.widthDp.dp)) {
                    items(24) { index -> PostCard(post(index), {}, Modifier.testTag("grid_$index")) }
                }
            }
        }
        compose.onNodeWithTag("grid_1").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        fun width() = compose.onNodeWithTag("grid_0").fetchSemanticsNode().boundsInRoot.width / density
        val original = width()
        compose.runOnIdle { preferences.save(CardSize.Smallest) }
        compose.waitForIdle()
        assertTrue(width() < original)
        compose.onNodeWithTag("grid_1").assertIsFocused()
        compose.onNodeWithTag("grid_1").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("grid_2").assertIsFocused()
        compose.runOnIdle { preferences.save(CardSize.Largest) }
        compose.waitForIdle()
        assertTrue(width() > original)
        compose.onNodeWithTag("grid_2").assertIsFocused()
    }

    private fun screen() {
        val preferences = CardSizePreferences(context)
        compose.setContent {
            BoostyVPTheme {
                var open by remember { mutableStateOf(false) }
                HomeLayout(HomeUiState.Content(listOf(HomeRow(
                    Blog("test", "Автор", "Автор", null, null, 0, 8, true, false, null),
                    (0..9).map(::post)))), {}, {}, {}, {}, onInterfaceSettings = { open = true })
                if (open) CardSizeSettingsDialog(LocalCardSize.current, preferences::save, { open = false })
            }
        }
    }

    private fun activate(label: String) {
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText(label).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }

    private fun post(index: Int) = Post("test_$index", index.toLong(), "Видео $index", "test", "Автор", 0,
        true, false, false, false, null, emptyList(), emptyList())
}
