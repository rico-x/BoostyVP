package io.github.ricox.boostyvp.core.ui.player

import android.content.Context

class PlayerPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("player_preferences", Context.MODE_PRIVATE)

    fun load(): PlayerOptions = PlayerOptions(
        seekBackSeconds = prefs.getInt("seek_back_seconds", 10),
        seekForwardSeconds = prefs.getInt("seek_forward_seconds", 30),
        controllerSeconds = prefs.getInt("controller_seconds", 4),
        rememberSpeed = prefs.getBoolean("remember_speed", true),
    ).normalized()

    fun save(options: PlayerOptions) {
        val safe = options.normalized()
        prefs.edit()
            .putInt("seek_back_seconds", safe.seekBackSeconds)
            .putInt("seek_forward_seconds", safe.seekForwardSeconds)
            .putInt("controller_seconds", safe.controllerSeconds)
            .putBoolean("remember_speed", safe.rememberSpeed)
            .apply()
    }

    fun playbackSpeed(): Float = if (load().rememberSpeed) {
        PlayerOptions.safeSpeed(prefs.getFloat("playback_speed", 1f))
    } else 1f

    fun saveSpeed(speed: Float) {
        if (load().rememberSpeed) prefs.edit().putFloat("playback_speed", PlayerOptions.safeSpeed(speed)).apply()
    }
}
