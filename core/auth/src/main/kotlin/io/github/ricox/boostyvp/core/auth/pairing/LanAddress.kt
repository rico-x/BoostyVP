package io.github.ricox.boostyvp.core.auth.pairing

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket

/**
 * Finds the IPv4 address another device on this network can reach.
 *
 * Returns a [Result] rather than a nullable String on purpose. The previous
 * version collapsed every possible failure into `null`, so the screen could
 * only ever say "not on a network" -- true or not -- and there was nothing to
 * debug from. Now the reason travels with the failure.
 */
class LanAddress(private val context: Context) {

    sealed interface Result {
        data class Found(val ip: String, val source: String) : Result

        /** @param detail what was actually observed, shown on screen. */
        data class NotFound(val detail: String) : Result
    }

    fun find(): Result {
        // ConnectivityManager first: it reports the address of the network
        // Android is actually routing through, which is the one a phone on the
        // same Wi-Fi will reach. Interface enumeration cannot tell an active
        // network from a stale one.
        fromActiveNetwork()?.let {
            Log.d(TAG, "Pairing will advertise $it (active network)")
            return Result.Found(it, "active network")
        }

        val observed = mutableListOf<String>()
        val fromInterfaces = fromInterfaces(observed)
        if (fromInterfaces != null) {
            Log.d(TAG, "Pairing will advertise $fromInterfaces (interface scan)")
            return Result.Found(fromInterfaces, "interface scan")
        }

        val detail = if (observed.isEmpty()) {
            "No network interfaces reported any address."
        } else {
            "Found only: ${observed.joinToString(", ")}"
        }
        Log.w(TAG, "No usable IPv4 address. $detail")
        return Result.NotFound(detail)
    }

    private fun fromActiveNetwork(): String? = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching null
        val network = manager.activeNetwork ?: return@runCatching null
        val properties = manager.getLinkProperties(network) ?: return@runCatching null

        properties.linkAddresses
            .map { it.address }
            .firstOrNull { it.isUsableIpv4() }
            ?.hostAddress
    }.getOrElse {
        Log.d(TAG, "ConnectivityManager lookup failed; falling back to interface scan", it)
        null
    }

    private fun fromInterfaces(observed: MutableList<String>): String? = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()
            ?: return@runCatching null // Documented to return null on some devices.

        val usable = mutableListOf<Pair<String, InetAddress>>()

        for (iface in interfaces.toList()) {
            if (!iface.isUp || iface.isLoopback) continue
            for (address in iface.inetAddresses.toList()) {
                observed += "${iface.name}=${address.hostAddress}"
                // Checked with a plain predicate. filterIsInstance<Pair<_, Inet4Address>>()
                // was the original bug here: generic arguments are erased, so it
                // matched every pair and the later cast blew up on IPv6.
                if (address.isUsableIpv4()) usable += iface.name to address
            }
        }

        // Prefer a private-range address, but accept anything routable --
        // a TV behind carrier-grade NAT (100.64.x) is still reachable from a
        // phone on the same network.
        val chosen = usable.firstOrNull { (_, address) -> address.isSiteLocalAddress }
            ?: usable.firstOrNull()

        chosen?.second?.hostAddress
    }.getOrElse {
        Log.w(TAG, "Could not enumerate network interfaces", it)
        null
    }

    private fun InetAddress.isUsableIpv4(): Boolean =
        this is Inet4Address && !isLoopbackAddress && !isLinkLocalAddress && !isAnyLocalAddress

    companion object {
        private const val TAG = "LanAddress"

        /**
         * A free TCP port. Bind-then-release, because the QR must be rendered
         * before anyone can scan it, so the port has to be known up front.
         */
        fun freePort(): Int = ServerSocket(0).use { it.localPort }
    }
}
