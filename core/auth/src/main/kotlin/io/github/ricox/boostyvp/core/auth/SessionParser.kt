package io.github.ricox.boostyvp.core.auth

import io.github.ricox.boostyvp.core.model.Session
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder

/**
 * Turns boosty.to's stored `auth` value plus its `_clientId` into a [Session].
 *
 * Shared by every sign-in route -- manual paste, the on-device WebView, and the
 * QR handoff -- so all three accept exactly the same inputs and fail with the
 * same messages. Previously this logic lived only in the manual-entry
 * ViewModel.
 *
 * The `auth` value is accepted in whichever form the caller managed to obtain:
 * URL-encoded (how it appears as a cookie) or plain JSON (how it appears in
 * localStorage).
 */
object SessionParser {

    fun parse(json: Json, rawAuth: String, clientId: String): Session {
        val auth = rawAuth.trim()
        val device = clientId.trim().trim('"')

        require(auth.isNotBlank()) { "The auth value is empty" }
        require(device.isNotBlank()) { "The _clientId value is empty" }

        val obj = decodeObject(json, auth)

        val access = obj.string("accessToken", "access_token")
            ?: throw IllegalArgumentException("No accessToken found in the auth value")
        val refresh = obj.string("refreshToken", "refresh_token")
            ?: throw IllegalArgumentException("No refreshToken found in the auth value")

        // Boosty has used both an absolute timestamp and a relative lifetime
        // here depending on where the value came from. A value small enough to
        // be a duration is treated as one; anything else is already absolute.
        val expiresRaw = obj.string("expiresAt", "expires_at", "expiresIn", "expires_in")
            ?.toLongOrNull()
            ?: 0L
        val nowSeconds = System.currentTimeMillis() / 1000
        val expiresAt = when {
            expiresRaw <= 0L -> 0L
            expiresRaw < RELATIVE_THRESHOLD_SECONDS -> nowSeconds + expiresRaw
            else -> expiresRaw
        }

        return Session(
            accessToken = access,
            refreshToken = refresh,
            expiresAt = expiresAt,
            deviceId = device,
        )
    }

    private fun decodeObject(json: Json, auth: String): JsonObject {
        val candidates = buildList {
            add(auth)
            if (!auth.trimStart().startsWith("{")) {
                runCatching { URLDecoder.decode(auth, "UTF-8") }.getOrNull()?.let(::add)
            }
        }

        for (candidate in candidates) {
            val parsed = runCatching { json.parseToJsonElement(candidate) as? JsonObject }.getOrNull()
            if (parsed != null) return parsed
        }
        throw IllegalArgumentException("That is not a readable auth value")
    }

    private fun JsonObject.string(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        runCatching { this[name]?.jsonPrimitive?.content }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != "null" }
    }

    /**
     * Ten years in seconds. Anything below this cannot plausibly be an absolute
     * epoch timestamp, so it is a duration.
     */
    private const val RELATIVE_THRESHOLD_SECONDS = 315_360_000L
}
