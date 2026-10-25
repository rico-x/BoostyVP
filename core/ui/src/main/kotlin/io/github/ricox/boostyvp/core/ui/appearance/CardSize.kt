package io.github.ricox.boostyvp.core.ui.appearance

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

/** Stable preference IDs, not enum ordinals: future presets must not change saved choices. */
enum class CardSize(
    val id: String,
    private val labelText: () -> String,
    val widthDp: Int,
    val titleSp: Int,
    val lineHeightSp: Int,
    val paddingDp: Int,
) {
    Smallest("smallest", { tr(UiText.card_size_001) }, 96, 12, 16, 8),
    Small("small", { tr(UiText.card_size_002) }, 124, 13, 18, 8),
    Medium("medium", { tr(UiText.card_size_003) }, 160, 14, 20, 10),
    Large("large", { tr(UiText.card_size_004) }, 216, 16, 22, 12),
    Largest("largest", { tr(UiText.card_size_005) }, 296, 18, 24, 12);
    val label: String get() = labelText()


    fun step(delta: Int): CardSize = entries[(ordinal + delta).coerceIn(0, entries.lastIndex)]

    /** Same spacing and rounding as the catalog's adaptive grid; counts fully visible row cards. */
    fun cardsPerRow(availableWidthDp: Float, gapDp: Int = GAP_DP): Int =
        ((availableWidthDp.coerceAtLeast(0f) + gapDp) / (widthDp + gapDp)).toInt().coerceAtLeast(1)

    companion object {
        val Default = Large
        const val GAP_DP = 16
        fun fromId(id: String?): CardSize = entries.firstOrNull { it.id == id } ?: Default
    }
}
