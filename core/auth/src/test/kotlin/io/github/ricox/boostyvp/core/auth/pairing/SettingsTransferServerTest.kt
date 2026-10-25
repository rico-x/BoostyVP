package io.github.ricox.boostyvp.core.auth.pairing

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class SettingsTransferServerTest {
    private val language = io.github.ricox.boostyvp.localization.Language.selected
    @org.junit.Before fun chooseRussian() { io.github.ricox.boostyvp.localization.Language.selected = "ru" }
    @org.junit.After fun restoreLanguage() { io.github.ricox.boostyvp.localization.Language.selected = language }
    @Test fun `temporary settings page exports only supplied bytes and stages imports`() {
        var received: String? = null
        val server = SettingsTransferServer({ "{\"settings\":true}" }) { received = it }
        val address = server.start("127.0.0.1")
        try {
            assertTrue(URL(address).readText().contains("Настройки BoostyVP"))
            assertEquals("{\"settings\":true}", URL("$address/download").readText())
            val upload = URL("$address/upload").openConnection() as HttpURLConnection
            upload.requestMethod = "POST"; upload.doOutput = true
            upload.outputStream.use { it.write("synthetic-backup".toByteArray()) }
            assertEquals(200, upload.responseCode)
            assertNull(upload.getHeaderField("Access-Control-Allow-Origin"))
            assertEquals("no-store", upload.getHeaderField("Cache-Control"))
            assertEquals("synthetic-backup", received)
            upload.disconnect()
            assertEquals(404, (URL(address.substringBefore("/settings/") + "/settings/wrong/download").openConnection() as HttpURLConnection).responseCode)
        } finally { server.stop() }
        assertTrue(runCatching { URL(address).openConnection().apply { connectTimeout = 300 }.getInputStream() }.isFailure)
    }
    @Test fun `invalid upload leaves settings unchanged and returns a rejection`() {
        val server = SettingsTransferServer({ "{}" }) { error("invalid backup") }
        val address = server.start("127.0.0.1")
        try {
            val upload = URL("$address/upload").openConnection() as HttpURLConnection
            upload.requestMethod = "POST"; upload.doOutput = true
            upload.outputStream.use { it.write("bad".toByteArray()) }
            assertEquals(400, upload.responseCode); upload.disconnect()
        } finally { server.stop() }
    }
}
