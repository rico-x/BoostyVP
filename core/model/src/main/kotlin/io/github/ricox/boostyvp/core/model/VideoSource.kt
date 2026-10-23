package io.github.ricox.boostyvp.core.model

/**
 * One playable rendition from a video block's `playerUrls` array.
 *
 * Boosty mixes adaptive manifests and per-quality progressive MP4s in the same
 * list, and frequently includes entries with an empty url -- those are dropped
 * during mapping and never reach this type.
 */
data class VideoSource(
    val kind: Kind,
    val url: String,
    /** Only meaningful for [Kind.Progressive]; higher is better. */
    val qualityRank: Int,
    /** The raw `type` string, kept for logging when Boosty adds a new one. */
    val rawType: String,
) {
    enum class Kind { Dash, Hls, Progressive, Unknown }

    companion object {
        /**
         * Selection order: adaptive first (the player can then adapt to the
         * network on its own), progressive only as a fallback.
         */
        private val KIND_PREFERENCE = mapOf(
            Kind.Dash to 3,
            Kind.Hls to 2,
            Kind.Progressive to 1,
            Kind.Unknown to 0,
        )

        /**
         * Best source at or below [maxProgressiveRank].
         *
         * The cap only applies to progressive renditions -- adaptive manifests
         * carry every rendition in one URL, so capping them here would be wrong;
         * limit those through the player's track selector instead.
         */
        fun pick(sources: List<VideoSource>, maxProgressiveRank: Int = Int.MAX_VALUE): VideoSource? =
            sources
                .filter { it.kind != Kind.Progressive || it.qualityRank <= maxProgressiveRank }
                .maxWithOrNull(
                    compareBy<VideoSource> { KIND_PREFERENCE[it.kind] ?: 0 }
                        .thenBy { it.qualityRank },
                )
    }
}
