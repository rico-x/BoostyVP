package io.github.ricox.boostyvp.core.network

import io.github.ricox.boostyvp.core.domain.auth.AuthException
import io.github.ricox.boostyvp.core.domain.auth.TokenRefresher
import io.github.ricox.boostyvp.core.model.Session
import io.github.ricox.boostyvp.core.network.di.BoostyRefresh
import retrofit2.HttpException
import javax.inject.Inject

/**
 * Network half of the refresh, wired into [io.github.ricox.boostyvp.core.domain.auth.SessionManager].
 *
 * The refresh API has its own HTTP client and dispatcher. This breaks the
 * injection cycle and allows refreshes while the authenticated client's
 * workers are blocked waiting for a token.
 */
class BoostyTokenRefresher @Inject constructor(
    @BoostyRefresh private val api: BoostyApi,
) : TokenRefresher {

    override suspend fun refresh(current: Session): Session {
        val response = try {
            api.refreshToken(
                deviceId = current.deviceId,
                refreshToken = current.refreshToken,
            )
        } catch (e: HttpException) {
            // 400 and 401 both mean "this refresh token is dead". Anything else
            // (5xx, 429) is transient and must not clear the session -- it
            // propagates as-is so the caller can retry.
            if (e.code() == 400 || e.code() == 401) {
                throw AuthException.Unrecoverable("Boosty rejected the refresh token", e)
            }
            throw e
        }

        if (response.accessToken.isBlank() || response.refreshToken.isBlank()) {
            throw AuthException.Unrecoverable("Refresh response was missing a token")
        }

        return Session(
            accessToken = response.accessToken,
            // Boosty rotates this. Keeping the old one would sign the user out
            // at the next refresh.
            refreshToken = response.refreshToken,
            expiresAt = (System.currentTimeMillis() / 1000) + response.expiresIn,
            deviceId = current.deviceId,
        )
    }
}
