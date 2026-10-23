package io.github.ricox.boostyvp.feature.player

import io.github.ricox.boostyvp.core.model.ContentBlock

internal sealed interface ReaderPart {
    data class Paragraph(val text: String, val heading: Boolean = false) : ReaderPart
    data class Image(val url: String, val ratio: Float) : ReaderPart
}

/** Text runs and links are inline; BLOCK_END, not each run, ends a paragraph. */
internal fun readerParts(blocks: List<ContentBlock>): List<ReaderPart> = buildList {
    val text = StringBuilder()
    fun flush() {
        if (text.isNotBlank()) add(ReaderPart.Paragraph(text.toString().trim()))
        text.clear()
    }
    for (block in blocks) when (block) {
        is ContentBlock.Text -> when (block.modificator) {
            "BLOCK_END" -> { text.append(block.text); flush() }
            "HEADER" -> { flush(); if (block.text.isNotBlank()) add(ReaderPart.Paragraph(block.text, true)) }
            else -> text.append(block.text)
        }
        is ContentBlock.Link -> {
            text.append(block.text?.takeIf { it.isNotBlank() } ?: block.url)
            if (!block.text.isNullOrBlank() && block.text != block.url && block.url.isNotBlank()) {
                text.append(" (${block.url})")
            }
        }
        is ContentBlock.Image -> {
            flush()
            if (block.url.startsWith("https://") || block.url.startsWith("http://")) add(ReaderPart.Image(
                block.url, if ((block.width ?: 0) > 0 && (block.height ?: 0) > 0)
                    (block.width!!.toFloat() / block.height!!).coerceIn(0.5f, 3f) else 16f / 9f))
        }
        is ContentBlock.File -> { flush(); add(ReaderPart.Paragraph("Файл: ${block.title ?: "вложение"}")) }
        is ContentBlock.Audio -> { flush(); add(ReaderPart.Paragraph("Аудио: ${block.title ?: "аудиозапись"}")) }
        else -> Unit
    }
    flush()
}
