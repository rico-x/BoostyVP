package io.github.ricox.boostyvp.core.domain.auth

import kotlinx.coroutines.flow.Flow

/**
 * Which Boosty account is signed in, as a stable key for local per-account data.
 *
 * The session itself carries no user id and its tokens rotate, so the key comes
 * from Boosty's own user id.
 */
interface CurrentAccount {
    /** Null while signed out or until the account could be identified. */
    val key: Flow<String?>

    /** The key now, waiting briefly for a first identification after sign-in. */
    suspend fun currentKey(): String?
}
