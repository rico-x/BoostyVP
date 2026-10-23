package io.github.ricox.boostyvp.core.ui.player

/** Non-secret presentation preferences, independent of the Boosty API. */
data class PlayerOptions(
    val seekBackSeconds: Int = 10,
    val seekForwardSeconds: Int = 30,
    val controllerSeconds: Int = 4,
    val rememberSpeed: Boolean = true,
) {
    fun normalized() = copy(
        seekBackSeconds = seekBackSeconds.takeIf { it in BACK_STEPS } ?: 10,
        seekForwardSeconds = seekForwardSeconds.takeIf { it in FORWARD_STEPS } ?: 30,
        controllerSeconds = controllerSeconds.takeIf { it in CONTROLLER_TIMES } ?: 4,
    )

    val controllerTimeoutMs: Int get() = controllerSeconds * 1_000

    companion object {
        val BACK_STEPS = listOf(5, 10, 15, 30)
        val FORWARD_STEPS = listOf(10, 15, 30, 60)
        val CONTROLLER_TIMES = listOf(2, 4, 8, 0)

        fun safeSpeed(speed: Float): Float = speed.takeIf { it.isFinite() && it in 0.25f..3f } ?: 1f
    }
}

fun List<Int>.nextChoice(current: Int): Int = this[(indexOf(current) + 1).mod(size)]
