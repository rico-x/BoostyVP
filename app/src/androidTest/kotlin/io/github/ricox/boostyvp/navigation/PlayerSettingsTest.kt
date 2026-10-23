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
import io.github.ricox.boostyvp.core.ui.player.EndAction
import io.github.ricox.boostyvp.core.ui.player.PlayerOptions
import io.github.ricox.boostyvp.core.ui.player.PlayerPreferences
import io.github.ricox.boostyvp.core.ui.player.PlayerSettingsDialog
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.feature.home.HomeLayout
import io.github.ricox.boostyvp.feature.home.HomeUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun newPlaybackSettingsAreChangedWithRemoteAndPersist() {
        val preferences = PlayerPreferences(context)
        compose.setContent {
            BoostyVPTheme {
                var options by remember { mutableStateOf(preferences.load()) }
                PlayerSettingsDialog(options, {
                    options = it
                    preferences.save(it)
                }, {})
            }
        }
        activate("Скорость для каждого автора: нет")
        activate("Запоминать качество: да")
        activate("В конце видео: остановиться")
        activate("Таймер сна: выкл")
        compose.onNodeWithText("Таймер сна: 30 мин").assertIsDisplayed()
        compose.onNodeWithText("Пауза, если столько времени не нажимать кнопки пульта во время просмотра.")
            .assertIsDisplayed()
        device.takeScreenshot(File(context.getExternalFilesDir(null), "player-settings-playback.png"))
        // The last rows are below the fold of a 540 dp screen; focus scrolls them in.
        activate("Готово")
        compose.runOnIdle {
            val saved = PlayerPreferences(context).load()
            assertEquals(true, saved.speedPerAuthor)
            assertEquals(false, saved.rememberQuality)
            assertEquals(EndAction.Next, saved.endAction)
            assertEquals(30, saved.sleepTimerMinutes)
        }
    }

    @Test fun speedIsKeptPerAuthorWhenAsked() {
        val preferences = PlayerPreferences(context)
        preferences.saveSpeed(1.25f)
        preferences.save(preferences.load().copy(speedPerAuthor = true))
        preferences.saveSpeed(1.5f, "author-a")
        preferences.saveSpeed(0.75f, "author-b")
        assertEquals(1.5f, PlayerPreferences(context).playbackSpeed("author-a"), 0f)
        assertEquals(0.75f, PlayerPreferences(context).playbackSpeed("author-b"), 0f)
        assertEquals("A new author starts at the common speed", 1.25f, PlayerPreferences(context).playbackSpeed("author-c"), 0f)
        preferences.save(preferences.load().copy(speedPerAuthor = false))
        assertEquals(1.25f, PlayerPreferences(context).playbackSpeed("author-a"), 0f)
    }

    @Test fun qualityIsRememberedUntilSwitchedOff() {
        val preferences = PlayerPreferences(context)
        assertNull(preferences.preferredQuality())
        preferences.savePreferredQuality(720)
        assertEquals(720, PlayerPreferences(context).preferredQuality())
        preferences.savePreferredQuality(null)
        assertNull("Авто forgets the height", PlayerPreferences(context).preferredQuality())
        preferences.savePreferredQuality(1080)
        preferences.save(preferences.load().copy(rememberQuality = false))
        assertNull(PlayerPreferences(context).preferredQuality())
        preferences.savePreferredQuality(480)
        preferences.save(preferences.load().copy(rememberQuality = true))
        assertNull("Nothing is stored while switched off", PlayerPreferences(context).preferredQuality())
    }

    private fun activate(label: String) {
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText(label).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }
}
