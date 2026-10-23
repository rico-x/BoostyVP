package io.github.ricox.boostyvp.core.ui.appearance

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class VisualStyleTest {
    @Test fun newDefaultsPreserveExistingLook() {
        val options = InterfaceOptions()
        assertEquals(CardStyle.Filled, options.cardStyle)
        assertEquals(3, options.focusWidth)
        assertTrue(options.focusZoom && options.animateFocus)
        assertFalse(options.fullTitle || options.playerClock || options.playerEndTime || options.playerThickSeekBar)
        assertEquals(AuthorHeaderSize.Spacious, options.authorHeader)
        assertEquals(DarkTheme.Graphite, options.theme)
        assertEquals(AccentColor.Red, options.accent)
        assertEquals(PlayerPanelLayout.Center, options.playerLayout)
        assertEquals(15, options.playerText.timeSp)
    }

    @Test fun stableStyleIdsAndUnknownValuesHaveSafeFallbacks() {
        CardStyle.entries.forEach { assertEquals(it, CardStyle.fromId(it.id)) }
        FocusColor.entries.forEach { assertEquals(it, FocusColor.fromId(it.id)) }
        AuthorHeaderSize.entries.forEach { assertEquals(it, AuthorHeaderSize.fromId(it.id)) }
        DarkTheme.entries.forEach { assertEquals(it, DarkTheme.fromId(it.id)) }
        AccentColor.entries.forEach { assertEquals(it, AccentColor.fromId(it.id)) }
        PlayerPanelLayout.entries.forEach { assertEquals(it, PlayerPanelLayout.fromId(it.id)) }
        PlayerTextSize.entries.forEach { assertEquals(it, PlayerTextSize.fromId(it.id)) }
        assertEquals(CardStyle.Filled, CardStyle.fromId("unknown"))
        assertEquals(FocusColor.White, FocusColor.fromId(null))
        assertEquals(AuthorHeaderSize.Spacious, AuthorHeaderSize.fromId("unknown"))
        assertEquals(DarkTheme.Graphite, DarkTheme.fromId(null))
        assertEquals(AccentColor.Red, AccentColor.fromId("unknown"))
        assertEquals(PlayerPanelLayout.Center, PlayerPanelLayout.fromId(null))
        assertEquals(PlayerTextSize.Normal, PlayerTextSize.fromId("unknown"))
    }

    @Test fun authorPresetsAreProgressivelyDenserAndTimeSizesLarger() {
        assertEquals(4, AuthorHeaderSize.entries.size)
        assertTrue(AuthorHeaderSize.entries.zipWithNext().all { (a, b) -> a.paddingDp > b.paddingDp && a.avatarDp > b.avatarDp })
        assertTrue(PlayerTextSize.entries.zipWithNext().all { (a, b) -> a.timeSp < b.timeSp })
    }

    @Test fun corruptFrameThicknessNeverHidesOrOversizesTheFocus() {
        assertEquals(3, InterfaceOptions(focusWidth = 0).normalized().focusWidth)
        assertEquals(3, InterfaceOptions(focusWidth = Int.MAX_VALUE).normalized().focusWidth)
        (1..4).forEach { assertEquals(it, InterfaceOptions(focusWidth = it).normalized().focusWidth) }
    }

    @Test fun textAccentsHaveContrastOnEveryDarkSurfaceWithoutChangingBrandFills() {
        fun luminance(argb: Int): Double {
            val rgb = listOf(16, 8, 0).map { ((argb ushr it) and 255) / 255.0 }
                .map { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
            return rgb.zip(listOf(0.2126, 0.7152, 0.0722)).sumOf { (a, b) -> a * b }
        }
        DarkTheme.entries.forEach { theme -> AccentColor.entries.forEach { accent ->
            listOf(theme.background, theme.surface, theme.raised).forEach { background ->
                val a = luminance(accent.textArgb); val b = luminance(background)
                assertTrue("${accent.id} on ${theme.id}", (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05) >= 4.5)
            }
        } }
        assertEquals(0xFFD64045.toInt(), AccentColor.Red.argb)
    }

    @Test fun independentMetadataRetainsLegacySizesUntilExplicitlySelected() {
        CardTextSize.entries.forEach { title ->
            assertEquals(title.metadataSp, InterfaceOptions(textSize = title).metadataSp)
            assertEquals(12, InterfaceOptions(textSize = title, metadataSize = CardMetadataSize.Normal).metadataSp)
        }
        assertEquals(CardMetadataSize.FollowTitle, CardMetadataSize.fromId("future_unknown"))
        CardMetadataSize.entries.forEach { assertEquals(it, CardMetadataSize.fromId(it.id)) }
    }
}
