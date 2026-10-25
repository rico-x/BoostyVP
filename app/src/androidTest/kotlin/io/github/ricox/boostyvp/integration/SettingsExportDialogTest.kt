package io.github.ricox.boostyvp.integration

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.localization.Language
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SettingsExportDialogTest {
    @get:Rule val compose = createComposeRule()
    private val previous = Language.selected
    private val app = ExportApp("example.files", "example.files.Save", "File manager", ExportMethod.SaveCopy)
    @After fun cleanup() { Language.selected = previous }

    private fun chooser(language: String) {
        Language.selected = language
        var picked: ExportApp? = null
        compose.setContent { BoostyVPTheme { SettingsExportDialog(listOf(app), false, "", { picked = it }, {}, {}) } }
        compose.onNodeWithTag("export_app_${app.packageName}").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(app, picked) }
    }
    @Test fun russianChooserAllowsRemoteAppSelection() = chooser("ru")
    @Test fun englishChooserAllowsRemoteAppSelection() = chooser("en")
    @Test fun noHandlerOffersBrowserTransferInsteadOfTVStub() {
        var browser = false
        compose.setContent { BoostyVPTheme { SettingsExportDialog(emptyList(), false, "", {}, { browser = true }, {}) } }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertTrue(browser) }
    }
}
