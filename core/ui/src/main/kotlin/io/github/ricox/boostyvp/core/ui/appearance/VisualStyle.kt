package io.github.ricox.boostyvp.core.ui.appearance

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

enum class CardStyle(val id: String, private val labelText: () -> String) {
    Filled("filled", { tr(UiText.visual_style_001) }), Light("light", { tr(UiText.visual_style_002) });
    val label: String get() = labelText()

    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Filled }
}

enum class FocusColor(val id: String, private val labelText: () -> String) {
    White("white", { tr(UiText.visual_style_003) }), Accent("accent", { tr(UiText.visual_style_004) });
    val label: String get() = labelText()

    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: White }
}

/** First is the existing spacious header; every subsequent variant takes less space. */
enum class AuthorHeaderSize(val id: String, private val labelText: () -> String, val paddingDp: Int, val avatarDp: Int) {
    Spacious("spacious", { tr(UiText.visual_style_005) }, 24, 88),
    Reduced("reduced", { tr(UiText.visual_style_006) }, 16, 64),
    Compact("compact", { tr(UiText.interface_options_006) }, 12, 48),
    Minimal("minimal", { tr(UiText.visual_style_007) }, 8, 32);
    val label: String get() = labelText()

    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Spacious }
}

enum class DarkTheme(val id: String, private val labelText: () -> String, val background: Int, val surface: Int, val raised: Int) {
    Graphite("graphite", { tr(UiText.visual_style_008) }, 0xFF101114.toInt(), 0xFF191A1F.toInt(), 0xFF303239.toInt()),
    Black("black", { tr(UiText.visual_style_009) }, 0xFF000000.toInt(), 0xFF101010.toInt(), 0xFF242424.toInt());
    val label: String get() = labelText()

    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Graphite }
}

enum class AccentColor(val id: String, private val labelText: () -> String, val argb: Int) {
    Red("red", { tr(UiText.visual_style_010) }, 0xFFD64045.toInt()),
    Blue("blue", { tr(UiText.visual_style_011) }, 0xFF6EA8FF.toInt()),
    Teal("teal", { tr(UiText.visual_style_012) }, 0xFF4EC9B0.toInt()),
    Violet("violet", { tr(UiText.visual_style_013) }, 0xFFB69CFF.toInt()),
    Amber("amber", { tr(UiText.visual_style_014) }, 0xFFE9BB61.toInt());
    val label: String get() = labelText()

    // Black text on light accents retains contrast; the original red uses white.
    val onArgb: Int get() = if (this == Red) 0xFFFFFFFF.toInt() else 0xFF101114.toInt()
    /** Small text / focus outlines need more contrast than a brand-colored fill. */
    val textArgb: Int get() = if (this == Red) 0xFFFF7C82.toInt() else argb
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Red }
}

enum class PlayerPanelLayout(val id: String, private val labelText: () -> String) {
    Center("center", { tr(UiText.visual_style_015) }), Lower("lower", { tr(UiText.visual_style_016) });
    val label: String get() = labelText()

    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Center }
}

enum class PlayerTextSize(val id: String, private val labelText: () -> String, val timeSp: Int) {
    Normal("normal", { tr(UiText.interface_options_004) }, 15), Large("large", { tr(UiText.card_size_004) }, 19), Largest("largest", { tr(UiText.interface_options_005) }, 23);
    val label: String get() = labelText()

    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Normal }
}

/** Shared geometry for the native player and its non-playing settings preview. */
object PlayerPanelMetrics {
    const val LowerTransportMarginDp = 108
    const val ProgressBottomDp = 88
    const val TitleSp = 24
    const val ClockSp = 14
    const val EndTimeSp = 12
}
