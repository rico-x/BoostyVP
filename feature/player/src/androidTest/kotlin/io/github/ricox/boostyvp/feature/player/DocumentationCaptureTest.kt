package io.github.ricox.boostyvp.feature.player

import android.graphics.Color
import android.view.KeyEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ricox.boostyvp.localization.Language
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in documentation capture, never a production account or actual post/video. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DocumentationCaptureTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: TvPlayerView

    @Test fun captureNextUpInBothLanguages() {
        assumeTrue("Opt in with documentationScreenshots=true",
            InstrumentationRegistry.getArguments().getString("documentationScreenshots") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName != "io.github.ricox.boostyvp")
        val device = UiDevice.getInstance(instrumentation)
        device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
        val original = Language.selected
        val locale = mutableStateOf("ru")
        try {
            Language.selected = "ru"
            compose.setContent {
                key(locale.value) {
                    Dialog(onDismissRequest = {}, properties = DialogProperties(
                        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                        TvPlayerHost(onDismiss = {}, factory = { context ->
                            TvPlayerView(context).apply {
                                view = this
                                player = PlaybackReliabilityDeviceTest.StatePlayer()
                                setBackgroundColor(Color.rgb(36, 36, 36))
                                applyPlayerAppearance(this, "BoostyVP")
                                hideController()
                            }
                        }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            for (code in listOf("ru", "en")) {
                compose.runOnIdle { Language.selected = code; locale.value = code }
                compose.waitUntil(5_000) {
                    compose.runOnIdle { ::view.isInitialized && view.hasWindowFocus() && view.hasFocus() }
                }
                compose.runOnIdle {
                    view.showNextUp(if (code == "ru") "Пример: следующее видео автора" else
                        "Example: next creator video", 8, onPlay = {}, onCancel = {})
                }
                compose.waitUntil(3_000) {
                    compose.runOnIdle {
                        val overlay = view.findViewWithTag<NextUpOverlay>(NextUpOverlay.TAG)
                        overlay.isShown && overlay.isLaidOut && overlay.height > 0
                    }
                }
                Thread.sleep(250)
                val file = File(instrumentation.targetContext.getExternalFilesDir(null), "docs-next-up-$code.png")
                assertTrue("Screenshot captured", device.takeScreenshot(file))
                device.pressBack() // Cancel the countdown; no media is actually opened.
                compose.waitForIdle()
            }
        } finally { Language.selected = original }
    }
}
