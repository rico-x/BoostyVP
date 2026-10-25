package io.github.ricox.boostyvp.localization

import io.github.ricox.boostyvp.localization.ru.Russian
import io.github.ricox.boostyvp.localization.en.English
import java.util.Locale

/** No Android/UI dependencies: errors, browser pages and enum labels use the same language. */
object Language {
    @Volatile var systemLocale: Locale = Locale.getDefault()
    @Volatile var selected: String = "system"
    val code: String get() = when(selected) { "ru", "en" -> selected; else -> if (systemLocale.language == "ru") "ru" else "en" }
    val locale: Locale get() = Locale.forLanguageTag(code)
    fun strings(code: String): Map<UiText, String> = if (code == "ru") Russian.strings else English.strings
}

fun tr(key: UiText, vararg args: Any?): String {
    require(args.size == key.arguments) { "Wrong argument count for $key" }
    return String.format(Language.locale, Language.strings(Language.code).getValue(key), *args)
}
