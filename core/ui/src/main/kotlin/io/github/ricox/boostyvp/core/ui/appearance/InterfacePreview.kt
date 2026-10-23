package io.github.ricox.boostyvp.core.ui.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.model.*
import io.github.ricox.boostyvp.core.ui.component.AuthorHeader
import io.github.ricox.boostyvp.core.ui.component.PostCard
import io.github.ricox.boostyvp.core.ui.theme.LocalAccentText

@Composable
internal fun InterfacePreview(section: InterfaceSection, modifier: Modifier = Modifier) {
    val o = LocalInterfaceOptions.current
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(when (section) {
            InterfaceSection.Catalog -> "Карточки в каталоге"
            InterfaceSection.Text -> "Название и подписи"
            InterfaceSection.Focus -> "Выделенная карточка"
            InterfaceSection.Author -> "Страница автора"
            InterfaceSection.Theme -> "Тема интерфейса"
            InterfaceSection.Player -> "Панель плеера"
        }, style = MaterialTheme.typography.titleMedium)
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds(), contentAlignment = Alignment.Center) {
            when (section) {
                InterfaceSection.Catalog, InterfaceSection.Text -> LazyRow(userScrollEnabled = false,
                    horizontalArrangement = Arrangement.spacedBy(o.density.horizontalGapDp.dp)) {
                    items(2) { index -> PostCard(PreviewPost.copy(id = "preview_$index"), {},
                        Modifier.focusProperties { canFocus = false }.then(if (index == 0) Modifier.testTag("interface_preview") else Modifier),
                        inAuthorRow = section == InterfaceSection.Catalog) }
                }
                InterfaceSection.Focus -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PostCard(PreviewPost, {}, Modifier.focusProperties { canFocus = false }
                        .testTag("interface_focus_preview"), previewFocused = true)
                    if (o.fullTitle) Text(PreviewPost.title, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.background(colors.surface, RoundedCornerShape(8.dp)).padding(12.dp))
                }
                InterfaceSection.Author -> ScaledPreview("interface_author_preview") {
                    Column(Modifier.fillMaxSize().background(colors.background).padding(horizontal = 48.dp, vertical = 27.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AuthorHeader(PreviewBlog, "author", {}, interactive = false)
                        Text("Публикации автора", style = MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(o.density.horizontalGapDp.dp)) {
                            repeat(6) { PostCard(PreviewPost.copy(id = "a$it"), {}, Modifier.focusProperties { canFocus = false }, inAuthorRow = true) }
                        }
                    }
                }
                InterfaceSection.Theme -> ScaledPreview("interface_theme_preview") {
                    Row(Modifier.fillMaxSize().background(colors.background)) {
                        Column(Modifier.width(112.dp).fillMaxHeight().background(colors.surface).padding(32.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Icon(Icons.Default.PlayArrow, null, tint = LocalAccentText.current)
                            Icon(Icons.Default.Home, null, tint = LocalAccentText.current)
                            Icon(Icons.Default.Settings, null, tint = colors.onSurface)
                        }
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Text("Ваши подписки", style = MaterialTheme.typography.headlineSmall)
                            Text("Автор примера", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                repeat(4) { PostCard(PreviewPost.copy(id = "t$it"), {}, Modifier.focusProperties { canFocus = false }, previewFocused = it == 0) }
                            }
                        }
                    }
                }
                InterfaceSection.Player -> ScaledPreview("interface_player_preview") { PlayerPanelPreview(o) }
            }
        }
        val fontScale = LocalContext.current.resources.configuration.fontScale
        Text(when (section) {
            InterfaceSection.Catalog -> "Прежние размеры и плотность сохраняются. Меню раскрывается поверх каталога, не меняя обложки."
            InterfaceSection.Text -> "Название: ${o.textSize.titleSp} sp · метаданные: ${o.metadataSp} sp.\nМасштаб шрифта ТВ: ${(fontScale * 100).toInt()}%. Для подписей рекомендуются 11–12 sp."
            InterfaceSection.Focus -> "Превью не забирает пульт. Рекомендуемая рамка — 2 dp. Полное название появляется после остановки фокуса, только если оно обрезано."
            InterfaceSection.Author -> "${o.authorHeader.label}. Шапка показана тем же компонентом, что и на странице автора."
            InterfaceSection.Theme -> "Акцентные надписи светлее заливок для читаемости. Видео и его цвета не меняются."
            InterfaceSection.Player -> "${o.playerLayout.label} · ${o.playerText.label.lowercase()} размер времени.\nПрименяется при открытии следующего видео. Это макет без воспроизведения."
        }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** Measure the actual 960×540 layout, then scale its drawing; never fetches user data. */
@Composable
private fun ScaledPreview(tag: String, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val sourceWidth = with(density) { 960.dp.roundToPx() }
    val sourceHeight = with(density) { 540.dp.roundToPx() }
    Layout(content, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clipToBounds()
        .border(1.dp, MaterialTheme.colorScheme.border, RoundedCornerShape(8.dp)).testTag(tag)) { measurables, constraints ->
        val children = measurables.map { it.measure(Constraints.fixed(sourceWidth, sourceHeight)) }
        val scale = constraints.maxWidth.toFloat() / sourceWidth
        layout(constraints.maxWidth, constraints.maxHeight) {
            children.forEach { child -> child.placeWithLayer(0, 0) {
                scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f)
            } }
        }
    }
}

@Composable
private fun PlayerPanelPreview(o: InterfaceOptions) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Text("Тестовое видео", style = MaterialTheme.typography.titleLarge.copy(fontSize = PlayerPanelMetrics.TitleSp.sp),
            modifier = Modifier.padding(start = 48.dp, top = 32.dp))
        Column(Modifier.align(Alignment.TopEnd).padding(end = 48.dp, top = 32.dp), horizontalAlignment = Alignment.End) {
            if (o.playerClock) Text("21:30", fontSize = PlayerPanelMetrics.ClockSp.sp)
            if (o.playerEndTime) Text("Окончание ≈ 22:15", fontSize = PlayerPanelMetrics.EndTimeSp.sp, color = colors.onSurfaceVariant)
        }
        Row(Modifier.align(if (o.playerLayout == PlayerPanelLayout.Lower) Alignment.BottomCenter else Alignment.Center)
            .then(if (o.playerLayout == PlayerPanelLayout.Lower) Modifier.padding(bottom = (PlayerPanelMetrics.LowerTransportMarginDp + 24).dp) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { index ->
                Box(Modifier.size(52.dp).background(colors.surface, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(36.dp)) {
                        val w = size.width; val h = size.height
                        if (index == 1) {
                            drawRect(colors.onSurface, Offset(w * 0.2f, h * 0.15f), Size(w * 0.2f, h * 0.7f))
                            drawRect(colors.onSurface, Offset(w * 0.6f, h * 0.15f), Size(w * 0.2f, h * 0.7f))
                        } else repeat(2) { triangle ->
                            val x = w * (0.1f + triangle * 0.4f)
                            drawPath(Path().apply {
                                moveTo(x + if (index == 0) w * 0.35f else 0f, h * 0.2f)
                                lineTo(x + if (index == 0) 0f else w * 0.35f, h * 0.5f)
                                lineTo(x + if (index == 0) w * 0.35f else 0f, h * 0.8f); close()
                            }, colors.onSurface)
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(start = 48.dp, end = 48.dp,
            bottom = (PlayerPanelMetrics.ProgressBottomDp + 24).dp).fillMaxWidth()
            .height(if (o.playerThickSeekBar) 6.dp else 4.dp).background(colors.border)) {
            Box(Modifier.fillMaxWidth(0.3f).fillMaxHeight().background(colors.primary))
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(horizontal = 48.dp, vertical = 27.dp)
            .fillMaxWidth().height(60.dp).background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("12:34 · 45:00", fontSize = o.playerText.timeSp.sp)
            Text("Качество: Авто  ⚙", fontSize = 14.sp)
        }
    }
}

private val PreviewBlog = Blog("author", "Описание канала", "Автор примера", null, null, 0, 178, true, false, null)
private val PreviewPost = Post("preview", 0, "Длинное название видео: проверяем удобство чтения с дивана",
    "author", "Автор с длинным именем", 1_700_000_000, true, false, false, false, null,
    listOf(ContentBlock.Video("preview", null, null, 754, 1920, 1080, null, null, null,
        listOf(VideoSource(VideoSource.Kind.Progressive, "about:blank", 1080, "preview")), true)), emptyList())
