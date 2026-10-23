package io.github.ricox.boostyvp.core.auth.pairing

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Payload protection for the LAN handoff.
 *
 * The transport is plain HTTP on purpose. The alternative -- self-signed TLS
 * plus certificate pinning -- means generating a certificate on the TV,
 * shipping its fingerprint through the QR, and persuading the phone's HTTP
 * client to trust exactly that one certificate. It is a lot of fragile
 * plumbing to protect a payload that can simply be encrypted directly.
 *
 * Instead the QR carries a 256-bit pre-shared key that never touches the
 * network, and the session is sealed with AES-GCM under that key. Only
 * something that scanned the TV's screen can produce a payload this TV will
 * accept, and nothing on the wire is readable. GCM authenticates as well as
 * encrypts, so a tampered payload fails to open rather than decoding to
 * garbage.
 */
object PairingCrypto {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    fun newKey(): ByteArray = ByteArray(KEY_BITS / 8).also(random::nextBytes)

    fun encodeKey(key: ByteArray): String =
        Base64.encodeToString(key, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun decodeKey(encoded: String): ByteArray =
        Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** @return base64 of `iv || ciphertext||tag`. */
    fun seal(key: ByteArray, plaintext: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        }
        val body = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + body, Base64.NO_WRAP)
    }

    /**
     * @throws IllegalArgumentException when the payload is malformed, and
     *   [javax.crypto.AEADBadTagException] when it was not sealed with [key].
     */
    fun open(key: ByteArray, payload: String): String {
        val bytes = Base64.decode(payload.trim(), Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "Pairing payload is too short" }

        val iv = bytes.copyOfRange(0, IV_BYTES)
        val body = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        }
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    /**
     * Constant-time comparison. A plain `==` on a secret leaks its prefix
     * through timing; on a LAN an attacker can make enough attempts for that to
     * matter.
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)
}
