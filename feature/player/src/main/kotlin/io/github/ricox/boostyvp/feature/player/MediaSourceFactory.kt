package io.github.ricox.boostyvp.feature.player

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.VideoSource

private const val TAG = "MediaSourceFactory"

/**
 * Boosty's CDN checks the request signature the same way the API does. Fetching
 * a manifest with a bare ExoPlayer user-agent typically succeeds while the
 * segment requests come back 403, which presents as a video that buffers
 * forever and then fails -- so the browser headers must be set here too, not
 * only on the API client.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun boostyDataSourceFactory(): DataSource.Factory =
    DefaultHttpDataSource.Factory()
        .setUserAgent(WEB_USER_AGENT)
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://boosty.to/",
                "Origin" to "https://boosty.to",
            ),
        )
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(30_000)

private const val WEB_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/**
 * Builds a media source for [source], choosing the right ExoPlayer source type.
 *
 * The URL is *not* inspected for a file extension: Boosty serves manifests from
 * paths that do not always end in .mpd or .m3u8, so the declared type from
 * `playerUrls` is the only reliable signal.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun buildMediaSource(
    source: VideoSource,
    dataSourceFactory: DataSource.Factory = boostyDataSourceFactory(),
): MediaSource {
    val item = MediaItem.fromUri(source.url)
    return when (source.kind) {
        VideoSource.Kind.Dash ->
            DashMediaSource.Factory(dataSourceFactory).createMediaSource(item)

        VideoSource.Kind.Hls ->
            HlsMediaSource.Factory(dataSourceFactory)
                // Boosty's HLS playlists sometimes omit codec strings; letting
                // ExoPlayer sniff avoids a spurious "unsupported format".
                .setAllowChunklessPreparation(true)
                .createMediaSource(item)

        VideoSource.Kind.Progressive, VideoSource.Kind.Unknown ->
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(item)
    }
}

/**
 * An ordered playlist of fallbacks for one video: the best source first, then
 * every other distinct source, then the same set again against [failoverHost]
 * if Boosty supplied one.
 *
 * ExoPlayer does not retry across *different* URLs on its own, so the retry
 * ladder is materialised here and stepped through by [FallbackController]
 * on a fatal error.
 */
fun playbackCandidates(
    video: ContentBlock.Video,
    maxProgressiveRank: Int = Int.MAX_VALUE,
): List<VideoSource> {
    val sources = video.sources.filter {
        it.kind != VideoSource.Kind.Progressive || it.qualityRank <= maxProgressiveRank
    }.distinctBy { it.url }
    val preferred = VideoSource.pick(sources)
    val ordered = buildList {
        if (preferred != null) add(preferred)
        addAll(sources.filter { it != preferred })
    }

    val host = video.failoverHost?.takeIf { it.isNotBlank() }
    if (host == null) return ordered

    val failovers = ordered.mapNotNull { candidate ->
        rewriteHost(candidate.url, host)?.let { candidate.copy(url = it) }
    }

    if (failovers.isEmpty()) {
        Log.d(TAG, "failoverHost '$host' produced no usable alternates")
    }
    return (ordered + failovers).distinctBy { it.url }
}

/** Swaps the host of [url] for [newHost], preserving scheme, path and query. */
internal fun rewriteHost(url: String, newHost: String): String? = try {
    val original = java.net.URI(url)
    val host = newHost.substringAfter("://").trim('/')
    // URI's multi-argument constructor re-encodes decoded components and can
    // change a signed query (e.g. %2F into /). Replace only the authority.
    val authority = java.net.URI(original.scheme, original.userInfo, host, original.port, null, null, null)
        .rawAuthority
    buildString {
        append(original.scheme).append("://").append(authority)
        append(original.rawPath.orEmpty())
        original.rawQuery?.let { append('?').append(it) }
        original.rawFragment?.let { append('#').append(it) }
    }
} catch (e: Exception) {
    Log.w(TAG, "Could not rewrite host on $url", e)
    null
}
