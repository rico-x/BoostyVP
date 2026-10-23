package io.github.ricox.boostyvp.core.ui.appearance

/** Stable preference IDs, not enum ordinals: future presets must not change saved choices. */
enum class CardSize(
    val id: String,
    val label: String,
    val widthDp: Int,
    val titleSp: Int,
    val lineHeightSp: Int,
    val paddingDp: Int,
) {
    Smallest("smallest", "Минимальный", 96, 12, 16, 8),
    Small("small", "Маленький", 124, 13, 18, 8),
    Medium("medium", "Средний", 160, 14, 20, 10),
    Large("large", "Крупный · как раньше", 216, 16, 22, 12),
    Largest("largest", "Максимальный", 296, 18, 24, 12);

    fun step(delta: Int): CardSize = entries[(ordinal + delta).coerceIn(0, entries.lastIndex)]

    /** Same spacing and rounding as the catalog's adaptive grid; counts fully visible row cards. */
    fun cardsPerRow(availableWidthDp: Float): Int =
        ((availableWidthDp.coerceAtLeast(0f) + GAP_DP) / (widthDp + GAP_DP)).toInt().coerceAtLeast(1)

    companion object {
        val Default = Large
        const val GAP_DP = 16
        fun fromId(id: String?): CardSize = entries.firstOrNull { it.id == id } ?: Default
    }
}
