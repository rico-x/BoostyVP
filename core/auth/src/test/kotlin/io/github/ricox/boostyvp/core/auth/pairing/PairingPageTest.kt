package io.github.ricox.boostyvp.core.auth.pairing

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPageTest {
    @Test
    fun `pairing page consistently presents the fork name`() {
        val page = PairingPage.html("A".repeat(43))
        assertTrue(page.contains("Подключение к BoostyVP"))
        assertTrue(page.contains("#boostyvp="))
        assertFalse(page.contains("Boosty TV"))
    }

    @Test
    fun `browser bookmark fits Android single line paste limit without truncating the script`() {
        val page = PairingPage.html("A".repeat(43))
        val url = Regex("""<textarea id="bookmark-url"[^>]*>(.*?)</textarea>""")
            .find(page)!!.groupValues[1]
        assertTrue(url.startsWith("javascript:"))
        // Android's EditText silently truncates pasted single-line text at 5,000.
        assertTrue("Bookmark is ${url.length} characters; keep margin below 5,000", url.length <= 4_500)
        val script = checkNotNull(javaClass.getResourceAsStream("/pairing/browser-handoff.js"))
            .bufferedReader().use { it.readText() }
        assertEquals(script, URLDecoder.decode(url.removePrefix("javascript:"), "UTF-8"))
        val installLink = Regex("""<a id="install-bookmark"[^>]*href="([^"]+)"[^>]*draggable="true"[^>]*>BoostyVP</a>""")
            .find(page)!!.groupValues[1]
        assertEquals("Dragging must save the complete script URL, not the pairing page", url, installLink)
    }
}
