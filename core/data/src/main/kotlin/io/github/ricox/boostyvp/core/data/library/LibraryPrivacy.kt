package io.github.ricox.boostyvp.core.data.library

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.ricox.boostyvp.core.domain.auth.CurrentAccount
import io.github.ricox.boostyvp.core.data.repository.RoomPlaybackProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class LibraryRecording(val history: Boolean = true, val resume: Boolean = true, val watched: Boolean = true)
enum class LibraryData(val table: String) {
    History("watch_history"), Resume("playback_progress"), Later("watch_later"),
    Videos("favorite_posts"), Authors("favorite_authors"), Watched("watched_posts")
}
data class LibraryPrivacyState(val account: String? = null, val recording: LibraryRecording = LibraryRecording())

/** Account-specific preferences are deliberately excluded from transferable settings. */
@Singleton
class LibraryRecordingStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("library_privacy", Context.MODE_PRIVATE)
    fun load(account: String) = LibraryRecording(prefs.getBoolean("$account.history", true),
        prefs.getBoolean("$account.resume", true), prefs.getBoolean("$account.watched", true))
    fun save(account: String, value: LibraryRecording) {
        prefs.edit().putBoolean("$account.history", value.history).putBoolean("$account.resume", value.resume)
            .putBoolean("$account.watched", value.watched).apply()
    }
    fun observe(account: String): Flow<LibraryRecording> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(load(account)) }
        prefs.registerOnSharedPreferenceChangeListener(listener); trySend(load(account))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)
}

@Singleton
class LibraryPrivacyRepository @Inject constructor(private val db: LibraryDatabase,
    private val account: CurrentAccount, private val recording: LibraryRecordingStore,
    private val progress: RoomPlaybackProgressRepository) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: Flow<LibraryPrivacyState> = account.key.flatMapLatest { key ->
        if (key == null) flowOf(LibraryPrivacyState()) else recording.observe(key).map { LibraryPrivacyState(key, it) }
    }
    suspend fun save(expectedAccount: String, value: LibraryRecording) = withContext(Dispatchers.IO) {
        check(account.currentKey() == expectedAccount) { "Account changed" }; recording.save(expectedAccount, value)
    }
    suspend fun clear(expectedAccount: String, selected: Set<LibraryData>) = withContext(Dispatchers.IO) {
        require(selected.isNotEmpty())
        check(account.currentKey() == expectedAccount) { "Account changed" }
        if (LibraryData.Resume in selected) progress.prepareLibrary(expectedAccount)
        db.withTransaction {
            check(account.currentKey() == expectedAccount) { "Account changed" }
            selected.forEach { data ->
                // Only enum-owned table names; account identifiers are always bound parameters.
                db.openHelper.writableDatabase.execSQL("DELETE FROM ${data.table} WHERE account = ?", arrayOf(expectedAccount))
            }
        }
    }
}
