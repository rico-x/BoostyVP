package io.github.ricox.boostyvp.core.domain.auth

import io.github.ricox.boostyvp.core.model.AuthState
import io.github.ricox.boostyvp.core.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the session and is the only thing allowed to refresh it.
 *
 * ## Why the mutex matters
 *
 * Boosty rotates the refresh token: a successful refresh invalidates the token
 * that was used to make it. A TV home screen fires a dozen requests at once, so
 * when the access token expires a dozen 401s arrive together. Without
 * serialisation, each one refreshes with the same now-stale refresh token; the
 * first succeeds and the rest get rejected, and the app signs the user out
 * moments after signing them in.
 *
 * [validAccessToken] and [refreshIfNeeded] therefore funnel every refresh
 * through one mutex, and any caller that arrives while a refresh is in flight
 * re-reads the store afterwards instead of issuing its own.
 */
class SessionManager(
    private val store: TokenStore,
    private val refresher: TokenRefresher,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val refreshMutex = Mutex()

    val authState: Flow<AuthState> = store.session.map { session ->
        if (session == null) AuthState.SignedOut else AuthState.SignedIn(session)
    }

    suspend fun currentSession(): Session? = store.peek()

    /** Replaces the stored session -- used by pairing and by manual token entry. */
    suspend fun signIn(session: Session) = refreshMutex.withLock {
        require(!session.isBlank) { "Session is missing one or more required fields" }
        store.save(session)
    }

    suspend fun signOut() = refreshMutex.withLock { store.clear() }

    /**
     * An access token that is valid right now, refreshing first if it is at or
     * near expiry.
     *
     * @throws AuthException.NotAuthenticated when nothing is stored.
     */
    suspend fun validAccessToken(): String {
        val current = store.peek() ?: throw AuthException.NotAuthenticated()
        if (!current.isExpired(now())) return current.accessToken
        return refreshIfNeeded(staleToken = current.accessToken).accessToken
    }

    /**
     * Refresh, unless another caller already did.
     *
     * @param staleToken the access token the caller found to be rejected. If the
     *   stored token differs by the time the mutex is acquired, someone else has
     *   already refreshed and that result is returned instead.
     */
    suspend fun refreshIfNeeded(staleToken: String): Session = refreshMutex.withLock {
        val stored = store.peek() ?: throw AuthException.NotAuthenticated()

        if (stored.accessToken != staleToken) {
            // Refreshed by whoever held the lock before us.
            return@withLock stored
        }

        val refreshed = try {
            refresher.refresh(stored)
        } catch (e: AuthException.Unrecoverable) {
            store.clear()
            throw e
        }

        require(!refreshed.isBlank) { "Refresh returned an incomplete session" }

        // Persist before returning. If this throws, the caller must NOT proceed
        // with the new access token -- the rotated refresh token would be lost
        // and the session unrecoverable on next launch.
        store.save(refreshed)
        refreshed
    }

}
