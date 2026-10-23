package io.github.ricox.boostyvp.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.ricox.boostyvp.core.domain.auth.TokenStore
import io.github.ricox.boostyvp.core.model.Session
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Session storage backed by EncryptedSharedPreferences.
 *
 * Two concessions to the reality of TV hardware:
 *
 *  - the master key does NOT require user authentication. Plenty of TV boxes
 *    have no secure lock screen at all, and requiring auth would make the app
 *    unusable on them.
 *  - if the keystore is unavailable or corrupt (cheap boxes with broken
 *    Keymaster implementations are common), this falls back to plain
 *    SharedPreferences rather than crashing on launch. A stolen token is bad;
 *    an app that cannot start is worse, and the fallback is logged loudly.
 */
class EncryptedTokenStore(
    context: Context,
    private val io: CoroutineDispatcher,
) : TokenStore {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy { openPreferences() }

    private fun openPreferences(): SharedPreferences = try {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            ENCRYPTED_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // GeneralSecurityException, IOException, KeyStoreException, and on some
        // devices an outright RuntimeException from a broken Keymaster HAL.
        Log.e(
            TAG,
            "Keystore unavailable; falling back to UNENCRYPTED token storage. " +
                "The session token is readable by root on this device.",
            e,
        )
        appContext.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)
    }

    override val session: Flow<Session?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged().flowOn(io)

    override suspend fun peek(): Session? = withContext(io) { read() }

    override suspend fun save(session: Session) = withContext(io) {
        // commit(), not apply(): this must be on disk before the caller
        // proceeds to use the new (rotated) refresh token.
        val ok = prefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_EXPIRES, session.expiresAt)
            .putString(KEY_DEVICE, session.deviceId)
            .commit()
        check(ok) { "Failed to persist session" }
    }

    override suspend fun clear() = withContext(io) {
        // EncryptedSharedPreferences.clear() removes values without adding
        // them to its changed-key list, so existing Flow listeners never see
        // sign-out. Explicit removals persist the deletion AND notify the UI.
        check(prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EXPIRES)
            .remove(KEY_DEVICE)
            .commit()) { "Failed to clear session" }
        Unit
    }

    private fun read(): Session? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val device = prefs.getString(KEY_DEVICE, null) ?: return null
        val expires = prefs.getLong(KEY_EXPIRES, 0L)
        val session = Session(access, refresh, expires, device)
        return session.takeUnless { it.isBlank }
    }

    private companion object {
        const val TAG = "TokenStore"
        const val ENCRYPTED_FILE = "boostyvp_session"
        const val PLAIN_FILE = "boostyvp_session_plain"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
        const val KEY_DEVICE = "device_id"
    }
}
