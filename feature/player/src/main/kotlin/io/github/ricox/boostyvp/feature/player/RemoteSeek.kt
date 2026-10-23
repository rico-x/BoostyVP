package io.github.ricox.boostyvp.feature.player

import kotlin.math.abs

/**
 * Seeking with ←/→ while the player controls are hidden.
 *
 * A press moves one configured step; holding the key accelerates. The target is
 * applied once, on release, so a long hold does not send dozens of seeks to a
 * network stream. Presses that follow quickly continue from the last target,
 * not from the player's position, which lags behind a seek that is still
 * buffering; the amount shown restarts from zero when the direction changes.
 *
 * Pure logic: times are the key events' own uptime values.
 */
internal class RemoteSeek(private val chainWindowMs: Long = CHAIN_WINDOW_MS) {
    /**
     * What the indicator shows. [deltaMs] counts from where the current run in
     * one direction began, so a change of direction starts again from zero.
     */
    data class Preview(val direction: Int, val targetMs: Long, val deltaMs: Long, val durationMs: Long)

    private var active = false
    private var direction = 0
    private var stepMs = 0L
    private var targetMs = 0L
    private var lastEventMs = 0L
    private var runStartMs = 0L
    private var lastCommitMs: Long? = null
    private var lastCommitAt = Long.MIN_VALUE

    /** Key down, first or repeated. [durationMs] <= 0 means unknown (no upper bound). */
    fun press(
        direction: Int,
        stepMs: Long,
        downTimeMs: Long,
        eventTimeMs: Long,
        positionMs: Long,
        durationMs: Long,
        firstEvent: Boolean,
    ): Preview {
        require(direction == 1 || direction == -1)
        if (firstEvent || !active) {
            val chained = lastCommitMs?.takeIf { eventTimeMs - lastCommitAt <= chainWindowMs }
            val origin = when {
                active -> targetMs // the other arrow pressed while one is held
                chained != null -> chained
                else -> positionMs.coerceAtLeast(0)
            }
            // The amount shown restarts from zero whenever the direction changes,
            // even straight after holding the other arrow; taps the same way add up.
            val sameRun = (active || chained != null) && direction == this.direction
            if (!sameRun) runStartMs = origin
            active = true
            this.direction = direction
            this.stepMs = stepMs
            targetMs = clamp(origin + direction * stepMs, durationMs)
        } else {
            val heldMs = eventTimeMs - downTimeMs
            if (heldMs >= HOLD_START_MS) {
                val elapsed = (eventTimeMs - lastEventMs).coerceIn(0, MAX_REPEAT_GAP_MS)
                val advance = (this.stepMs * stepsPerSecond(heldMs) * elapsed / 1_000.0).toLong()
                targetMs = clamp(targetMs + this.direction * advance, durationMs)
            }
        }
        lastEventMs = eventTimeMs
        return Preview(this.direction, targetMs, targetMs - runStartMs, durationMs)
    }

    /** Key up: the position to seek to, or null when no press is in progress. */
    fun commit(eventTimeMs: Long): Long? {
        if (!active) return null
        active = false
        lastCommitMs = targetMs
        lastCommitAt = eventTimeMs
        return targetMs
    }

    /** A cancelled press seeks nowhere and ends the chain. */
    fun cancel() {
        active = false
        lastCommitMs = null
    }

    private fun clamp(value: Long, durationMs: Long): Long =
        if (durationMs > 0) value.coerceIn(0, durationMs) else value.coerceAtLeast(0)

    companion object {
        const val CHAIN_WINDOW_MS = 1_500L
        /** Below this a held key behaves like one press; Android starts repeating at ~400 ms. */
        const val HOLD_START_MS = 500L
        /** A stalled remote must not turn one late repeat into a huge jump. */
        const val MAX_REPEAT_GAP_MS = 200L

        /** Steps per second while held: 2 after half a second, 6 after 2 s, 15 after 5 s. */
        fun stepsPerSecond(heldMs: Long): Int = when {
            heldMs < 2_000 -> 2
            heldMs < 5_000 -> 6
            else -> 15
        }
    }
}

/** 0:05, 12:34, 1:02:03 -- the same shape as the player's own time labels. */
internal fun formatSeekTime(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1_000
    val hours = total / 3_600
    val minutes = (total % 3_600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** "+1:30" / "−0:10" with a real minus sign. */
internal fun formatSeekDelta(deltaMs: Long): String =
    (if (deltaMs < 0) "−" else "+") + formatSeekTime(abs(deltaMs))
