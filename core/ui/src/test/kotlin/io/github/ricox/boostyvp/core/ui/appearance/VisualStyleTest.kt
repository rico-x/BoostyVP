package io.github.ricox.boostyvp.core.ui.appearance

import org.junit.Assert.*
import org.junit.Test

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
}
