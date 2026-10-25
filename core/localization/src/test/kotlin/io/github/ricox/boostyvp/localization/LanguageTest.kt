package io.github.ricox.boostyvp.localization

import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import java.util.Locale

class LanguageTest {
    private val initial = Language.selected
    private val system = Language.systemLocale
    @After fun restore() { Language.selected = initial; Language.systemLocale = system }
    @Test fun `both language packs contain every key with matching arguments`() {
        for (code in listOf("ru", "en")) {
            val pack = Language.strings(code)
            assertEquals(UiText.entries.toSet(), pack.keys)
            Language.selected = code
            UiText.entries.forEach { key ->
                assertFalse("$code $key", pack.getValue(key).isBlank())
                val result = tr(key, *Array(key.arguments) { "test${it + 1}" })
                assertFalse("$code $key", Regex("%[0-9]+[$]s").containsMatchIn(result))
                if (code == "en" && key != UiText.language_settings_dialog_004)
                    assertFalse("Untranslated: $key", Regex("[А-Яа-яЁё]").containsMatchIn(result))
            }
        }
    }
    @Test fun `system default falls back to English without changing chosen language`() {
        Language.selected = "system"; Language.systemLocale = Locale.forLanguageTag("ru-RU")
        assertEquals("ru", Language.code)
        Language.systemLocale = Locale.GERMAN; assertEquals("en", Language.code)
        Language.selected = "ru"; assertEquals("ru", Language.code)
    }
    @Test fun `switching a language is immediate and arguments cannot be lost`() {
        Language.selected = "ru"; assertEquals("Настройки", tr(UiText.settings_hub_dialog_009))
        Language.selected = "en"; assertEquals("Settings", tr(UiText.settings_hub_dialog_009))
        assertEquals("Channel 2: New", tr(UiText.settings_hub_dialog_018, 2, "New"))
        assertTrue(runCatching { tr(UiText.settings_hub_dialog_018, 2) }.isFailure)
    }
}
