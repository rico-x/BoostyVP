package io.github.ricox.boostyvp.core.network.interceptor

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
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
            .header("Origin", WEB_ORIGIN)
            .header("Referer", "$WEB_ORIGIN/")
            .header("X-App", "web")
            .build()
        return chain.proceed(request)
    }

    companion object {
        const val WEB_ORIGIN = "https://boosty.to"

        /**
         * Kept in one place so it can be updated as Chrome moves on. A stale UA
         * is a plausible cause of sudden 403s.
         */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
