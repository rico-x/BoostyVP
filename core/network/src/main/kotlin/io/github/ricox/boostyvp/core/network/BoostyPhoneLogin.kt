package io.github.ricox.boostyvp.core.network

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
            if (result.code.isBlank()) throw PhoneLoginException("Boosty не вернул подтверждение отправки кода. Попробуйте ещё раз.")
            PhoneChallenge(result.code, result.sentTransport)
        }

    override suspend fun confirmCode(phone: String, challenge: String, smsCode: String, deviceId: String): Session =
        phoneRequest {
            val result = api.confirmPhoneCode(phone = phone, code = challenge, smsCode = smsCode, deviceId = deviceId)
            if (result.accessToken.isBlank() || result.refreshToken.isBlank() || result.expiresIn <= 0) {
                throw PhoneLoginException("Boosty вернул неполную сессию. Запросите новый код.")
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
            error == "invalid_check" -> "Boosty требует CAPTCHA. Для этого входа используйте QR и браузер."
            error == "denied_phone_prefix" -> "Boosty не поддерживает вход с этим кодом страны."
            error == "denied_phone_registration" -> "Привяжите этот номер к вашему аккаунту на сайте Boosty."
            error == "sms_gate_ratelimit" || e.code() == 429 -> "Слишком много запросов. Подождите несколько минут."
            e.code() == 400 || e.code() == 401 -> "Номер или код не принят. Проверьте данные и попробуйте снова."
            else -> "Сервис входа Boosty временно недоступен. Попробуйте позже."
        })
    }
}
