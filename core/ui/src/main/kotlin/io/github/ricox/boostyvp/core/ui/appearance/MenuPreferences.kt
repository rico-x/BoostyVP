package io.github.ricox.boostyvp.core.ui.appearance

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

enum class MenuDensity(val heightDp: Int, val gapDp: Int) { Tight(32, 1), Compact(34, 2), Normal(46, 6), Spacious(54, 8) }
data class MenuOptions(val density: MenuDensity = MenuDensity.Compact, val textSp: Int = 14,
    val widthDp: Int = 224, val order: List<String> = DefaultOrder, val hidden: Set<String> = emptySet(),
    val refresh: Boolean = true, val filter: Boolean = true, val signOut: Boolean = true) {
    fun normalized() = copy(textSp = textSp.takeIf { it in listOf(12, 14, 16, 18) } ?: 14,
        widthDp = widthDp.takeIf { it in listOf(192, 224, 256) } ?: 224,
        order = (order.filter { it in DefaultOrder }.distinct() + DefaultOrder).distinct(),
        hidden = hidden.intersect(DefaultOrder.toSet()) - "subscriptions")
    fun move(id: String, delta: Int): MenuOptions {
        val list = normalized().order.toMutableList(); val index = list.indexOf(id)
        if (index < 0) return this
        val target = (index + delta).coerceIn(0, list.lastIndex)
        list.add(target, list.removeAt(index)); return copy(order = list)
    }
    companion object { val DefaultOrder = listOf("subscriptions", "new", "history", "later", "favorites", "videos", "search", "queue") }
}
class MenuPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("interface_preferences", Context.MODE_PRIVATE)
    fun load() = MenuOptions(MenuDensity.entries.firstOrNull { it.name == prefs.getString("menu_density", null) } ?: MenuDensity.Compact,
        prefs.getInt("menu_text_sp", 14), prefs.getInt("menu_width_dp", 224),
        prefs.getString("menu_order", null)?.split(',') ?: MenuOptions.DefaultOrder,
        prefs.getString("menu_hidden", "")!!.split(',').toSet(), prefs.getBoolean("menu_refresh", true),
        prefs.getBoolean("menu_filter", true), prefs.getBoolean("menu_sign_out", true)).normalized()
    fun save(value: MenuOptions) { val safe = value.normalized(); prefs.edit()
        .putString("menu_density", safe.density.name).putInt("menu_text_sp", safe.textSp).putInt("menu_width_dp", safe.widthDp)
        .putString("menu_order", safe.order.joinToString(",")).putString("menu_hidden", safe.hidden.joinToString(","))
        .putBoolean("menu_refresh", safe.refresh).putBoolean("menu_filter", safe.filter).putBoolean("menu_sign_out", safe.signOut).apply() }
    fun observe(change: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> change() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
val LocalMenuOptions = staticCompositionLocalOf { MenuOptions() }
@Composable
internal fun ProvideMenuOptions(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { MenuPreferences(context) }
    var options by remember(prefs) { mutableStateOf(prefs.load()) }
    DisposableEffect(prefs) { val stop = prefs.observe { options = prefs.load() }; onDispose { stop() } }
    CompositionLocalProvider(LocalMenuOptions provides options, content = content)
}
