package io.github.ricox.boostyvp.core.ui.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ricox.boostyvp.core.ui.component.TvActionButton
import io.github.ricox.boostyvp.core.ui.theme.Overscan

enum class InterfaceSection(val id: String, val label: String) {
    Catalog("catalog", "Каталог"), Text("text", "Подписи"), Focus("focus", "Выделение"),
    Author("author", "Автор"), Theme("theme", "Тема"), Player("player", "Плеер");
}

/** Six short, real categories; one focus stop per control and a non-interactive preview. */
@Composable
fun InterfaceSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { InterfacePreferences(context) }
    val cardPreferences = remember(context) { CardSizePreferences(context) }
    val options = LocalInterfaceOptions.current
    val size = LocalCardSize.current
    var section by rememberSaveable { mutableStateOf(InterfaceSection.Catalog) }
    var showCardSize by rememberSaveable { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val tabFocus = remember { InterfaceSection.entries.associateWith { FocusRequester() } }
    val choices = settingsFor(section, options, preferences::save)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Overscan.Horizontal, vertical = Overscan.Vertical),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Визуальные улучшения", style = MaterialTheme.typography.headlineSmall)
                Text("← → — значение · OK — выбор", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InterfaceSection.entries.forEach { target ->
                    TvActionButton(target.label, { section = target }, Modifier.weight(1f)
                        .focusRequester(tabFocus.getValue(target)).focusProperties { down = firstFocus }
                        .testTag("interface_category_${target.id}").semantics { selected = section == target },
                        primary = section == target)
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                key(section) {
                    LazyColumn(Modifier.weight(1f).testTag("interface_settings_list"),
                        contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (section == InterfaceSection.Catalog) item(key = "card_size") {
                            TvActionButton("Размер карточек: ${size.label}", { showCardSize = true },
                                Modifier.fillMaxWidth().focusRequester(firstFocus)
                                    .focusProperties { up = tabFocus.getValue(section) }.testTag("interface_card_size"))
                        }
                        items(choices, key = { it.key }) { choice ->
                            val first = section != InterfaceSection.Catalog && choice === choices.first()
                            VisualSettingRow(choice, if (first) Modifier.focusRequester(firstFocus)
                                .focusProperties { up = tabFocus.getValue(section) } else Modifier)
                        }
                    }
                }
                InterfacePreview(section, Modifier.width(320.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("↑ ↓ — пункт · Вверх из первого пункта — категории\nСохраняется автоматически · Назад — закрыть",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TvActionButton("Готово", onDismiss)
            }
        }
        LaunchedEffect(section) { withFrameNanos { }; firstFocus.requestFocus() }
    }
    if (showCardSize) CardSizeSettingsDialog(size, cardPreferences::save, { showCardSize = false })
}

internal enum class SettingKind { Steps, Toggle, Choice, Color }
internal data class VisualSetting(val key: String, val title: String, val values: List<String>, val index: Int,
    val onSelect: (Int) -> Unit, val kind: SettingKind = SettingKind.Steps)

private fun settingsFor(section: InterfaceSection, o: InterfaceOptions, save: (InterfaceOptions) -> Unit): List<VisualSetting> {
    fun toggle(key: String, title: String, value: Boolean, change: (Boolean) -> InterfaceOptions) =
        VisualSetting(key, title, listOf("Нет", "Да"), if (value) 1 else 0, { save(change(it == 1)) }, SettingKind.Toggle)
    return when (section) {
        InterfaceSection.Catalog -> listOf(
            toggle("sidebar", "Сворачивать меню", o.autoCollapseSidebar) { o.copy(autoCollapseSidebar = it) },
            VisualSetting("density", "Плотность", CatalogDensity.entries.map { it.label }, o.density.ordinal,
                { save(o.copy(density = CatalogDensity.entries[it])) }),
            VisualSetting("card_style", "Оформление карточек", CardStyle.entries.map { it.label }, o.cardStyle.ordinal,
                { save(o.copy(cardStyle = CardStyle.entries[it])) }, SettingKind.Choice),
        )
        InterfaceSection.Text -> listOf(
            VisualSetting("text_size", "Текст названия", CardTextSize.entries.map { it.label }, o.textSize.ordinal,
                { save(o.copy(textSize = CardTextSize.entries[it])) }),
            VisualSetting("metadata_size", "Размер метаданных", CardMetadataSize.entries.map { it.label }, o.metadataSize.ordinal,
                { save(o.copy(metadataSize = CardMetadataSize.entries[it])) }),
            VisualSetting("title_lines", "Строк названия", listOf("1", "2", "3"), o.titleLines - 1,
                { save(o.copy(titleLines = it + 1)) }),
            toggle("author", "Имя автора", o.showAuthor) { o.copy(showAuthor = it) },
            toggle("repeat_author", "Повтор имени у автора", !o.hideRepeatedAuthor) { o.copy(hideRepeatedAuthor = !it) },
            toggle("date", "Дата публикации", o.showDate) { o.copy(showDate = it) },
            toggle("duration", "Длительность видео", o.showDuration) { o.copy(showDuration = it) },
        )
        InterfaceSection.Focus -> listOf(
            VisualSetting("focus_color", "Цвет рамки", FocusColor.entries.map { it.label }, o.focusColor.ordinal,
                { save(o.copy(focusColor = FocusColor.entries[it])) }, SettingKind.Choice),
            VisualSetting("focus_width", "Толщина рамки", (1..4).map { if (it == 2) "2 dp · рекомендуется" else "$it dp" }, o.focusWidth - 1,
                { save(o.copy(focusWidth = it + 1)) }),
            toggle("focus_zoom", "Увеличивать карточку", o.focusZoom) { o.copy(focusZoom = it) },
            toggle("focus_animation", "Анимация выделения", o.animateFocus) { o.copy(animateFocus = it) },
            toggle("full_title", "Полное обрезанное название", o.fullTitle) { o.copy(fullTitle = it) },
        )
        InterfaceSection.Author -> listOf(VisualSetting("author_header", "Шапка страницы автора",
            AuthorHeaderSize.entries.map { it.label }, o.authorHeader.ordinal,
            { save(o.copy(authorHeader = AuthorHeaderSize.entries[it])) }))
        InterfaceSection.Theme -> listOf(
            VisualSetting("theme", "Тёмная тема", DarkTheme.entries.map { it.label }, o.theme.ordinal,
                { save(o.copy(theme = DarkTheme.entries[it])) }, SettingKind.Choice),
            VisualSetting("accent", "Акцент интерфейса", AccentColor.entries.map { it.label }, o.accent.ordinal,
                { save(o.copy(accent = AccentColor.entries[it])) }, SettingKind.Color),
        )
        InterfaceSection.Player -> listOf(
            VisualSetting("player_layout", "Панель управления", PlayerPanelLayout.entries.map { it.label }, o.playerLayout.ordinal,
                { save(o.copy(playerLayout = PlayerPanelLayout.entries[it])) }, SettingKind.Choice),
            VisualSetting("player_text", "Время в плеере", PlayerTextSize.entries.map { it.label }, o.playerText.ordinal,
                { save(o.copy(playerText = PlayerTextSize.entries[it])) }),
            toggle("player_seek_bar", "Утолщённая шкала", o.playerThickSeekBar) { o.copy(playerThickSeekBar = it) },
            toggle("player_clock", "Часы на панели", o.playerClock) { o.copy(playerClock = it) },
            toggle("player_end_time", "Время окончания", o.playerEndTime) { o.copy(playerEndTime = it) },
        )
    }
}
