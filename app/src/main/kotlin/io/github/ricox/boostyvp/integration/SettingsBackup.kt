package io.github.ricox.boostyvp.integration

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import android.content.Context
import android.content.SharedPreferences
import io.github.ricox.boostyvp.core.ui.appearance.*
import io.github.ricox.boostyvp.core.ui.player.PlayerPreferences
import kotlinx.serialization.json.*

/** Explicit allowlist. Session, pairing, account, library, URLs and author identifiers never enter a backup. */
object BackupSchema {
    private fun fields(type: Char, names: String) = names.split(' ').associateWith { type }
    val files = mapOf(
        "player_preferences" to (fields('i', "seek_back_seconds seek_forward_seconds controller_seconds sleep_timer_minutes preferred_quality_height") +
            fields('b', "remember_speed remember_quality speed_per_author channel_video_navigation transport_video_navigation digit_seek double_back_exit") +
            fields('s', "end_action video_scale") + fields('f', "playback_speed")),
        "interface_preferences" to (fields('b', "sidebar_auto_collapse card_show_author card_show_date card_show_duration card_hide_repeated_author focus_zoom focus_animation focused_full_title player_thick_seek_bar player_clock player_end_time") +
            fields('i', "card_title_lines focus_width menu_text_sp menu_width_dp") +
            fields('b', "menu_refresh menu_filter menu_sign_out") +
            fields('s', "card_size card_text_size card_metadata_size catalog_density card_style focus_color author_header dark_theme accent_color player_panel_layout player_time_text menu_order menu_hidden menu_density app_language")),
        "tv_channels" to (fields('b', "enabled watch_next") + fields('i', "count cards") + fields('s', "sources")),
    )
    fun decode(text: String): Map<String, Map<String, Any>> {
        require(text.toByteArray().size <= 64 * 1024) { tr(UiText.settings_backup_001) }
        val root = Json.parseToJsonElement(text).jsonObject
        require(root["app"]?.jsonPrimitive?.content == "BoostyVP" && root["schema"]?.jsonPrimitive?.int == 1) { tr(UiText.settings_backup_002) }
        val preferences = root.getValue("preferences").jsonObject
        require(preferences.keys == files.keys) { tr(UiText.settings_backup_003) }
        return preferences.mapValues { (file, element) ->
            element.jsonObject.mapValues { (key, value) ->
                require(key in files.getValue(file)) { tr(UiText.settings_backup_004, key) }
                val p = value as? JsonPrimitive ?: error(tr(UiText.settings_backup_005, key))
                when (files.getValue(file).getValue(key)) {
                    'b' -> { require(!p.isString); p.boolean }
                    'i' -> { require(!p.isString); p.int }
                    'f' -> { require(!p.isString); p.float.also { require(it.isFinite()) } }
                    else -> { require(p.isString && p.content.length <= 200); p.content }
                }
            }
        }
    }
}

class SettingsBackup(private val context: Context) {
    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    fun export(): String = buildJsonObject {
        put("app", "BoostyVP"); put("schema", 1)
        putJsonObject("preferences") {
            BackupSchema.files.forEach { (file, allowed) -> putJsonObject(file) {
                prefs(file).all.filterKeys { it in allowed }.forEach { (key, value) ->
                    when (value) { is Boolean -> put(key, value); is Number -> put(key, value); is String -> put(key, value) }
                }
            } }
        }
    }.toString()

    /** Validate the whole document before touching any file; roll back all files on a write failure. */
    fun restore(text: String) {
        val decoded = BackupSchema.decode(text)
        val before = BackupSchema.files.keys.associateWith { prefs(it).all.toMap() }
        try {
            decoded.forEach { (file, values) ->
                val editor = prefs(file).edit()
                BackupSchema.files.getValue(file).keys.forEach(editor::remove)
                values.forEach { (key, value) -> editor.putValue(key, value) }
                check(editor.commit()) { tr(UiText.settings_backup_006) }
            }
            // Normalize supported presets before any screen consumes the imported values.
            PlayerPreferences(context).let { it.save(it.load()) }
            InterfacePreferences(context).let { it.save(it.load()) }
            CardSizePreferences(context).let { it.save(it.load()) }
            TvChannelPreferences(context).let { it.save(it.load()) }
            io.github.ricox.boostyvp.core.ui.appearance.MenuPreferences(context).let { it.save(it.load()) }
        } catch (e: Exception) {
            before.forEach { (file, values) -> prefs(file).edit().also { edit ->
                BackupSchema.files.getValue(file).keys.forEach(edit::remove)
                values.filterKeys { it in BackupSchema.files.getValue(file) }.forEach { (key, value) -> if (value != null) edit.putValue(key, value) }
            }.commit() }
            throw e
        }
    }

    fun reset() {
        // No clear(): leave unrelated/future keys, credentials, database and library untouched.
        BackupSchema.files.forEach { (file, fields) -> prefs(file).edit().also { e -> fields.keys.forEach(e::remove) }.commit() }
    }
    private fun SharedPreferences.Editor.putValue(key: String, value: Any) {
        when (value) { is Boolean -> putBoolean(key, value); is Int -> putInt(key, value); is Float -> putFloat(key, value); is String -> putString(key, value) }
    }
}
