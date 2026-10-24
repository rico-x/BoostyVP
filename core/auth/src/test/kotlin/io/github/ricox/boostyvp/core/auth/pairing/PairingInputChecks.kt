package io.github.ricox.boostyvp.core.auth.pairing

import io.github.ricox.boostyvp.core.auth.SessionParser
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.EOFException

/**
 * What must hold for anything a device on the LAN sends to the pairing server.
 * Called by [PairingInputFuzzTest] with Jazzer's inputs; a broken check or an
 * unexpected exception is a finding.
 */
internal object PairingInputChecks {
    /** As the app's Json (NetworkModule): lenient parsing widens what is reached. */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun httpRequest(bytes: ByteArray) {
        val read = try {
            readRequest(ByteArrayInputStream(bytes))
        } catch (_: EOFException) {
            return  // A body shorter than its Content-Length; the server drops it.
        }
        if (read !is RequestRead.Complete) return
        val request = read.request
        check('?' !in request.path) { "Query string kept in the path" }
        check(request.method == request.method.uppercase()) { "Method not normalised" }
        check(request.body.length <= MAX_BODY_BYTES) { "Body above the limit" }
        for (name in listOf("payload", "auth", "clientId")) formField(request.body, name)
    }

    fun formField(body: String, name: String) {
        val value = HttpRequest("POST", "/pair", body).formField(name) ?: return
        check(value.isNotBlank()) { "Blank form value returned" }
    }

    fun session(rawAuth: String, clientId: String) {
        val session = try {
            SessionParser.parse(json, rawAuth, clientId)
        } catch (_: IllegalArgumentException) {
            return  // Includes SerializationException: rejected, as intended.
        }
        check(session.accessToken.isNotBlank()) { "Blank access token accepted" }
        check(session.refreshToken.isNotBlank()) { "Blank refresh token accepted" }
        check(session.deviceId.isNotBlank()) { "Blank device id accepted" }
        check(session.expiresAt >= 0) { "Negative expiry" }
    }
}
