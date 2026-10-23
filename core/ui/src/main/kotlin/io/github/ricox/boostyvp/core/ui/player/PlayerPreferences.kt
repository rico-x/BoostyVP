package io.github.ricox.boostyvp.core.ui.player

import android.content.Context

class PlayerPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("player_preferences", Context.MODE_PRIVATE)

    fun load(): PlayerOptions = PlayerOptions(
        seekBackSeconds = prefs.getInt("seek_back_seconds", 10),
        seekForwardSeconds = prefs.getInt("seek_forward_seconds", 30),
        controllerSeconds = prefs.getInt("controller_seconds", 4),
        rememberSpeed = prefs.getBoolean("remember_speed", true),
        endAction = EndAction.fromId(prefs.getString("end_action", null)),
        sleepTimerMinutes = prefs.getInt("sleep_timer_minutes", 0),
        rememberQuality = prefs.getBoolean("remember_quality", true),
        speedPerAuthor = prefs.getBoolean("speed_per_author", false),
    ).normalized()

    fun save(options: PlayerOptions) {
        val safe = options.normalized()
        prefs.edit()
            .putInt("seek_back_seconds", safe.seekBackSeconds)
            .putInt("seek_forward_seconds", safe.seekForwardSeconds)
            .putInt("controller_seconds", safe.controllerSeconds)
            .putBoolean("remember_speed", safe.rememberSpeed)
            .putString("end_action", safe.endAction.id)
            .putInt("sleep_timer_minutes", safe.sleepTimerMinutes)
            .putBoolean("remember_quality", safe.rememberQuality)
            .putBoolean("speed_per_author", safe.speedPerAuthor)
            .apply()
        // Switching the preference off forgets the remembered choice as well.
        if (!safe.rememberQuality) prefs.edit().remove(KEY_QUALITY).apply()
    }

    /** The speed for a new video: the author's own when [PlayerOptions.speedPerAuthor] is on. */
    fun playbackSpeed(blogUrl: String? = null): Float {
        val options = load()
        if (!options.rememberSpeed) return 1f
        val global = prefs.getFloat("playback_speed", 1f)
        val speed = if (options.speedPerAuthor && !blogUrl.isNullOrBlank()) {
            prefs.getFloat(authorKey(blogUrl), global)
        } else global
        return PlayerOptions.safeSpeed(speed)
    }

    fun saveSpeed(speed: Float, blogUrl: String? = null) {
        val options = load()
        if (!options.rememberSpeed) return
        val key = if (options.speedPerAuthor && !blogUrl.isNullOrBlank()) authorKey(blogUrl) else "playback_speed"
        prefs.edit().putFloat(key, PlayerOptions.safeSpeed(speed)).apply()
    }

    /** Height in pixels of the last quality picked by hand; null is "Авто" or nothing remembered. */
    fun preferredQuality(): Int? =
        if (load().rememberQuality) prefs.getInt(KEY_QUALITY, 0).takeIf { it > 0 } else null

    fun savePreferredQuality(height: Int?) {
        if (!load().rememberQuality) return
        if (height == null) prefs.edit().remove(KEY_QUALITY).apply()
        else prefs.edit().putInt(KEY_QUALITY, height).apply()
    }

    private fun authorKey(blogUrl: String) = "playback_speed_author_$blogUrl"

    private companion object {
        const val KEY_QUALITY = "preferred_quality_height"
    }
}
