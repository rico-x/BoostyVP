package io.github.ricox.boostyvp.core.network.interceptor

import io.github.ricox.boostyvp.core.network.BoostyWebHeaders
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Boosty's API is served for its own web client and is sensitive to a request
 * that does not look like one. These headers must match on the media requests
 * too, or manifests fetch fine and segments come back 403 -- see
 * MediaHttpFactory in :feature:player.
 */
class BoostyHeadersInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", BoostyWebHeaders.USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
            .header("Origin", BoostyWebHeaders.ORIGIN)
            .header("Referer", "${BoostyWebHeaders.ORIGIN}/")
            .header("X-App", "web")
            .build()
        return chain.proceed(request)
    }

}
