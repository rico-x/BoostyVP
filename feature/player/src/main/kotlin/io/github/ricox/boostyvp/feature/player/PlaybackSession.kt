package io.github.ricox.boostyvp.feature.player

import android.app.PendingIntent
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaSession

/** Foreground playback only: no service keeping the TV awake after the viewer leaves. */
internal fun createPlaybackSession(context: Context, player: Player, postId: String): MediaSession {
    val launch = context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
    return MediaSession.Builder(context, player).setId("boostyvp-$postId").apply {
        if (launch != null) setSessionActivity(PendingIntent.getActivity(context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }.build()
}
