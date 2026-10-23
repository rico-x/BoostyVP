package io.github.ricox.boostyvp.core.ui.player

/** What the player does when a video finishes. */
enum class EndAction(val id: String, val label: String) {
    /** Stay on the last frame with the controls shown -- the original behaviour. */
    Stop("stop", "остановиться"),
    /** The next video to the right in the author's row, after a short countdown. */
    Next("next", "следующее видео автора"),
    Repeat("repeat", "повторить"),
    Close("close", "закрыть плеер");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Stop
    }
}

/** Non-secret presentation preferences, independent of the Boosty API. */
data class PlayerOptions(
    val seekBackSeconds: Int = 10,
    val seekForwardSeconds: Int = 30,
    val controllerSeconds: Int = 4,
    val rememberSpeed: Boolean = true,
    val endAction: EndAction = EndAction.Stop,
    /** Pause after this long without a remote key press during playback; 0 is off. */
    val sleepTimerMinutes: Int = 0,
    /** A quality picked by hand becomes the starting quality of later videos. */
    val rememberQuality: Boolean = true,
    /** Remember the speed separately for each author instead of one for all. */
    val speedPerAuthor: Boolean = false,
) {
    fun normalized() = copy(
        seekBackSeconds = seekBackSeconds.takeIf { it in BACK_STEPS } ?: 10,
        seekForwardSeconds = seekForwardSeconds.takeIf { it in FORWARD_STEPS } ?: 30,
        controllerSeconds = controllerSeconds.takeIf { it in CONTROLLER_TIMES } ?: 4,
        sleepTimerMinutes = sleepTimerMinutes.takeIf { it in SLEEP_TIMES } ?: 0,
    )

    val controllerTimeoutMs: Int get() = controllerSeconds * 1_000

    val sleepTimerMs: Long get() = sleepTimerMinutes * 60_000L

    companion object {
        val BACK_STEPS = listOf(5, 10, 15, 30)
        val FORWARD_STEPS = listOf(10, 15, 30, 60)
        val CONTROLLER_TIMES = listOf(2, 4, 8, 0)
        val SLEEP_TIMES = listOf(0, 30, 60, 120, 180)

        fun safeSpeed(speed: Float): Float = speed.takeIf { it.isFinite() && it in 0.25f..3f } ?: 1f

        /** "30 мин", "1 ч", "выкл". */
        fun sleepLabel(minutes: Int): String = when {
            minutes <= 0 -> "выкл"
            minutes % 60 == 0 -> "${minutes / 60} ч"
            else -> "$minutes мин"
        }
    }
}

fun List<Int>.nextChoice(current: Int): Int = this[(indexOf(current) + 1).mod(size)]

fun <T> List<T>.nextItem(current: T): T = this[(indexOf(current) + 1).mod(size)]
