package io.github.ricox.boostyvp.core.ui.appearance

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import android.content.Context

enum class TvFeedSource(val id: String, private val labelText: () -> String) {
    New("new", { tr(UiText.menu_settings_dialog_002) }), Continue("continue", { tr(UiText.tv_channel_preferences_001) }), Later("later", { tr(UiText.library_privacy_dialog_005) }),
    History("history", { tr(UiText.menu_settings_dialog_003) }), Favorites("favorites", { tr(UiText.library_privacy_dialog_007) });
    val label: String get() = labelText()

}
data class TvChannelOptions(val enabled: Boolean = true, val count: Int = 1,
    val sources: List<TvFeedSource> = TvFeedSource.entries.toList(), val cards: Int = 20, val watchNext: Boolean = false) {
    fun normalized() = copy(count = count.coerceIn(1, TvFeedSource.entries.size), cards = cards.coerceIn(5, 50),
        sources = (sources.distinct() + TvFeedSource.entries).distinct())
    fun changeSource(slot: Int): TvChannelOptions {
        val current = normalized().sources.toMutableList()
        val next = TvFeedSource.entries[(current[slot].ordinal + 1) % TvFeedSource.entries.size]
        val other = current.indexOf(next)
        current[other] = current[slot]; current[slot] = next
        return copy(sources = current)
    }
}
class TvChannelPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tv_channels", Context.MODE_PRIVATE)
    fun load() = TvChannelOptions(prefs.getBoolean("enabled", true), prefs.getInt("count", 1),
        prefs.getString("sources", null)?.split(',')?.mapNotNull { id -> TvFeedSource.entries.find { it.id == id } }
            ?: TvFeedSource.entries.toList(), prefs.getInt("cards", 20), prefs.getBoolean("watch_next", false)).normalized()
    fun save(options: TvChannelOptions) { val safe = options.normalized(); prefs.edit()
        .putBoolean("enabled", safe.enabled).putInt("count", safe.count).putInt("cards", safe.cards)
        .putString("sources", safe.sources.joinToString(",") { it.id }).putBoolean("watch_next", safe.watchNext).apply() }
}
