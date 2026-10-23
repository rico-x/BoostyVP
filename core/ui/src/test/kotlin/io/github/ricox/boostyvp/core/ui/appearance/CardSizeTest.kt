package io.github.ricox.boostyvp.core.ui.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSizeTest {
    @Test fun fiveOrderedSizesKeepTheOriginalAsThePenultimateDefault() {
        assertEquals(5, CardSize.entries.size)
        assertEquals(216, CardSize.Default.widthDp)
        assertEquals(3, CardSize.Default.ordinal)
        assertTrue(CardSize.entries.zipWithNext().all { (a, b) -> a.widthDp < b.widthDp })
    }

    @Test fun savedIdsAreStableAndUnknownOrMissingValuesUseTheOriginalSize() {
        CardSize.entries.forEach { assertEquals(it, CardSize.fromId(it.id)) }
        assertEquals(CardSize.Default, CardSize.fromId(null))
        assertEquals(CardSize.Default, CardSize.fromId("obsolete"))
    }

    @Test fun remoteStepsClampWithoutWrappingAtTheEnds() {
        assertEquals(CardSize.Smallest, CardSize.Smallest.step(-1))
        assertEquals(CardSize.Largest, CardSize.Largest.step(1))
        assertEquals(CardSize.Largest, CardSize.Default.step(1))
        assertEquals(CardSize.Medium, CardSize.Default.step(-1))
    }

    @Test fun eachSizeOffersADifferentCardCountInAStandardTvHomeViewport() {
        assertEquals(listOf(6, 5, 4, 3, 2), CardSize.entries.map { it.cardsPerRow(696f) })
    }

    @Test fun countIncludesGapsAndAlwaysOffersAtLeastOneCard() {
        assertEquals(1, CardSize.Default.cardsPerRow(447f))
        assertEquals(2, CardSize.Default.cardsPerRow(448f))
        assertEquals(3, CardSize.Default.cardsPerRow(680f))
        assertEquals(1, CardSize.Default.cardsPerRow(0f))
        assertEquals(1, CardSize.Default.cardsPerRow(-10f))
    }
}
