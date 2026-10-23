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
        metadataSize = CardMetadataSize.fromId(prefs.getString("card_metadata_size", null)),
        titleLines = prefs.getInt("card_title_lines", 2),
        showAuthor = prefs.getBoolean("card_show_author", true),
        showDate = prefs.getBoolean("card_show_date", true),
        showDuration = prefs.getBoolean("card_show_duration", true),
        hideRepeatedAuthor = prefs.getBoolean("card_hide_repeated_author", false),
        density = CatalogDensity.fromId(prefs.getString("catalog_density", null)),
        cardStyle = CardStyle.fromId(prefs.getString("card_style", null)),
        focusColor = FocusColor.fromId(prefs.getString("focus_color", null)),
        focusWidth = prefs.getInt("focus_width", 3),
        focusZoom = prefs.getBoolean("focus_zoom", true),
        animateFocus = prefs.getBoolean("focus_animation", true),
        fullTitle = prefs.getBoolean("focused_full_title", false),
        authorHeader = AuthorHeaderSize.fromId(prefs.getString("author_header", null)),
        theme = DarkTheme.fromId(prefs.getString("dark_theme", null)),
        accent = AccentColor.fromId(prefs.getString("accent_color", null)),
        playerLayout = PlayerPanelLayout.fromId(prefs.getString("player_panel_layout", null)),
        playerText = PlayerTextSize.fromId(prefs.getString("player_time_text", null)),
        playerThickSeekBar = prefs.getBoolean("player_thick_seek_bar", false),
        playerClock = prefs.getBoolean("player_clock", false),
        playerEndTime = prefs.getBoolean("player_end_time", false),
    ).normalized()

    fun save(options: InterfaceOptions) {
        val safe = options.normalized()
        // Never clear this file: card_size belongs to the existing size selector.
        prefs.edit().putBoolean("sidebar_auto_collapse", safe.autoCollapseSidebar)
            .putString("card_text_size", safe.textSize.id)
            .putString("card_metadata_size", safe.metadataSize.id)
            .putInt("card_title_lines", safe.titleLines)
            .putBoolean("card_show_author", safe.showAuthor)
            .putBoolean("card_show_date", safe.showDate)
            .putBoolean("card_show_duration", safe.showDuration)
            .putBoolean("card_hide_repeated_author", safe.hideRepeatedAuthor)
            .putString("catalog_density", safe.density.id)
            .putString("card_style", safe.cardStyle.id)
            .putString("focus_color", safe.focusColor.id)
            .putInt("focus_width", safe.focusWidth)
            .putBoolean("focus_zoom", safe.focusZoom)
            .putBoolean("focus_animation", safe.animateFocus)
            .putBoolean("focused_full_title", safe.fullTitle)
            .putString("author_header", safe.authorHeader.id)
            .putString("dark_theme", safe.theme.id)
            .putString("accent_color", safe.accent.id)
            .putString("player_panel_layout", safe.playerLayout.id)
            .putString("player_time_text", safe.playerText.id)
            .putBoolean("player_thick_seek_bar", safe.playerThickSeekBar)
            .putBoolean("player_clock", safe.playerClock)
            .putBoolean("player_end_time", safe.playerEndTime).apply()
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
