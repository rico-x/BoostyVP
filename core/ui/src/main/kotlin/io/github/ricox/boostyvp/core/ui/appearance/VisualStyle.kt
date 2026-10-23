package io.github.ricox.boostyvp.core.ui.appearance

enum class CardStyle(val id: String, val label: String) {
    Filled("filled", "С подложкой"), Light("light", "Обложка и подпись");
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Filled }
}

enum class FocusColor(val id: String, val label: String) {
    White("white", "Белая"), Accent("accent", "Цвет акцента");
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: White }
}

/** First is the existing spacious header; every subsequent variant takes less space. */
enum class AuthorHeaderSize(val id: String, val label: String, val paddingDp: Int, val avatarDp: Int) {
    Spacious("spacious", "Просторная · как сейчас", 24, 88),
    Reduced("reduced", "Уменьшенная", 16, 64),
    Compact("compact", "Компактная", 12, 48),
    Minimal("minimal", "Минимальная", 8, 32);
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Spacious }
}

enum class DarkTheme(val id: String, val label: String, val background: Int, val surface: Int, val raised: Int) {
    Graphite("graphite", "Графитовая", 0xFF101114.toInt(), 0xFF191A1F.toInt(), 0xFF303239.toInt()),
    Black("black", "Чёрная", 0xFF000000.toInt(), 0xFF101010.toInt(), 0xFF242424.toInt());
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Graphite }
}

enum class AccentColor(val id: String, val label: String, val argb: Int) {
    Red("red", "Красный", 0xFFD64045.toInt()),
    Blue("blue", "Синий", 0xFF6EA8FF.toInt()),
    Teal("teal", "Бирюзовый", 0xFF4EC9B0.toInt()),
    Violet("violet", "Сиреневый", 0xFFB69CFF.toInt()),
    Amber("amber", "Янтарный", 0xFFE9BB61.toInt());
    // Black text on light accents retains contrast; the original red uses white.
    val onArgb: Int get() = if (this == Red) 0xFFFFFFFF.toInt() else 0xFF101114.toInt()
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Red }
}

enum class PlayerPanelLayout(val id: String, val label: String) {
    Center("center", "Кнопки по центру"), Lower("lower", "Кнопки над шкалой");
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Center }
}

enum class PlayerTextSize(val id: String, val label: String, val timeSp: Int) {
    Normal("normal", "Обычный", 15), Large("large", "Крупный", 19), Largest("largest", "Очень крупный", 23);
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Normal }
}
