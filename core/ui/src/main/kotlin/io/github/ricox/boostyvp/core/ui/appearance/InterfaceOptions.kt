package io.github.ricox.boostyvp.core.ui.appearance

/** Presentation only: no account, library, playback or network state. */
data class InterfaceOptions(
    val autoCollapseSidebar: Boolean = true,
    val textSize: CardTextSize = CardTextSize.Normal,
    val titleLines: Int = 2,
    val showAuthor: Boolean = true,
    val showDate: Boolean = true,
    val showDuration: Boolean = true,
    val hideRepeatedAuthor: Boolean = false,
    val density: CatalogDensity = CatalogDensity.Normal,
) {
    fun normalized() = copy(titleLines = titleLines.takeIf { it in 1..3 } ?: 2)
    fun showsAuthor(inAuthorRow: Boolean) = showAuthor && !(inAuthorRow && hideRepeatedAuthor)
}

/** Card text stays the same size when the thumbnail size changes. */
enum class CardTextSize(val id: String, val label: String, val titleSp: Int, val metadataSp: Int) {
    Smallest("smallest", "Мелкий", 12, 10),
    Small("small", "Небольшой", 14, 11),
    Normal("normal", "Обычный", 16, 12),
    Large("large", "Крупный", 18, 14),
    Largest("largest", "Очень крупный", 20, 16);

    companion object {
        fun fromId(id: String?): CardTextSize = entries.firstOrNull { it.id == id } ?: Normal
    }
}

enum class CatalogDensity(
    val id: String,
    val label: String,
    val horizontalGapDp: Int,
    val gridGapDp: Int,
    val rowGapDp: Int,
    val rowPaddingDp: Int,
    val headingPaddingDp: Int,
    val avatarDp: Int,
    val cardPaddingDeltaDp: Int,
) {
    Compact("compact", "Компактная", 8, 12, 4, 6, 4, 28, -2),
    Normal("normal", "Обычная", 16, 20, 12, 10, 6, 36, 0),
    Spacious("spacious", "Свободная", 24, 28, 20, 14, 10, 44, 2);

    companion object {
        fun fromId(id: String?): CatalogDensity = entries.firstOrNull { it.id == id } ?: Normal
    }
}

/** Shared by the real sidebar and card-count preview. Keep the TV overscan margin intact. */
object BrowseDimensions {
    const val ExpandedSidebarDp = 192
    const val CollapsedSidebarDp = 112
    fun contentSidebarWidth(options: InterfaceOptions): Int =
        if (options.autoCollapseSidebar) CollapsedSidebarDp else ExpandedSidebarDp
}
