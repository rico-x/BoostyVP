package io.github.ricox.boostyvp.navigation

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ricox.boostyvp.core.ui.player.PlayerOptions
import io.github.ricox.boostyvp.core.ui.player.PlayerPreferences
import io.github.ricox.boostyvp.core.ui.player.PlayerSettingsDialog
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.feature.home.HomeLayout
import io.github.ricox.boostyvp.feature.home.HomeUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class PlayerSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun prepare() {
        device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
        // Instrumentation targets the disposable .debug package, never the user's release.
        context.getSharedPreferences("player_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun settingsAreChangedWithRemoteAndPersistAcrossReopen() {
        val preferences = PlayerPreferences(context)
        compose.setContent {
            BoostyVPTheme {
                var open by remember { mutableStateOf(false) }
                var options by remember { mutableStateOf(preferences.load()) }
                HomeLayout(HomeUiState.Content(emptyList()), {}, {}, {}, {}, onPlayerSettings = { open = true })
                if (open) PlayerSettingsDialog(options, {
                    options = it
                    preferences.save(it)
                }, { open = false })
            }
        }
        activate("Плеер")
        activate("Назад: 10 с")
        activate("Вперёд: 30 с")
        activate("Панель управления: 4 с")
        activate("Запоминать скорость: да")
        compose.onNodeWithText("Назад: 15 с").assertIsDisplayed()
        device.takeScreenshot(File(context.getExternalFilesDir(null), "player-settings-style.png"))
        activate("Готово")
        compose.runOnIdle {
            assertEquals(PlayerOptions(15, 60, 8, false), PlayerPreferences(context).load())
        }
        activate("Плеер")
        compose.onNodeWithText("Вперёд: 60 с").assertIsDisplayed()
        compose.onNodeWithText("Запоминать скорость: нет").assertIsDisplayed()
        device.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Плеер").assertIsFocused()
    }

    @Test fun speedCanBeRememberedOrExplicitlyDisabled() {
        val preferences = PlayerPreferences(context)
        preferences.saveSpeed(1.5f)
        assertEquals(1.5f, PlayerPreferences(context).playbackSpeed(), 0f)
        preferences.save(preferences.load().copy(rememberSpeed = false))
        preferences.saveSpeed(2f)
        assertEquals(1f, PlayerPreferences(context).playbackSpeed(), 0f)
        assertFalse(PlayerPreferences(context).load().rememberSpeed)
    }

    private fun activate(label: String) {
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText(label).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }
}
