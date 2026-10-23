package io.github.ricox.boostyvp.core.ui.appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.component.PostCard
import io.github.ricox.boostyvp.core.ui.component.TvActionButton
import io.github.ricox.boostyvp.core.ui.theme.Overscan
import kotlin.math.roundToInt

@Composable
fun CardSizeSettingsDialog(
    size: CardSize,
    onChange: (CardSize) -> Unit,
    onDismiss: () -> Unit,
) {
    val sliderFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Match the real content widths (sidebar/overscan/padding), not the preview panel width.
            val homeWidth = (maxWidth - 192.dp - 24.dp * 2).coerceAtLeast(1.dp)
            val newWidth = (maxWidth - 192.dp - 24.dp - Overscan.Horizontal).coerceAtLeast(1.dp)
            val blogWidth = (maxWidth - Overscan.Horizontal * 2).coerceAtLeast(1.dp)
            Column(Modifier.fillMaxSize().padding(horizontal = Overscan.Horizontal, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Размер карточек", style = MaterialTheme.typography.headlineSmall)
                    Text("${size.ordinal + 1} / ${CardSize.entries.size} · ${size.label}",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text("← → меняют размер · OK или Назад — закрыть. Выбор сохраняется автоматически.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CardSizeSlider(size, onChange, onDismiss, Modifier.focusRequester(sliderFocus))
                Text("Карточек в строке: подписки — ${size.cardsPerRow(homeWidth.value)} · " +
                    "«Новое» — ${size.cardsPerRow(newWidth.value)} · автор — ${size.cardsPerRow(blogWidth.value)}",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("card_size_count"))
                // Real card layout, no account data, image downloads or extra remote focus stops.
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalCardSize provides size) {
                        LazyRow(Modifier.width(homeWidth), userScrollEnabled = false,
                            horizontalArrangement = Arrangement.spacedBy(CardSize.GAP_DP.dp)) {
                            itemsIndexed(PreviewPosts) { index, post ->
                                PostCard(post, {}, Modifier.focusProperties { canFocus = false }
                                    .testTag("card_size_preview_$index"))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Общий размер для всех лент. Обложки сохраняют пропорции 16:9.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    TvActionButton("Как раньше", { onChange(CardSize.Default) })
                    TvActionButton("Готово", onDismiss, primary = true)
                }
            }
            // BoxWithConstraints subcomposes its children during measurement. Request only after
            // that subtree has attached; an effect outside it can run before the slider exists.
            LaunchedEffect(Unit) {
                withFrameNanos { }
                sliderFocus.requestFocus()
            }
        }
    }
}

/** A single remote focus stop: Left/Right change one notch and never escape at either end. */
@Composable
private fun CardSizeSlider(
    size: CardSize,
    onChange: (CardSize) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().height(56.dp)
        .onFocusChanged { focused = it.isFocused }
        .border(if (focused) 2.dp else 1.dp, if (focused) colors.onSurface else colors.border,
            RoundedCornerShape(12.dp))
        .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
        .onPreviewKeyEvent { event ->
            when (event.key) {
                Key.DirectionLeft, Key.DirectionRight -> {
                    if (event.type == KeyEventType.KeyDown) {
                        val next = size.step(if (event.key == Key.DirectionLeft) -1 else 1)
                        if (next != size) onChange(next)
                    }
                    true
                }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    if (event.type == KeyEventType.KeyUp) onConfirm()
                    true
                }
                else -> false // Up/Down leave the bar; Back dismisses the dialog normally.
            }
        }
        .semantics {
            contentDescription = "Размер карточек"
            stateDescription = "${size.label}, ${size.ordinal + 1} из ${CardSize.entries.size}"
            progressBarRangeInfo = ProgressBarRangeInfo(size.ordinal.toFloat(),
                0f..CardSize.entries.lastIndex.toFloat(), steps = CardSize.entries.size - 2)
            setProgress { value ->
                if (!value.isFinite()) false else {
                    onChange(CardSize.entries[value.roundToInt().coerceIn(0, CardSize.entries.lastIndex)])
                    true
                }
            }
        }
        .testTag("card_size_slider").focusable().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Мельче", style = MaterialTheme.typography.labelLarge)
        Canvas(Modifier.weight(1f).height(40.dp).pointerInput(size) {
            detectTapGestures { point ->
                val inset = 10.dp.toPx()
                val width = (this.size.width - inset * 2).coerceAtLeast(1f)
                val index = ((point.x - inset) / width * CardSize.entries.lastIndex).roundToInt()
                    .coerceIn(0, CardSize.entries.lastIndex)
                onChange(CardSize.entries[index])
            }
        }) {
            val inset = 10.dp.toPx()
            val start = Offset(inset, this.size.height / 2)
            val stepWidth = (this.size.width - inset * 2) / CardSize.entries.lastIndex
            val thumb = start.copy(x = start.x + stepWidth * size.ordinal)
            drawLine(colors.border, start, start.copy(x = this.size.width - inset), 4.dp.toPx(), StrokeCap.Round)
            drawLine(colors.primary, start, thumb, 4.dp.toPx(), StrokeCap.Round)
            CardSize.entries.indices.forEach { index ->
                drawCircle(if (index <= size.ordinal) colors.primary else colors.onSurfaceVariant,
                    4.dp.toPx(), start.copy(x = start.x + index * stepWidth))
            }
            drawCircle(colors.onSurface, 10.dp.toPx(), thumb)
            drawCircle(colors.primary, 6.dp.toPx(), thumb)
        }
        Text("Крупнее", style = MaterialTheme.typography.labelLarge)
    }
}

private val PreviewPosts = (1..10).map { index ->
    Post("preview_$index", index.toLong(), "Название видео $index", "preview", "Автор", 0,
        true, false, false, false, null,
        listOf(ContentBlock.Video("preview_$index", null, null, 0, 1920, 1080, null, null, null,
            emptyList(), true)), emptyList())
}
