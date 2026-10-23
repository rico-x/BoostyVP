package io.github.ricox.boostyvp.core.model

/**
 * A Boosty web session, as it exists in the browser after login.
 *
 * All four fields are required. [deviceId] is the value boosty.to stores as
 * `_clientId`; the refresh call is rejected without it.
 */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** Unix epoch seconds. */
    val expiresAt: Long,
    val deviceId: String,
) {
    /**
     * Treat the token as expired slightly early so an in-flight request does not
     * race the actual expiry.
     */
    fun isExpired(nowEpochSeconds: Long, skewSeconds: Long = 60): Boolean =
        nowEpochSeconds >= (expiresAt - skewSeconds)

    val isBlank: Boolean
        get() = accessToken.isBlank() || refreshToken.isBlank() || deviceId.isBlank()
}

sealed interface AuthState {
    /** Token store has not been read yet. */
    data object Unknown : AuthState

    /** No session stored, or refresh failed irrecoverably. */
    data object SignedOut : AuthState

    data class SignedIn(val session: Session) : AuthState
}
