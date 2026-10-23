package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.core.model.ContentBlock
import org.junit.Assert.*
import org.junit.Test

class ReaderPartsTest {
    @Test fun `inline text runs are joined and paragraph ends are respected`() {
        val result = readerParts(listOf(ContentBlock.Text("Hello ", null), ContentBlock.Text("world", null),
            ContentBlock.Text("", "BLOCK_END"), ContentBlock.Text("Next", null)))
        assertEquals(listOf(ReaderPart.Paragraph("Hello world"), ReaderPart.Paragraph("Next")), result)
    }
    @Test fun `heading and link text remain readable without executing markup`() {
        val result = readerParts(listOf(ContentBlock.Text("Heading", "HEADER"), ContentBlock.Link("https://example.org", "Source")))
        assertEquals(listOf(ReaderPart.Paragraph("Heading", true), ReaderPart.Paragraph("Source (https://example.org)")), result)
    }
    @Test fun `invalid local image references cannot load device files`() {
        assertTrue(readerParts(listOf(ContentBlock.Image("x", "file:///data/private", 0, 0))).isEmpty())
    }
    @Test fun `long paragraphs are not truncated`() {
        val text = "Long paragraph. ".repeat(2000)
        assertEquals(text.trim(), (readerParts(listOf(ContentBlock.Text(text, null))).single() as ReaderPart.Paragraph).text)
    }
}
