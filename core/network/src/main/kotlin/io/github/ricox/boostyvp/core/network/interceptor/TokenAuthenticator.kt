package io.github.ricox.boostyvp.core.network.interceptor

import android.util.Log
import io.github.ricox.boostyvp.core.domain.auth.AuthException
import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Reacts to a 401 by refreshing once and replaying the request.
 *
 * The serialisation that makes this safe lives in [SessionManager]: a burst of
 * concurrent 401s -- which is exactly what a TV home screen produces -- all call
 * [SessionManager.refreshIfNeeded] with the token they each found rejected, and
 * only the first one actually performs a refresh. Because Boosty rotates the
 * refresh token, doing this any other way logs the user out.
 */
class TokenAuthenticator(
    private val sessionManager: SessionManager,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.isRefreshCall()) return null

        // Give up rather than loop. OkHttp counts prior responses in the chain.
        if (response.priorResponseCount() >= MAX_RETRIES) {
            Log.w(TAG, "Giving up after $MAX_RETRIES authentication attempts")
            return null
        }

        val rejected = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?: return null

        val refreshed = runBlocking {
            try {
                sessionManager.refreshIfNeeded(staleToken = rejected)
            } catch (e: AuthException) {
                Log.w(TAG, "Could not refresh session", e)
                null
            } catch (e: Exception) {
                // Transient network failure. Returning null surfaces the 401 to
                // the caller, which will retry the whole request later; the
                // session is left intact on purpose.
                Log.w(TAG, "Refresh failed transiently", e)
                null
            }
        } ?: return null

        if (refreshed.accessToken == rejected) return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${refreshed.accessToken}")
            .build()
    }

    private fun Response.priorResponseCount(): Int {
        var count = 0
        var prior = priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val TAG = "TokenAuthenticator"
        const val MAX_RETRIES = 2
    }
}
