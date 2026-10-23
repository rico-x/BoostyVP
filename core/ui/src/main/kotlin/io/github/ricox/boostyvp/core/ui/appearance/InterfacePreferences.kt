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

class InterfacePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("interface_preferences", Context.MODE_PRIVATE)

    fun load() = InterfaceOptions(
        autoCollapseSidebar = prefs.getBoolean("sidebar_auto_collapse", true),
        textSize = CardTextSize.fromId(prefs.getString("card_text_size", null)),
        titleLines = prefs.getInt("card_title_lines", 2),
        showAuthor = prefs.getBoolean("card_show_author", true),
        showDate = prefs.getBoolean("card_show_date", true),
        showDuration = prefs.getBoolean("card_show_duration", true),
        hideRepeatedAuthor = prefs.getBoolean("card_hide_repeated_author", false),
        density = CatalogDensity.fromId(prefs.getString("catalog_density", null)),
    ).normalized()

    fun save(options: InterfaceOptions) {
        val safe = options.normalized()
        // Never clear this file: card_size belongs to the existing size selector.
        prefs.edit().putBoolean("sidebar_auto_collapse", safe.autoCollapseSidebar)
            .putString("card_text_size", safe.textSize.id)
            .putInt("card_title_lines", safe.titleLines)
            .putBoolean("card_show_author", safe.showAuthor)
            .putBoolean("card_show_date", safe.showDate)
            .putBoolean("card_show_duration", safe.showDuration)
            .putBoolean("card_hide_repeated_author", safe.hideRepeatedAuthor)
            .putString("catalog_density", safe.density.id).apply()
    }

    internal fun observe(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != "card_size") onChange()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

val LocalInterfaceOptions = staticCompositionLocalOf { InterfaceOptions() }

@Composable
internal fun ProvideInterfaceOptions(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { InterfacePreferences(context) }
    var options by remember(preferences) { mutableStateOf(preferences.load()) }
    DisposableEffect(preferences) {
        val stop = preferences.observe { options = preferences.load() }
        options = preferences.load()
        onDispose { stop() }
    }
    CompositionLocalProvider(LocalInterfaceOptions provides options, content = content)
}
