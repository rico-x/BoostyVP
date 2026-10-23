package io.github.ricox.boostyvp.core.ui.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceOptionsTest {
    @Test fun defaultKeepsNormalTextAndSpacingWithOptionalAutoCollapse() {
        val options = InterfaceOptions()
        assertEquals(16, options.textSize.titleSp)
        assertEquals(2, options.titleLines)
        assertEquals(16, options.density.horizontalGapDp)
        assertTrue(options.autoCollapseSidebar)
    }

    @Test fun invalidLineCountsUseTwoInsteadOfBreakingTextLayout() {
        assertEquals(2, InterfaceOptions(titleLines = 0).normalized().titleLines)
        assertEquals(2, InterfaceOptions(titleLines = Int.MAX_VALUE).normalized().titleLines)
        (1..3).forEach { assertEquals(it, InterfaceOptions(titleLines = it).normalized().titleLines) }
    }

    @Test fun stableIdsAndFallbacksDoNotDependOnEnumOrder() {
        CardTextSize.entries.forEach { assertEquals(it, CardTextSize.fromId(it.id)) }
        CatalogDensity.entries.forEach { assertEquals(it, CatalogDensity.fromId(it.id)) }
        assertEquals(CardTextSize.Normal, CardTextSize.fromId(null))
        assertEquals(CardTextSize.Normal, CardTextSize.fromId("unknown"))
        assertEquals(CatalogDensity.Normal, CatalogDensity.fromId(null))
        assertEquals(CatalogDensity.Normal, CatalogDensity.fromId("unknown"))
    }

    @Test fun hideRepeatedAuthorNeverHidesAuthorsFromMixedRows() {
        val options = InterfaceOptions(hideRepeatedAuthor = true)
        assertFalse(options.showsAuthor(inAuthorRow = true))
        assertTrue(options.showsAuthor(inAuthorRow = false))
        assertFalse(options.copy(showAuthor = false).showsAuthor(inAuthorRow = false))
        assertTrue(InterfaceOptions().showsAuthor(inAuthorRow = true))
    }

    @Test fun densityChangesOnlySpacingAndCountIncludesTheSelectedGap() {
        assertTrue(CatalogDensity.entries.zipWithNext().all { (a, b) ->
            a.horizontalGapDp < b.horizontalGapDp && a.gridGapDp < b.gridGapDp && a.rowGapDp < b.rowGapDp
        })
        assertEquals(3, CardSize.Large.cardsPerRow(680f, CatalogDensity.Compact.horizontalGapDp))
        assertEquals(2, CardSize.Large.cardsPerRow(680f, CatalogDensity.Spacious.horizontalGapDp))
    }

    @Test fun collapsedMenuRetainsOverscanAndAFullButtonTarget() {
        assertEquals(192, BrowseDimensions.contentSidebarWidth(InterfaceOptions(autoCollapseSidebar = false)))
        assertEquals(112, BrowseDimensions.contentSidebarWidth(InterfaceOptions(autoCollapseSidebar = true)))
        assertTrue(BrowseDimensions.CollapsedSidebarDp >= 48 + 48 + 16)
    }
}
