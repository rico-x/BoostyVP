package io.github.ricox.boostyvp.core.model

/**
 * One element of a post body. Boosty returns these as a heterogeneous array
 * discriminated by a `type` string.
 *
 * [Unknown] exists on purpose: Boosty ships new block types without notice, and
 * an exhaustive parse would fail the whole post. Renderers skip what they do not
 * recognise.
 */
sealed interface ContentBlock {

    data class Text(
        val text: String,
        val modificator: String?,
    ) : ContentBlock

    data class Image(
        val id: String,
        val url: String,
        val width: Int?,
        val height: Int?,
    ) : ContentBlock

    data class Video(
        val id: String,
        val vid: String?,
        val title: String?,
        /** Seconds. */
        val duration: Int,
        val width: Int?,
        val height: Int?,
        val previewUrl: String?,
        /** Secondary CDN host to retry against when the primary fails. */
        val failoverHost: String?,
        /** Resume point Boosty itself recorded, in seconds. */
        val timeCode: Int?,
        val sources: List<VideoSource>,
        val isReady: Boolean,
    ) : ContentBlock {
        val hasPlayableSource: Boolean get() = sources.isNotEmpty()
    }

    data class Audio(
        val id: String,
        val title: String?,
        val url: String?,
        val duration: Int,
    ) : ContentBlock

    data class Link(
        val url: String,
        val text: String?,
    ) : ContentBlock

    data class File(
        val id: String,
        val title: String?,
        val url: String?,
        val sizeBytes: Long?,
    ) : ContentBlock

    data class Unknown(val rawType: String) : ContentBlock
}
