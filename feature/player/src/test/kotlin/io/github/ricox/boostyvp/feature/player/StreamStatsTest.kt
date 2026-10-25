package io.github.ricox.boostyvp.feature.player

import org.junit.Assert.*
import org.junit.Test

class StreamStatsTest {
    private val language = io.github.ricox.boostyvp.localization.Language.selected
    @org.junit.Before fun chooseRussian() { io.github.ricox.boostyvp.localization.Language.selected = "ru" }
    @org.junit.After fun restoreLanguage() { io.github.ricox.boostyvp.localization.Language.selected = language }
    @Test fun missingMetadataIsNotInventedAndNegativeCountersAreClamped() {
        val text = StreamStats(bufferMs = -1, dropped = -1, rendered = -1).text()
        assertTrue(text.contains("Видео: нет данных"))
        assertTrue(text.contains("Битрейт видео: нет данных"))
        assertTrue(text.contains("Буфер: 0.0 с"))
        assertTrue(text.contains("Пропущено кадров: 0"))
        assertFalse(text.contains("-1"))
    }
    @Test fun realFormatAndDecoderCountersAreShownWithoutAnyNetworkAddress() {
        val text = StreamStats(3840, 2160, 59.94f, "video/av01", "av01.0.12M.10", 12_000_000,
            "c2.vendor.av1.decoder", "audio/mp4a-latm", 12500, 3, 400).text()
        listOf("3840 × 2160", "59.94", "av01.0.12M.10", "12.00 Мбит/с", "c2.vendor.av1.decoder",
            "12.5 с", "Пропущено кадров: 3", "выведено: 400").forEach { assertTrue(text, text.contains(it)) }
        assertFalse(text.contains("http"))
    }
}
