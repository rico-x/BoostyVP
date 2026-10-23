package io.github.ricox.boostyvp.core.ui.appearance

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Device presentation only. Separate from account/library data and player options. */
class CardSizePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("interface_preferences", Context.MODE_PRIVATE)

    fun load(): CardSize = CardSize.fromId(prefs.getString(KEY, null))

    fun save(size: CardSize) { prefs.edit().putString(KEY, size.id).apply() }

    internal fun observe(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY || key == null) onChange()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object { const val KEY = "card_size" }
}

val LocalCardSize = staticCompositionLocalOf { CardSize.Default }

/** One setting for rows, all grids and preview; changes propagate without restarting the app. */
@Composable
internal fun ProvideCardSize(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { CardSizePreferences(context) }
    var size by remember(preferences) { mutableStateOf(preferences.load()) }
    DisposableEffect(preferences) {
        val stop = preferences.observe { size = preferences.load() }
        size = preferences.load()
        onDispose { stop() }
    }
    CompositionLocalProvider(LocalCardSize provides size, content = content)
}
