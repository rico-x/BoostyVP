package io.github.ricox.boostyvp.core.auth

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * All three sign-in routes funnel through this, so a regression here breaks
 * manual paste, the on-device WebView and QR pairing at once.
 */
class SessionParserTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `parses plain json as found in localStorage`() {
        val auth = """{"accessToken":"at","refreshToken":"rt","expiresAt":2000000000}"""
        val session = SessionParser.parse(json, auth, "device-1")

        assertEquals("at", session.accessToken)
        assertEquals("rt", session.refreshToken)
        assertEquals(2_000_000_000L, session.expiresAt)
        assertEquals("device-1", session.deviceId)
    }

    @Test
    fun `parses url-encoded json as found in the cookie`() {
        val auth = "%7B%22accessToken%22%3A%22at%22%2C%22refreshToken%22%3A%22rt%22%7D"
        val session = SessionParser.parse(json, auth, "device-1")

        assertEquals("at", session.accessToken)
        assertEquals("rt", session.refreshToken)
    }

    @Test
    fun `snake case field names are accepted`() {
        val auth = """{"access_token":"at","refresh_token":"rt","expires_in":3600}"""
        val session = SessionParser.parse(json, auth, "device-1")
        assertEquals("at", session.accessToken)
        assertEquals("rt", session.refreshToken)
    }

    @Test
    fun `a relative lifetime is converted to an absolute expiry`() {
        val auth = """{"accessToken":"at","refreshToken":"rt","expiresIn":3600}"""
        val now = System.currentTimeMillis() / 1000
        val session = SessionParser.parse(json, auth, "device-1")

        // Should land about an hour out, not in 1970.
        assertTrue(
            "expiresAt was ${session.expiresAt}, expected roughly ${now + 3600}",
            session.expiresAt in (now + 3500)..(now + 3700),
        )
    }

    @Test
    fun `an absolute timestamp is left alone`() {
        val auth = """{"accessToken":"at","refreshToken":"rt","expiresAt":1900000000}"""
        assertEquals(1_900_000_000L, SessionParser.parse(json, auth, "d").expiresAt)
    }

    @Test
    fun `a quoted client id is unwrapped`() {
        // localStorage.getItem often hands back the value still quoted.
        val auth = """{"accessToken":"at","refreshToken":"rt"}"""
        assertEquals("abc", SessionParser.parse(json, auth, "\"abc\"").deviceId)
    }

    @Test
    fun `missing tokens produce a readable message`() {
        val thrown = runCatching {
            SessionParser.parse(json, """{"refreshToken":"rt"}""", "d")
        }.exceptionOrNull()
        assertTrue(thrown?.message?.contains("accessToken") == true)
    }

    @Test
    fun `garbage input is rejected rather than half-parsed`() {
        val thrown = runCatching { SessionParser.parse(json, "not json at all", "d") }
            .exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `blank client id is rejected`() {
        val auth = """{"accessToken":"at","refreshToken":"rt"}"""
        val thrown = runCatching { SessionParser.parse(json, auth, "  ") }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
    }
}
