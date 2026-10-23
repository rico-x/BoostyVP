package io.github.ricox.boostyvp.core.network.interceptor

import io.github.ricox.boostyvp.core.domain.auth.AuthException
import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the bearer token, refreshing proactively when it is already known to
 * be expired.
 *
 * Requests are still sent unauthenticated when no session exists -- several
 * Boosty endpoints work anonymously, and browsing a public blog before pairing
 * is a legitimate state.
 */
class AuthInterceptor(
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // The refresh call authenticates itself with the refresh token in the
        // body; adding a dead bearer token to it would be pointless and could
        // confuse the server.
        if (request.isRefreshCall()) return chain.proceed(request)

        val token = runBlocking {
            try {
                sessionManager.validAccessToken()
            } catch (e: AuthException) {
                null
            }
        }

        val authed = if (token != null) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }

        return chain.proceed(authed)
    }
}

internal fun okhttp3.Request.isRefreshCall(): Boolean =
    url.encodedPath.contains("oauth/token")
