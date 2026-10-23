package io.github.ricox.boostyvp.navigation

import android.provider.Settings
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.feature.pairing.PhoneLoginForm
import io.github.ricox.boostyvp.feature.pairing.PhoneLoginState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native TV inputs and IME; synthetic values, no account or SMS requests. */
@RunWith(AndroidJUnit4::class)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class PhoneLoginRemoteTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remoteMovesBetweenFieldsOpensKeyboardAndActivatesSignIn() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val imePackage = Settings.Secure.getString(instrumentation.targetContext.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD).substringBefore('/')
        var submissions = 0
        compose.setContent {
            BoostyVPTheme {
                PhoneLoginForm(
                    PhoneLoginState(phone = "+79990000000", code = "123456", codeRequested = true),
                    onPhoneChange = {}, onCodeChange = {}, onSubmit = { submissions++ })
            }
        }
        compose.onNodeWithTag("phone_number").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("phone_number").assertIsFocused()
        // TV keyboards can float over the app without reporting any IME inset.
        assertTrue("Phone focus did not open the keyboard", device.wait(Until.hasObject(By.pkg(imePackage)), 5_000))
        device.pressBack()
        assertTrue("Back did not close the keyboard", device.wait(Until.gone(By.pkg(imePackage)), 5_000))
        compose.onNodeWithTag("phone_number").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("phone_code").assertIsFocused()
        assertTrue("Code focus did not open the keyboard", device.wait(Until.hasObject(By.pkg(imePackage)), 5_000))
        device.pressBack()
        assertTrue("Back did not close the keyboard", device.wait(Until.gone(By.pkg(imePackage)), 5_000))
        compose.onNodeWithTag("phone_code").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("phone_submit").assertIsFocused()
        compose.onNodeWithTag("phone_submit").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, submissions) }
    }
}
