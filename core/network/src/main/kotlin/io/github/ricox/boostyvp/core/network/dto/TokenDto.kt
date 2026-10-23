package io.github.ricox.boostyvp.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of the refresh call.
 *
 * Path and fields verified against Boosty's public web client, 2026-10-19.
 */
@Serializable
data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    /** Seconds until the access token expires, relative to now. */
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("token_type") val tokenType: String? = null,
)
