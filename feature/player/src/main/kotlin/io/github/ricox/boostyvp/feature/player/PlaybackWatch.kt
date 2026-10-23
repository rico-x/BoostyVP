package io.github.ricox.boostyvp.feature.player

/**
 * Notices playback that keeps buffering: [limitMs] of buffering within a
 * [windowMs] while the viewer wants it to play. Boosty's signed media links
 * expire, and a stream can stall on a dead CDN connection; both are fixed by
 * fetching the post again, not by waiting longer.
 *
 * Pure logic over monotonic milliseconds.
 */
internal class StallDetector(
    private val windowMs: Long = 60_000,
    private val limitMs: Long = 20_000,
) {
    private var windowStart = Long.MIN_VALUE
    private var bufferedMs = 0L
    private var bufferingSince: Long? = null

    fun bufferingStarted(nowMs: Long) {
        if (bufferingSince != null) return
        if (windowStart == Long.MIN_VALUE || nowMs - windowStart > windowMs) {
            windowStart = nowMs
            bufferedMs = 0
        }
        bufferingSince = nowMs
    }

    fun bufferingEnded(nowMs: Long) {
        val since = bufferingSince ?: return
        bufferedMs += nowMs - since
        bufferingSince = null
    }

    fun isStalled(nowMs: Long): Boolean {
        val current = bufferingSince?.let { nowMs - it } ?: 0
        return bufferingSince != null && bufferedMs + current >= limitMs
    }

    /** A rejected (cooldown) request must keep watching the ongoing buffering. */
    fun recoverIfStalled(nowMs: Long, refresh: () -> Boolean): Boolean {
        if (!isStalled(nowMs)) return false
        if (!refresh()) return false
        reset()
        return true
    }

    fun reset() {
        windowStart = Long.MIN_VALUE
        bufferedMs = 0
        bufferingSince = null
    }
}

/** One viewing session, shared by successive videos and link-recovery player views. */
internal class PlaybackInteraction(initialInteractionAt: Long) {
    var lastInteractionAt: Long = initialInteractionAt
        private set

    fun recordKey(atMs: Long) {
        lastInteractionAt = atMs
    }
}

/** The sleep timer pauses playback after [timerMs] without a remote key press; 0 disables it. */
internal fun sleepTimerDue(nowMs: Long, lastInputMs: Long, timerMs: Long, playing: Boolean): Boolean =
    timerMs > 0 && playing && nowMs - lastInputMs >= timerMs

/**
 * A remembered quality for a new video: the same height, else the nearest below
 * it (never more data than chosen), else the lowest there is.
 */
internal fun rememberedResolution(available: List<Int>, preferred: Int): Int? =
    available.filter { it <= preferred }.maxOrNull() ?: available.minOrNull()

/**
 * Where a video starts from its saved resume point. A point in the last seconds
 * means it was watched to the end -- often on another device, which Boosty
 * remembers -- so it starts again from the top rather than ending at once.
 */
internal fun resumeStartMs(savedMs: Long, durationMs: Long): Long {
    if (savedMs <= 0 || durationMs <= 0) return savedMs.coerceAtLeast(0)
    val tail = maxOf(RESUME_TAIL_MS, durationMs / 20)
    return if (savedMs >= durationMs - tail) 0 else savedMs
}

/** The final stretch that counts as watched: 10 s, or 5 % of a long video. */
private const val RESUME_TAIL_MS = 10_000L
