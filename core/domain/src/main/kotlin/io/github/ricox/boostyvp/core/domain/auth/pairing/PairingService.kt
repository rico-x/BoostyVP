package io.github.ricox.boostyvp.core.domain.auth.pairing

import io.github.ricox.boostyvp.core.model.Session
import kotlinx.coroutines.CoroutineScope

/**
 * What the QR encodes: a plain `http://` URL, so any phone camera can open it.
 *
 * An earlier version used a custom `boostyvp://` scheme, which no installed app
 * claimed -- scanning it did nothing at all.
 */
data class PairingOffer(
    val host: String,
    val port: Int,
    val encodedKey: String,
) {
    val url: String get() = "http://$host:$port$LINK_PATH"

    /** Shown under the QR for anyone typing it into a laptop instead. */
    val displayAddress: String get() = "$host:$port"

    companion object { const val LINK_PATH = "/link" }
}

sealed interface PairingStartResult {
    data class Ready(val offer: PairingOffer) : PairingStartResult
    data class NoNetwork(val detail: String) : PairingStartResult
    data class CouldNotBind(val detail: String) : PairingStartResult
}

interface PairingService {
    suspend fun start(onPaired: (Session) -> Unit): PairingStartResult
    fun stop()

    fun interface Factory {
        fun create(scope: CoroutineScope): PairingService
    }
}
