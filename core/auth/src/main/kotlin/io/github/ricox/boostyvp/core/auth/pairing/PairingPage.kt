package io.github.ricox.boostyvp.core.auth.pairing

/**
 * The page the TV serves to whatever device scanned the QR.
 *
 * Self-contained on purpose: no CDN, no external font, no framework. The TV
 * serves this over a LAN with no guaranteed internet, so anything fetched from
 * outside would simply fail to load.
 *
 * ## Browser handoff and clipboard fallback
 *
 * A saved bookmark runs inside Boosty's origin and reads the signed-in
 * session. Web Crypto seals it with the pairing key, then top-level navigation
 * returns it in a fragment to this page. The fragment is removed immediately;
 * an explicit button posts the sealed envelope to the existing /pair endpoint.
 * This avoids HTTPS-to-HTTP fetch and clipboard restrictions on mobile browsers.
 * The older copy-and-paste flow remains available as a fallback.
 */
internal object PairingPage {

    fun html(key: String): String = HTML
        .replace(SUBMIT_TOKEN, PAIR_PREFIX + "/" + key)
        .replace(BOOKMARKLET_TOKEN, BOOKMARKLET)
        .replace("%%BROWSER_BOOKMARKLET%%", BROWSER_BOOKMARKLET)
        .replace("%%PAGE_SCRIPT%%", PAGE_SCRIPT)

    /** Path prefix that carries the pairing key. */
    const val PAIR_PREFIX = "/p"

    /**
     * Placeholders swapped in at render time. Deliberately not Kotlin string
     * templates: the markup below is full of CSS and JavaScript, and letting
     * the compiler interpolate into it invites a syntax error every time the
     * page changes.
     */
    private const val SUBMIT_TOKEN = "%%SUBMIT%%"
    private const val BOOKMARKLET_TOKEN = "%%BOOKMARKLET%%"

    /**
     * Falls back to reading `auth`/`_clientId` from cookies when they are
     * absent from localStorage -- see [io.github.ricox.boostyvp.feature.pairing]'s
     * `READ_STORAGE_JS`, which needs the same fallback for the same reason:
     * Boosty has stored `auth` in both places at different times.
     */
    private const val BOOKMARKLET = "javascript:%28function%28%29%7Bvar%20a%3DlocalStorage.getItem%28%27auth%27%29%2Cc%3DlocalStorage.getItem%28%27_clientId%27%29%3Bif%28%21a%29%7Bvar%20m%3Ddocument.cookie.match%28%2F%28%3F%3A%5E%7C%3B%5Cs%2A%29auth%3D%28%5B%5E%3B%5D%2A%29%2F%29%3Bif%28m%29a%3DdecodeURIComponent%28m%5B1%5D%29%3B%7Dif%28%21c%29%7Bvar%20cc%3Ddocument.cookie.match%28%2F%28%3F%3A%5E%7C%3B%5Cs%2A%29_clientId%3D%28%5B%5E%3B%5D%2A%29%2F%29%3Bif%28cc%29c%3DdecodeURIComponent%28cc%5B1%5D%29%3B%7Dif%28%21a%7C%7C%21c%29%7Balert%28%27Sign%20in%20to%20Boosty%20first%2C%20then%20run%20this%20again.%27%29%3Breturn%7Dvar%20t%3DJSON.stringify%28%7Bauth%3Aa%2CclientId%3Ac%7D%29%3Bif%28navigator.clipboard%26%26navigator.clipboard.writeText%29%7Bnavigator.clipboard.writeText%28t%29.then%28function%28%29%7Balert%28%27Copied.%20Now%20paste%20it%20on%20the%20TV%20pairing%20page.%27%29%7D%2Cfunction%28%29%7Bprompt%28%27Copy%20this%2C%20then%20paste%20it%20on%20the%20TV%20pairing%20page%3A%27%2Ct%29%7D%29%7Delse%7Bprompt%28%27Copy%20this%2C%20then%20paste%20it%20on%20the%20TV%20pairing%20page%3A%27%2Ct%29%7D%7D%29%28%29"

    private val HTML = resource("page.html")
    private val PAGE_SCRIPT = resource("pairing-page.js")
    // Keep the resource compact: Android's single-line bookmark editor truncates
    // pasted URLs at 5,000 characters. PairingPageTest checks the encoded URL,
    // with margin; ChromeBookmarkTest saves and executes it in real Chrome.
    private val BROWSER_BOOKMARKLET = "javascript:" + java.net.URLEncoder
        .encode(resource("browser-handoff.js"), "UTF-8")
        .replace("+", "%20")

    private fun resource(name: String): String =
        checkNotNull(PairingPage::class.java.getResourceAsStream("/pairing/$name")) {
            "Missing pairing resource: $name"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
