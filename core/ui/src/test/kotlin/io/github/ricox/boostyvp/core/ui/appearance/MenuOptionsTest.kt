package io.github.ricox.boostyvp.core.ui.appearance

import org.junit.Assert.*
import org.junit.Test

class MenuOptionsTest {
    @org.junit.Test fun expandedSidebarAlwaysReservesItsActualWidth() {
        for (width in listOf(192, 224, 256)) {
            val menu = MenuOptions(widthDp = width)
            org.junit.Assert.assertEquals(width, BrowseDimensions.contentSidebarWidth(InterfaceOptions(), menu, expanded = true))
            org.junit.Assert.assertEquals(112, BrowseDimensions.contentSidebarWidth(InterfaceOptions(), menu, expanded = false))
            org.junit.Assert.assertEquals(width, BrowseDimensions.contentSidebarWidth(
                InterfaceOptions(autoCollapseSidebar = false), menu, expanded = false))
        }
    }
    @Test fun `preset labels are not frozen when the language changes`() {
        val old = io.github.ricox.boostyvp.localization.Language.selected
        try {
            io.github.ricox.boostyvp.localization.Language.selected = "ru"
            assertEquals("Крупный", CardSize.Large.label)
            io.github.ricox.boostyvp.localization.Language.selected = "en"
            assertEquals("Large", CardSize.Large.label)
            assertEquals("Fit", io.github.ricox.boostyvp.core.ui.player.VideoScale.Fit.label)
        } finally { io.github.ricox.boostyvp.localization.Language.selected = old }
    }
    @Test fun `corrupt preferences cannot hide the home or lose a destination`() {
        val value = MenuOptions(textSp = 1, widthDp = 10, order = listOf("invalid", "new", "new"),
            hidden = setOf("subscriptions", "new", "invalid")).normalized()
        assertEquals(MenuOptions.DefaultOrder.toSet(), value.order.toSet())
        assertEquals(value.order.distinct(), value.order)
        assertEquals(setOf("new"), value.hidden)
        assertEquals(14, value.textSp); assertEquals(224, value.widthDp)
    }
    @Test fun `reordering at either edge never loses or duplicates an item`() {
        var value = MenuOptions()
        value = value.move("subscriptions", -1); assertEquals("subscriptions", value.order.first())
        repeat(30) { value = value.move("queue", -1) }
        assertEquals("queue", value.order.first())
        assertEquals(MenuOptions.DefaultOrder.toSet(), value.order.toSet())
    }
    @Test fun `menu density and text size are independent of card layout`() {
        val compact = MenuOptions(density = MenuDensity.Tight, textSp = 18)
        assertEquals(32, compact.density.heightDp); assertEquals(18, compact.normalized().textSp)
        assertEquals(112, BrowseDimensions.contentSidebarWidth(InterfaceOptions(), compact))
        assertEquals(256, BrowseDimensions.contentSidebarWidth(InterfaceOptions(autoCollapseSidebar = false), compact.copy(widthDp = 256)))
    }
}
