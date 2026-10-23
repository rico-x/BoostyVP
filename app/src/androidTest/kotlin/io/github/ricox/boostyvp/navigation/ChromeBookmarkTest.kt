package io.github.ricox.boostyvp.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import io.github.ricox.boostyvp.core.auth.pairing.PairingCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLEncoder
import kotlin.concurrent.thread

private const val BOOKMARK_NAME = "BoostyVP"

/** Opt-in integration check on a disposable emulator with Chrome's first run completed/skipped. */
@RunWith(AndroidJUnit4::class)
class ChromeBookmarkTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun browserHandoffBookmarkExecutesTheScriptPackagedInTheTvApk() {
        val script = checkNotNull(PairingCrypto::class.java.getResourceAsStream("/pairing/browser-handoff.js"))
            .bufferedReader().use { it.readText() }
        val bookmark = "javascript:" + URLEncoder.encode(script, "UTF-8").replace("+", "%20")
        checkBookmark(bookmark, By.textStartsWith("Закладка работает."))
    }

    private fun checkBookmark(bookmark: String, expected: BySelector) {
        assumeTrue("Use -Pandroid.testInstrumentationRunnerArguments.chromeBookmark=true on an emulator",
            InstrumentationRegistry.getArguments().getString("chromeBookmark") == "true")
        assumeTrue("This check creates a bookmark only in an emulator", Build.HARDWARE in listOf("ranchu", "goldfish"))
        val name = "$BOOKMARK_NAME test ${SystemClock.uptimeMillis()}"
        LocalPage().use { page ->
            // Dismiss leftovers if a previous diagnostic run stopped in Chrome's editor/menu.
            if (device.hasObject(By.pkg("com.android.intentresolver"))) device.pressBack()
            if (device.hasObject(By.res("com.android.chrome:id/button_two"))) device.pressBack()
            if (device.hasObject(By.res("com.android.chrome:id/bottom_sheet"))) device.pressBack()
            if (device.hasObject(By.text("Edit bookmark"))) device.pressBack()
            instrumentation.targetContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page.url)).apply {
                setPackage("com.android.chrome")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            device.wait(Until.findObject(By.text("No thanks")), 1_000)?.click()
            require(By.text(LocalPage.TITLE))

            require(By.res("com.android.chrome:id/menu_button")).click()
            require(By.desc("Bookmark")).click()
            require(By.res("com.android.chrome:id/bottom_sheet"))
            device.pressBack()
            require(By.res("com.android.chrome:id/menu_button")).click()
            require(By.res("com.android.chrome:id/button_two")).click()
            require(By.text("Edit bookmark"))
            val fields = device.findObjects(By.clazz("android.widget.EditText"))
            assertEquals("Bookmark editor should contain a title and URL", 2, fields.size)
            fields[0].text = name
            val address = device.findObjects(By.clazz("android.widget.EditText"))[1]
            address.click()
            device.pressKeyCode(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)
            device.pressKeyCode(KeyEvent.KEYCODE_DEL)
            val clipboard = instrumentation.targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("bookmark URL", bookmark))
            device.pressKeyCode(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON)
            assertEquals(bookmark, device.findObjects(By.clazz("android.widget.EditText"))[1].text)
            require(By.desc("Navigate up")).click()

            require(By.text(LocalPage.TITLE))
            require(By.res("com.android.chrome:id/url_bar")).click()
            require(By.res("com.android.chrome:id/url_bar")).text = name
            // Selecting the bookmark suggestion is essential; Enter would start a search.
            require(By.textStartsWith("javascript:")).click()
            require(expected)
            if (device.hasObject(By.res("com.android.chrome:id/positive_button"))) device.pressBack()
            // The production export's origin guard ran on the existing local page.
            assertTrue(device.wait(Until.hasObject(By.text(LocalPage.TITLE)), 5_000))
        }
    }

    private fun require(selector: BySelector): UiObject2 =
        device.wait(Until.findObject(selector), 10_000) ?: run {
            val hierarchy = ByteArrayOutputStream()
            device.dumpWindowHierarchy(hierarchy)
            error("Missing $selector\n$hierarchy")
        }

    private class LocalPage : AutoCloseable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${server.localPort}/link"

        init {
            thread(isDaemon = true, name = "chrome-export-fixture") {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    runCatching {
                        socket.use {
                            socket.soTimeout = 2_000
                            val reader = socket.getInputStream().bufferedReader()
                            while (!reader.readLine().isNullOrEmpty()) { }
                            val body = "<html><head><title>$TITLE</title></head><body><h1>$TITLE</h1><div id='bookmark-test-status' tabindex='-1'>Waiting for bookmark</div></body></html>".toByteArray()
                            socket.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                write(body)
                                flush()
                            }
                        }
                    }
                }
            }
        }

        override fun close() = server.close()
        companion object { const val TITLE = "BoostyVP Chrome test" }
    }
}
