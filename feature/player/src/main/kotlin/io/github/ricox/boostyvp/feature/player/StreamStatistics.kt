package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView

/** No URIs, titles, account identifiers or events leave the device. */
internal data class StreamStats(
    val width: Int = -1, val height: Int = -1, val fps: Float = -1f,
    val mime: String? = null, val codecs: String? = null, val bitrate: Int = -1,
    val decoder: String? = null, val audio: String? = null,
    val bufferMs: Long = 0, val dropped: Int = 0, val rendered: Int = 0,
) {
    fun text(): String = buildString {
        append(tr(UiText.stream_statistics_001))
        append(tr(UiText.stream_statistics_002)).append(if (width > 0 && height > 0) "$width × $height" else tr(UiText.stream_statistics_003))
        if (fps > 0) append(" · ").append(java.lang.String.format(java.util.Locale.ROOT, "%.2f", fps)).append(tr(UiText.stream_statistics_004))
        append(tr(UiText.stream_statistics_005)).append(codecs?.takeIf { it.isNotBlank() } ?: mime ?: tr(UiText.stream_statistics_003))
        append(tr(UiText.stream_statistics_006)).append(if (bitrate > 0)
            java.lang.String.format(java.util.Locale.ROOT, tr(UiText.stream_statistics_007), bitrate / 1_000_000.0) else tr(UiText.stream_statistics_003))
        append(tr(UiText.stream_statistics_008)).append(decoder ?: tr(UiText.stream_statistics_003))
        append(tr(UiText.stream_statistics_009)).append(audio ?: tr(UiText.stream_statistics_003))
        append(tr(UiText.stream_statistics_010)).append(java.lang.String.format(java.util.Locale.ROOT, tr(UiText.stream_statistics_011), bufferMs.coerceAtLeast(0) / 1000.0))
        append(tr(UiText.stream_statistics_012)).append(dropped.coerceAtLeast(0))
        append(tr(UiText.stream_statistics_013)).append(rendered.coerceAtLeast(0))
        append(tr(UiText.stream_statistics_014))
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class StreamStatistics(private val view: PlayerView, private val player: ExoPlayer) {
    private var decoder: String? = null
    private var attached = false
    private val info = TextView(view.context).apply {
        tag = "stream_statistics"
        textSize = 15f
        typeface = Typeface.MONOSPACE
        setTextColor(Color.WHITE)
        setPadding(context.playerDp(16), context.playerDp(12), context.playerDp(16), context.playerDp(12))
        background = GradientDrawable().apply { setColor(0xE6101114.toInt()); cornerRadius = context.playerDp(10).toFloat() }
        visibility = View.GONE
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val button = Button(view.context, null, android.R.attr.borderlessButtonStyle).apply {
        tag = "stream_statistics_button"
        text = tr(UiText.stream_statistics_015)
        contentDescription = tr(UiText.stream_statistics_016)
        isAllCaps = false
        textSize = 14f
        background = playerControlBackground(context)
        setTextColor(playerControlTextColors(context))
        setOnClickListener { toggle() }
    }
    private val listener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String,
            initializedTimestampMs: Long, initializationDurationMs: Long) { decoder = decoderName }
    }
    private val poll = object : Runnable {
        override fun run() {
            if (!attached || !shown) return
            val format = player.videoFormat
            val audio = player.audioFormat
            val counters = player.videoDecoderCounters
            counters?.ensureUpdated()
            info.text = StreamStats(format?.width ?: -1, format?.height ?: -1, format?.frameRate ?: -1f,
                format?.sampleMimeType, format?.codecs,
                format?.averageBitrate?.takeIf { it > 0 } ?: format?.peakBitrate ?: -1,
                decoder, audio?.codecs ?: audio?.sampleMimeType,
                player.totalBufferedDuration, counters?.droppedBufferCount ?: 0,
                counters?.renderedOutputBufferCount ?: 0).text()
            view.postDelayed(this, 1_000)
        }
    }
    init {
        view.overlayFrameLayout?.addView(info, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START).apply { setMargins(view.context.playerDp(24), view.context.playerDp(64), 0, 0) })
        view.findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_basic_controls)?.let { controls ->
            controls.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        player.addAnalyticsListener(listener)
    }
    val shown get() = info.visibility == View.VISIBLE
    fun toggle() { if (shown) hide() else { info.visibility = View.VISIBLE; start() } }
    fun hide() { info.visibility = View.GONE; view.removeCallbacks(poll) }
    fun start() { attached = true; view.removeCallbacks(poll); if (shown) poll.run() }
    fun stop() { attached = false; view.removeCallbacks(poll) }
    fun release() {
        stop()
        player.removeAnalyticsListener(listener)
        (info.parent as? ViewGroup)?.removeView(info)
        (button.parent as? ViewGroup)?.removeView(button)
    }
}
