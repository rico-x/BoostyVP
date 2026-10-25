package io.github.ricox.boostyvp.core.ui.appearance

import org.junit.Assert.*
import org.junit.Test

class TvChannelOptionsTest {
    @Test fun `default is exactly one new channel without system watch next`() {
        val value = TvChannelOptions().normalized()
        assertTrue(value.enabled); assertEquals(1, value.count)
        assertEquals(TvFeedSource.New, value.sources.first()); assertFalse(value.watchNext)
    }
    @Test fun `source change swaps slots rather than publishing duplicates`() {
        val original = TvChannelOptions(count = 3)
        val changed = original.changeSource(0).normalized()
        assertEquals(TvFeedSource.Continue, changed.sources.first())
        assertEquals(TvFeedSource.New, changed.sources[1])
        assertEquals(5, changed.sources.distinct().size)
    }
    @Test fun `imported options are bounded and missing sources are filled`() {
        val safe = TvChannelOptions(count = 200, cards = -10, sources = listOf(TvFeedSource.Later, TvFeedSource.Later)).normalized()
        assertEquals(5, safe.count); assertEquals(5, safe.cards)
        assertEquals(5, safe.sources.size); assertEquals(TvFeedSource.Later, safe.sources.first())
    }
}
