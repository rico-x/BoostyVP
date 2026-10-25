package io.github.ricox.boostyvp.core.network

/** Shared web identity for API calls, media manifests and media segments. */
object BoostyWebHeaders {
    const val ORIGIN = "https://boosty.to"
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    val mediaRequestProperties: Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "Origin" to ORIGIN,
        "Referer" to "$ORIGIN/",
    )
}
