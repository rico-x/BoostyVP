package io.github.ricox.boostyvp.core.network

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import io.github.ricox.boostyvp.core.domain.auth.PhoneChallenge
import io.github.ricox.boostyvp.core.domain.auth.PhoneLogin
import io.github.ricox.boostyvp.core.domain.auth.PhoneLoginException
import io.github.ricox.boostyvp.core.model.Session
import io.github.ricox.boostyvp.core.network.di.BoostyRefresh
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import javax.inject.Inject

/** Matches Boosty's public web client (app.BJHesUs0.js, checked 2026-10-19). */
class BoostyPhoneLogin @Inject constructor(
    @BoostyRefresh private val api: BoostyApi,
) : PhoneLogin {
    override suspend fun requestCode(phone: String, deviceId: String): PhoneChallenge =
        phoneRequest {
            val result = api.sendPhoneCode(phone = phone, deviceId = deviceId).data.phoneCode
            if (result.code.isBlank()) throw PhoneLoginException(tr(UiText.boosty_phone_login_001))
            PhoneChallenge(result.code, result.sentTransport)
        }

    override suspend fun confirmCode(phone: String, challenge: String, smsCode: String, deviceId: String): Session =
        phoneRequest {
            val result = api.confirmPhoneCode(phone = phone, code = challenge, smsCode = smsCode, deviceId = deviceId)
            if (result.accessToken.isBlank() || result.refreshToken.isBlank() || result.expiresIn <= 0) {
                throw PhoneLoginException(tr(UiText.boosty_phone_login_002))
            }
            Session(result.accessToken, result.refreshToken,
                System.currentTimeMillis() / 1000 + result.expiresIn, deviceId)
        }

    private suspend fun <T> phoneRequest(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        val error = runCatching {
            val body = e.response()?.errorBody()?.string().orEmpty()
            (Json.parseToJsonElement(body) as? JsonObject)?.get("error")
                ?.let { (it as? JsonPrimitive)?.content }
        }.getOrNull()
        throw PhoneLoginException(when {
            error == "invalid_check" -> tr(UiText.boosty_phone_login_003)
            error == "denied_phone_prefix" -> tr(UiText.boosty_phone_login_004)
            error == "denied_phone_registration" -> tr(UiText.boosty_phone_login_005)
            error == "sms_gate_ratelimit" || e.code() == 429 -> tr(UiText.boosty_phone_login_006)
            e.code() == 400 || e.code() == 401 -> tr(UiText.boosty_phone_login_007)
            else -> tr(UiText.boosty_phone_login_008)
        })
    }
}
