package io.github.ricox.boostyvp.core.domain.auth

import io.github.ricox.boostyvp.core.model.Session

/**
 * Performs the network half of a token refresh.
 *
 * Declared in the pure JVM domain and implemented in :core:network.
 * Session policies have no Android, HTTP, or storage dependency.
 */
fun interface TokenRefresher {
    /**
     * @return the new session on success. Implementations must throw
     *   [AuthException.Unrecoverable] when Boosty rejects the refresh token
     *   outright, and a plain IOException for transient network failures --
     *   the two are handled very differently by [SessionManager].
     */
    suspend fun refresh(current: Session): Session
}

sealed class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The refresh token is dead. The user must pair again. */
    class Unrecoverable(message: String, cause: Throwable? = null) : AuthException(message, cause)

    /** No session at all. */
    class NotAuthenticated : AuthException("No stored session")
}
