package io.github.ricox.boostyvp.feature.player

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import io.github.ricox.boostyvp.core.ui.R as UiR
import io.github.ricox.boostyvp.core.ui.appearance.InterfacePreferences
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.ricox.boostyvp.core.model.VideoSource

internal val VideoSource.resolution: Int?
    get() = when (rawType) {
        "tiny" -> 144
        "lowest" -> 240
        "low" -> 360
        "medium" -> 480
        "high" -> 720
        "full_hd" -> 1080
        "quad_hd" -> 1440
        "ultra_hd" -> 2160
        else -> null
    }

/** Include each resolution once, even when it has several codecs or CDN hosts. */
internal fun qualityResolutions(sources: List<VideoSource>, trackHeights: List<Int>): List<Int> =
    (sources.filter { it.kind == VideoSource.Kind.Progressive }.mapNotNull { it.resolution } + trackHeights)
        .filter { it > 0 }
        .distinct()
        .sortedDescending()

/** One exact rendition and its CDN alternates; never silently change a manual quality. */
internal fun sourcesForResolution(sources: List<VideoSource>, height: Int): List<VideoSource> =
    sources.filter { it.kind == VideoSource.Kind.Progressive && it.resolution == height }
        .distinctBy { it.url }

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class VideoQualityController(
    private val player: ExoPlayer,
    private val fallback: FallbackController,
    private val candidates: List<VideoSource>,
    /** A quality picked in the dialog, null for "Авто"; the caller may remember it. */
    private val onManualChoice: (Int?) -> Unit = {},
) {
    private var selectedHeight: Int? = null
    /** A remembered quality still waiting for the adaptive stream's tracks. */
    private var rememberedHeight: Int? = null
    private var dialog: AlertDialog? = null
    private var qualityButton: Button? = null

    fun attach(playerView: PlayerView) {
        val controls = playerView.findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_basic_controls)
            ?: return
        val settings = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_settings)
        qualityButton = Button(playerView.context, null, android.R.attr.borderlessButtonStyle).apply {
            isAllCaps = false
            background = playerControlBackground(playerView.context)
            setTextColor(playerControlTextColors(playerView.context))
            setPadding(playerView.context.playerDp(16), 0, playerView.context.playerDp(16), 0)
            textSize = 14f
            isFocusable = true
            setOnClickListener { show(playerView) }
        }
        // A remembered quality may already be chosen before the controls exist.
        showLabel(selectedHeight)
        controls.addView(
            qualityButton,
            controls.indexOfChild(settings).coerceAtLeast(0),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun supportedTracks(): List<Pair<Tracks.Group, Int>> = buildList {
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEach { group ->
            for (index in 0 until group.length) {
                if (group.isTrackSupported(index) && group.getTrackFormat(index).height > 0) {
                    add(group to index)
                }
            }
        }
    }

    /**
     * Start at a remembered quality, before [FallbackController.start]: the same
     * height or the nearest below it. The button shows what is actually used.
     */
    fun preselect(preferredHeight: Int?) {
        val wanted = preferredHeight ?: return
        val progressive = qualityResolutions(candidates, emptyList())
        if (progressive.isEmpty()) {
            rememberedHeight = wanted // decided once the manifest's tracks are known
            return
        }
        val height = rememberedResolution(progressive, wanted) ?: return
        selectedHeight = height
        showLabel(height)
        fallback.replaceSources(sourcesForResolution(candidates, height))
    }

    private fun showLabel(height: Int?) {
        val label = height?.let { "${it}p" } ?: "Авто"
        qualityButton?.text = "Качество: $label"
        qualityButton?.contentDescription = "Качество видео: $label"
    }

    fun onTracksChanged() {
        rememberedHeight?.let { wanted ->
            val heights = supportedTracks().map { (group, index) -> group.getTrackFormat(index).height }
            if (heights.isNotEmpty()) {
                rememberedHeight = null
                selectedHeight = rememberedResolution(heights.distinct(), wanted)
                showLabel(selectedHeight)
            }
        }
        val height = selectedHeight ?: return
        val match = supportedTracks()
            .filter { (group, index) -> group.getTrackFormat(index).height == height }
            .maxByOrNull { (group, index) -> group.getTrackFormat(index).bitrate }
            ?: return
        val override = TrackSelectionOverride(match.first.mediaTrackGroup, match.second)
        if (player.trackSelectionParameters.overrides[override.mediaTrackGroup] != override) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(override)
                .build()
        }
    }

    fun show(playerView: PlayerView) {
        dialog?.dismiss()
        val tracks = supportedTracks()
        val heights = qualityResolutions(
            candidates,
            tracks.map { (group, index) -> group.getTrackFormat(index).height },
        )
        val labels = arrayOf("Авто", *heights.map { "${it}p" }.toTypedArray())
        val checked = selectedHeight?.let { heights.indexOf(it) + 1 } ?: 0
        val previousTimeout = playerView.controllerShowTimeoutMs
        val appearance = InterfacePreferences(playerView.context).load()
        playerView.controllerShowTimeoutMs = 0
        dialog = AlertDialog.Builder(playerView.context)
            .setCustomTitle(TextView(playerView.context).apply {
                text = "Качество видео"
                textSize = 24f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                setTextColor(playerView.context.getColor(UiR.color.boostyvp_text))
                val padding = playerView.context.playerDp(24)
                setPadding(padding, padding, padding, padding / 2)
            })
            .setSingleChoiceItems(labels, checked) { openedDialog, which ->
                select(heights.getOrNull(which - 1))
                openedDialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .create()
            .apply {
                setOnDismissListener {
                    playerView.controllerShowTimeoutMs =
                        if (player.playbackState == androidx.media3.common.Player.STATE_ENDED) 0 else previousTimeout
                    playerView.showController()
                    qualityButton?.requestFocus()
                    dialog = null
                }
                show()
                window?.setBackgroundDrawable(GradientDrawable().apply {
                    setColor(appearance.theme.surface)
                    cornerRadius = playerView.context.playerDp(24).toFloat()
                    setStroke(playerView.context.playerDp(1), playerView.context.getColor(UiR.color.boostyvp_border))
                })
                listView.selector = GradientDrawable().apply {
                    setColor(appearance.theme.raised)
                    cornerRadius = playerView.context.playerDp(12).toFloat()
                    setStroke(playerView.context.playerDp(2), appearance.accent.textArgb)
                }
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(appearance.accent.textArgb)
            }
    }

    private fun select(height: Int?) {
        selectedHeight = height
        val label = height?.let { "${it}p" } ?: "Авто"
        qualityButton?.text = "Качество: $label"
        qualityButton?.contentDescription = "Качество видео: $label"
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
        if (height == null) {
            fallback.selectSources(candidates)
        } else {
            val progressive = sourcesForResolution(candidates, height)
            if (progressive.isNotEmpty()) {
                fallback.selectSources(progressive, keepCurrent = true)
            } else {
                // A manifest-only video: select the actual supported track.
                val adaptive = candidates.filter {
                    it.kind == VideoSource.Kind.Dash || it.kind == VideoSource.Kind.Hls
                }
                if (adaptive.isNotEmpty()) fallback.selectSources(adaptive, keepCurrent = true)
                onTracksChanged()
            }
        }
        onManualChoice(height)
    }

    fun dismiss() {
        dialog?.dismiss()
    }
}
