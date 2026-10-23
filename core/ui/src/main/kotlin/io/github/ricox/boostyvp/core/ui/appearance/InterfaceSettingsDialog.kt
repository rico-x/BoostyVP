package io.github.ricox.boostyvp.core.ui.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.model.ContentBlock
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.VideoSource
import io.github.ricox.boostyvp.core.ui.component.PostCard
import io.github.ricox.boostyvp.core.ui.component.TvActionButton
import io.github.ricox.boostyvp.core.ui.theme.Overscan
import kotlin.math.roundToInt

/** The visual settings group. The player settings and library remain independent. */
@Composable
fun InterfaceSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { InterfacePreferences(context) }
    val cardPreferences = remember(context) { CardSizePreferences(context) }
    val options = LocalInterfaceOptions.current
    val size = LocalCardSize.current
    var showCardSize by rememberSaveable { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Overscan.Horizontal, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Визуальные улучшения", style = MaterialTheme.typography.headlineSmall)
            Text("↑ ↓ — пункт · ← → или OK — значение · Назад — закрыть. Сохраняется автоматически.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LazyColumn(Modifier.weight(1f).testTag("interface_settings_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                    item(key = "card_size") {
                        TvActionButton("Размер карточек: ${size.label}", { showCardSize = true },
                            Modifier.fillMaxWidth().focusRequester(firstFocus).testTag("interface_card_size"))
                    }
                    item(key = "sidebar") {
                        InterfaceChoice("Сворачивать меню", listOf("Нет", "Да"), if (options.autoCollapseSidebar) 1 else 0,
                            { preferences.save(options.copy(autoCollapseSidebar = it == 1)) }, "interface_sidebar")
                    }
                    item(key = "text_size") {
                        InterfaceChoice("Текст карточек", CardTextSize.entries.map { it.label }, options.textSize.ordinal,
                            { preferences.save(options.copy(textSize = CardTextSize.entries[it])) }, "interface_text_size")
                    }
                    item(key = "title_lines") {
                        InterfaceChoice("Строк названия", listOf("1", "2", "3"), options.titleLines - 1,
                            { preferences.save(options.copy(titleLines = it + 1)) }, "interface_title_lines")
                    }
                    item(key = "density") {
                        InterfaceChoice("Плотность", CatalogDensity.entries.map { it.label }, options.density.ordinal,
                            { preferences.save(options.copy(density = CatalogDensity.entries[it])) }, "interface_density")
                    }
                    item(key = "author") {
                        InterfaceChoice("Имя автора", listOf("Скрывать", "Показывать"), if (options.showAuthor) 1 else 0,
                            { preferences.save(options.copy(showAuthor = it == 1)) }, "interface_author")
                    }
                    item(key = "repeat_author") {
                        InterfaceChoice("Повтор имени в ряду автора", listOf("Скрывать", "Показывать"),
                            if (options.hideRepeatedAuthor) 0 else 1,
                            { preferences.save(options.copy(hideRepeatedAuthor = it == 0)) }, "interface_repeat_author")
                    }
                    item(key = "date") {
                        InterfaceChoice("Дата публикации", listOf("Скрывать", "Показывать"), if (options.showDate) 1 else 0,
                            { preferences.save(options.copy(showDate = it == 1)) }, "interface_date")
                    }
                    item(key = "duration") {
                        InterfaceChoice("Длительность видео", listOf("Скрывать", "Показывать"), if (options.showDuration) 1 else 0,
                            { preferences.save(options.copy(showDuration = it == 1)) }, "interface_duration")
                    }
                }
                Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ряд автора «Автор примера»", style = MaterialTheme.typography.titleMedium)
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        PostCard(PreviewPost, {}, Modifier.focusProperties { canFocus = false }
                            .testTag("interface_preview"), inAuthorRow = true)
                    }
                    Text("Текст не зависит от размера обложки.\nПлотность меняет отступы, а не размер шрифта.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TvActionButton("Готово", onDismiss, primary = true)
            }
        }
        LaunchedEffect(Unit) { withFrameNanos { }; firstFocus.requestFocus() }
    }
    // Keep the parent dialog/focus target attached while the existing size bar is open.
    if (showCardSize) CardSizeSettingsDialog(size, cardPreferences::save, { showCardSize = false })
}

/** One focus target per option; horizontal keys select without jumping into the preview. */
@Composable
private fun InterfaceChoice(
    title: String,
    values: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    tag: String,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Button(onClick = { onSelect((selected + 1) % values.size) },
        modifier = Modifier.fillMaxWidth().testTag(tag)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
                    if (event.type == KeyEventType.KeyDown) {
                        val next = (selected + if (event.key == Key.DirectionLeft) -1 else 1).coerceIn(values.indices)
                        if (next != selected) onSelect(next)
                    }
                    true
                } else false
            }.semantics {
                stateDescription = values[selected]
                progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..values.lastIndex.toFloat(),
                    steps = (values.size - 2).coerceAtLeast(0))
                setProgress { value ->
                    if (!value.isFinite()) false else {
                        onSelect(value.roundToInt().coerceIn(values.indices)); true
                    }
                }
            },
        shape = ButtonDefaults.shape(shape),
        colors = ButtonDefaults.colors(containerColor = colors.surfaceVariant, contentColor = colors.onSurface,
            focusedContainerColor = colors.surfaceVariant, focusedContentColor = colors.onSurface),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, colors.onSurface), shape = shape)),
        scale = ButtonDefaults.scale(focusedScale = 1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(values[selected], style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                values.indices.forEach { index ->
                    Box(Modifier.weight(1f).height(3.dp).background(
                        if (index <= selected) colors.primary else colors.border, shape))
                }
            }
        }
    }
}

// Only a synthetic thumbnail placeholder; no URL is fetched or passed to a player.
private val PreviewPost = Post("interface_preview", 0, "Длинное название видео: проверяем удобство чтения с дивана",
    "preview", "Автор примера", 1_700_000_000, true, false, false, false, null,
    listOf(ContentBlock.Video("preview", null, null, 754, 1920, 1080, null, null, null,
        listOf(VideoSource(VideoSource.Kind.Progressive, "about:blank", 1080, "preview")), true)), emptyList())
