package io.github.ricox.boostyvp.core.domain.auth

import io.github.ricox.boostyvp.core.model.Session

/** Phone and verification values stay in memory until a session is confirmed. */
interface PhoneLogin {
    suspend fun requestCode(phone: String, deviceId: String): PhoneChallenge
    suspend fun confirmCode(phone: String, challenge: String, smsCode: String, deviceId: String): Session
}

data class PhoneChallenge(val verificationId: String, val delivery: String? = null)

/** Only a safe, user-facing message; never include the request or response body. */
class PhoneLoginException(message: String) : Exception(message)
