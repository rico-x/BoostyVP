package io.github.ricox.boostyvp.core.data.account

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.ricox.boostyvp.core.domain.auth.CurrentAccount
import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import io.github.ricox.boostyvp.core.model.AuthState
import io.github.ricox.boostyvp.core.network.BoostyApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers the identified account across launches; forgotten on sign-out. */
interface AccountIdCache {
    fun read(): Long?
    fun write(id: Long)
    fun clear()
}

class PreferencesAccountIdCache @Inject constructor(
    @ApplicationContext context: Context,
) : AccountIdCache {
    private val prefs by lazy { context.getSharedPreferences("boostyvp_account", Context.MODE_PRIVATE) }
    override fun read(): Long? = prefs.getLong(KEY_ID, 0L).takeIf { it > 0 }
    override fun write(id: Long) { prefs.edit().putLong(KEY_ID, id).apply() }
    override fun clear() { prefs.edit().remove(KEY_ID).apply() }

    private companion object { const val KEY_ID = "user_id" }
}

/**
 * Keys local data by Boosty's user id, asked once per sign-in and then cached.
 * Offline at sign-in, the request is retried with backoff while signed in;
 * until then the key is null and per-account data is neither read nor written.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class BoostyCurrentAccount internal constructor(
    private val api: BoostyApi,
    sessions: SessionManager,
    private val cache: AccountIdCache,
    scope: CoroutineScope,
    private val retryDelayMs: (attempt: Int) -> Long,
) : CurrentAccount {
    @Inject constructor(api: BoostyApi, sessions: SessionManager, cache: AccountIdCache) : this(
        api, sessions, cache, CoroutineScope(SupervisorJob() + Dispatchers.IO),
        { attempt -> minOf(MAX_RETRY_MS, 2_000L shl minOf(attempt, 5)) },
    )

    override val key: StateFlow<String?> = sessions.authState
        .map { it is AuthState.SignedIn }
        .distinctUntilChanged()
        .transformLatest { signedIn ->
            if (!signedIn) {
                cache.clear()
                emit(null)
                return@transformLatest
            }
            val cached = cache.read()
            if (cached != null) {
                emit(keyOf(cached))
            } else {
                emit(null)
                emit(keyOf(identify()))
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun currentKey(): String? =
        key.value ?: withTimeoutOrNull(FIRST_KEY_WAIT_MS) { key.filterNotNull().first() }

    private suspend fun identify(): Long {
        var attempt = 0
        while (true) {
            try {
                val id = api.getCurrentUser().id
                if (id > 0) {
                    cache.write(id)
                    return id
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Offline or a transient failure: keep trying while signed in.
            }
            delay(retryDelayMs(attempt++))
        }
    }

    private fun keyOf(id: Long) = "boosty:$id"

    private companion object {
        const val FIRST_KEY_WAIT_MS = 3_000L
        const val MAX_RETRY_MS = 60_000L
    }
}
