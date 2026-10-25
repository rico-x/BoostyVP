package io.github.ricox.boostyvp.core.auth.pairing

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import android.util.Log
import io.github.ricox.boostyvp.core.domain.auth.pairing.PairingOffer
import io.github.ricox.boostyvp.core.domain.auth.pairing.PairingService
import io.github.ricox.boostyvp.core.domain.auth.pairing.PairingStartResult
import io.github.ricox.boostyvp.core.auth.SessionParser
import io.github.ricox.boostyvp.core.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An endpoint that accepts one Boosty session from another device on
 * the same network.
 *
 *  - `GET  /link` serves the browser page at a stable, typeable address
 *  - `GET  /p/{key}` remains compatible with previously saved callbacks
 *  - `POST /p/{key}`  that page (or its bookmarklet) submitting the session
 *  - `POST /pair` accepts the browser's AES-GCM sealed payload
 *
 * No time limit. Starts when the QR appears; stops after success or when the
 * screen is left. A new attempt gets a fresh internal encryption key.
 */
class PairingServer(
    private val json: Json,
    private val scope: CoroutineScope,
    private val lanAddress: LanAddress,
    /** Explicitly advertised host when a debug emulator is reached through a LAN relay. */
    private val advertisedHost: String? = null,
    private val listenPort: Int = 0,
) : PairingService {
    init {
        require(listenPort in 0..65535) { "Invalid pairing port" }
    }

    private var server: MiniHttpServer? = null
    private var shutdownJob: Job? = null
    private var claimed: AtomicBoolean? = null

    override suspend fun start(
        onPaired: (Session) -> Unit,
    ): PairingStartResult = try {
        withContext(Dispatchers.IO) {
            stop()

            val host = advertisedHost ?: when (val found = lanAddress.find()) {
                is LanAddress.Result.Found -> found.ip
                is LanAddress.Result.NotFound -> return@withContext PairingStartResult.NoNetwork(found.detail)
            }

            val secret = PairingCrypto.newKey()
            val encodedKey = PairingCrypto.encodeKey(secret)
            val offerClaimed = AtomicBoolean(false)
            claimed = offerClaimed

            // Catching Throwable, not Exception, is deliberate. A missing class
            // raises NoClassDefFoundError, which is an Error; the previous
            // `catch (e: Exception)` let it escape the coroutine and the pairing
            // screen simply span forever with nothing reported.
            val started = MiniHttpServer(listenPort) { request ->
                route(request, encodedKey, secret, offerClaimed, onPaired)
            }
            val port = try {
                started.start()
            } catch (t: Throwable) {
                started.stop()
                Log.e(TAG, "Could not start the pairing server on $host:$listenPort", t)
                return@withContext PairingStartResult.CouldNotBind(describe(t, "binding $host:$listenPort"))
            }

            server = started

            PairingStartResult.Ready(PairingOffer(host = host, port = port, encodedKey = encodedKey))
        }
    } catch (e: CancellationException) {
        stop()
        throw e
    }

    override fun stop() {
        claimed?.set(true)
        claimed = null
        shutdownJob?.cancel()
        shutdownJob = null
        runCatching { server?.stop() }
            .onFailure { Log.w(TAG, "Pairing server did not stop cleanly", it) }
        server = null
    }

    private fun route(
        request: HttpRequest,
        encodedKey: String,
        secret: ByteArray,
        offerClaimed: AtomicBoolean,
        onPaired: (Session) -> Unit,
    ): HttpResponse {
        val pagePath = "${PairingPage.PAIR_PREFIX}/$encodedKey"

        return when {
            request.method == "GET" && request.path.trimEnd('/') == PairingOffer.LINK_PATH ->
                HttpResponse.html(PairingPage.html(encodedKey))

            request.method == "GET" && keyMatches(request.path, pagePath) ->
                HttpResponse.html(PairingPage.html(encodedKey))

            request.method == "POST" && keyMatches(request.path, pagePath) -> {
                // The bookmarklet copies one combined blob; someone reading
                // DevTools by hand pastes the two values separately.
                val session = request.formField("payload")?.let { fromCombinedPayload(it) }
                    ?: buildSession(request.formField("auth"), request.formField("clientId"))

                if (session == null || !offerClaimed.compareAndSet(false, true)) {
                    HttpResponse.badRequest()
                } else {
                    accept(session, onPaired)
                    HttpResponse.html(DONE_HTML)
                }
            }

            request.method == "POST" && request.path == SEALED_PATH -> {
                val session = openSealed(secret, request.body)
                if (session == null || !offerClaimed.compareAndSet(false, true)) {
                    // Deliberately vague: a specific message would tell someone
                    // on the LAN whether they had the right key.
                    HttpResponse.badRequest()
                } else {
                    accept(session, onPaired)
                    HttpResponse.ok()
                }
            }

            else -> HttpResponse.notFound()
        }
    }

    private fun accept(session: Session, onPaired: (Session) -> Unit) {
        onPaired(session)
        shutdownJob = scope.launch {
            // Let the response actually flush before tearing the socket down,
            // or the browser reports a failure for a pairing that succeeded.
            delay(SHUTDOWN_GRACE_MILLIS)
            stop()
        }
    }

    private fun keyMatches(suppliedPath: String, expectedPath: String): Boolean =
        PairingCrypto.constantTimeEquals(
            suppliedPath.trimEnd('/').toByteArray(Charsets.UTF_8),
            expectedPath.trimEnd('/').toByteArray(Charsets.UTF_8),
        )

    private fun fromCombinedPayload(raw: String): Session? = runCatching {
        val obj = json.parseToJsonElement(raw.trim()) as? JsonObject ?: return null

        fun field(vararg names: String) = names.firstNotNullOfOrNull { name ->
            (obj[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        }

        buildSession(field("auth"), field("clientId", "_clientId"))
    }.getOrElse {
        Log.d(TAG, "Combined payload was not readable: ${it.message}")
        null
    }

    private fun buildSession(auth: String?, clientId: String?): Session? {
        if (auth.isNullOrBlank() || clientId.isNullOrBlank()) return null
        return runCatching { SessionParser.parse(json, auth, clientId) }
            .getOrElse {
                Log.d(TAG, "Rejected a pairing submission: ${it.message}")
                null
            }
    }

    private fun openSealed(secret: ByteArray, body: String): Session? = runCatching {
        val plaintext = PairingCrypto.open(secret, body)
        val obj = json.parseToJsonElement(plaintext) as? JsonObject ?: return null

        fun field(vararg names: String) = names.firstNotNullOfOrNull { name ->
            (obj[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        }

        buildSession(field("auth"), field("clientId", "_clientId"))
    }.getOrElse {
        Log.d(TAG, "Rejected a sealed pairing attempt")
        null
    }

    private fun describe(t: Throwable, whileDoing: String) =
        "${t::class.java.simpleName}: ${t.message ?: "no detail"} (while $whileDoing)"

    companion object {
        const val SEALED_PATH = "/pair"

        private const val SHUTDOWN_GRACE_MILLIS = 750L
        private const val TAG = "PairingServer"

        private val DONE_HTML = tr(UiText.pairing_server_001).trimIndent()
    }
}
