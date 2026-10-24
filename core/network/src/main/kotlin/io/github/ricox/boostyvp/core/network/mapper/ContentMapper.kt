package io.github.ricox.boostyvp.core.network.mapper

import android.util.Log
import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.VideoSource
import io.github.ricox.boostyvp.core.network.dto.ContentBlockDto
import io.github.ricox.boostyvp.core.network.dto.PlayerUrlDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "ContentMapper"

/**
 * Boosty double-encodes text. The `content` field is a JSON *string* whose
 * value is itself JSON, normally a three element array:
 *
 *     "[\"the actual text\",\"unstyled\",[[0,0,12]]]"
 *
 * Element 0 is the text, element 1 a style name, element 2 a list of styling
 * ranges this app currently ignores.
 *
 * Anything unexpected degrades to the raw string rather than throwing -- a post
 * with odd markup should still be readable.
 */
internal fun decodeTextContent(json: Json, content: JsonElement?): String {
    val raw = (content as? JsonPrimitive)?.contentOrNull ?: return ""
    if (raw.isBlank()) return ""
    if (!raw.trimStart().startsWith("[")) return raw

    return try {
        val parsed = json.parseToJsonElement(raw)
        val first = (parsed as? JsonArray)?.firstOrNull()
        (first as? JsonPrimitive)?.contentOrNull ?: raw
    } catch (e: Exception) {
        // Parser exceptions can include private post text; never log them.
        Log.d(TAG, "Text block was not the expected wrapper; using raw value")
        raw
    }
}

// Not JsonPrimitive.intOrNull: on text such as "1 (a quote, then a digit) it
// throws instead of returning null (found by fuzzing), failing the whole page.
// The content of a JSON number and of a numeric string parse the same way.
private fun JsonElement?.asIntOrNull(): Int? =
    (this as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

/**
 * Quality ranks for progressive MP4 renditions. Higher is better.
 *
 * Unlisted names rank 0 rather than being dropped: a new tier Boosty adds is
 * still playable, it just sorts below everything known.
 */
private val PROGRESSIVE_RANKS = mapOf(
    "tiny" to 1,
    "lowest" to 2,
    "low" to 3,
    "medium" to 4,
    "high" to 5,
    "full_hd" to 6,
    "quad_hd" to 7,
    "ultra_hd" to 8,
)

private val DASH_TYPES = setOf("dash", "dash_live", "live_playback_dash")
private val HLS_TYPES = setOf("hls", "hls_live", "live_ondemand_hls", "live_playback_hls")

internal fun PlayerUrlDto.toVideoSourceOrNull(): VideoSource? {
    // Boosty returns a slot per rendition whether or not it produced one.
    if (url.isBlank()) return null

    val kind = when {
        type in DASH_TYPES -> VideoSource.Kind.Dash
        type in HLS_TYPES -> VideoSource.Kind.Hls
        type in PROGRESSIVE_RANKS -> VideoSource.Kind.Progressive
        else -> {
            Log.w(TAG, "Unrecognised playerUrls type '$type'; treating as progressive")
            VideoSource.Kind.Unknown
        }
    }

    return VideoSource(
        kind = kind,
        url = url,
        qualityRank = PROGRESSIVE_RANKS[type] ?: 0,
        rawType = type,
    )
}

internal fun ContentBlockDto.toDomain(json: Json): ContentBlock = when (type) {

    "text", "header" -> ContentBlock.Text(
        text = decodeTextContent(json, content),
        modificator = if (type == "header") "HEADER" else modificator,
    )

    "image" -> ContentBlock.Image(
        id = id.orEmpty(),
        url = url.orEmpty(),
        width = width.asIntOrNull(),
        height = height.asIntOrNull(),
    )

    "ok_video", "video" -> ContentBlock.Video(
        id = id.orEmpty(),
        vid = vid,
        title = title,
        duration = duration ?: 0,
        width = width.asIntOrNull(),
        height = height.asIntOrNull(),
        previewUrl = preview ?: defaultPreview,
        failoverHost = failoverHost,
        timeCode = timeCode,
        sources = playerUrls.mapNotNull { it.toVideoSourceOrNull() },
        // Boosty reports transcoding progress through both fields depending on
        // how the video was uploaded, so accept either as "done".
        isReady = uploadStatus.equals("ok", ignoreCase = true) ||
            status.equals("ok", ignoreCase = true) ||
            (uploadStatus == null && status == null),
    )

    "audio_file" -> ContentBlock.Audio(
        id = id.orEmpty(),
        title = title,
        url = url,
        duration = duration ?: 0,
    )

    "link" -> ContentBlock.Link(
        url = url.orEmpty(),
        text = decodeTextContent(json, content).takeIf { it.isNotBlank() },
    )

    "file" -> ContentBlock.File(
        id = id.orEmpty(),
        title = title,
        url = url,
        sizeBytes = size,
    )

    else -> ContentBlock.Unknown(rawType = type)
}

internal fun List<ContentBlockDto>.toDomain(json: Json): List<ContentBlock> =
    map { it.toDomain(json) }
