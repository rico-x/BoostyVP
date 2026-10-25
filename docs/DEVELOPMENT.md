# Разработка и проверки

[English](DEVELOPMENT.en.md) · [Документация](README.md)

## Среда и сборка

Код/история — локально. Сборка в Docker `linux/amd64`: JDK 21, Gradle 8.13,
AGP 8.13.2, compileSdk 36, targetSdk 35, minSdk 23, Java 11 bytecode.
[Команды и кеш](../build-support/README.md). Не запускайте два Gradle одновременно
на одном checkout/кеше. На Apple Silicon Android SDK/JVM работают под эмуляцией.

```sh
./gradlew testDebugUnitTest :core:domain:test :core:localization:test \
  :app:assembleDebug assembleDebugAndroidTest :app:assembleRelease :app:lintDebug \
  --max-workers=1 --no-watch-fs --console=plain
node --test build-support/*.test.mjs
node build-support/test-pairing-brand.mjs
node build-support/check-docs.mjs
node build-support/build-pages.mjs
```

Без версии получается сегодняшняя `.0`, не публичный выпуск. Для локального
release задавайте явную `-Pboostyvp.version=YYYY.MM.DD.N` с подходящим versionCode.
Публичный номер/тег назначает workflow: [публикация](RELEASING.md).
Версии зависимостей/границы обновлений — в [DEPENDENCIES](DEPENDENCIES.md).

## Проверка на устройстве

CI выполняет JVM/Node-проверки, собирает test APK и пять ABI-вариантов;
инструментальные тесты на runner **не исполняются**. Разрешение владельца
на реальный ТВ требуется отдельно. Проверяйте debug-пакет с синтетическими
данными; не удаляйте production-библиотеку ради проверки приватности.

Регрессии: HomeRowFocus, PlayerReturn, NewFeedFocus, ContinueWatching,
HistoryActions, PostReader, BilingualLibraryUi, SettingsFileExport,
SettingsExportDialog, VideoCommentsNavigation, PlayerControls,
RemoteSeekDevice, PlaybackReliabilityDevice и миграции core/data.
Полные имена — в `src/androidTest` соответствующего модуля.

```sh
./gradlew :app:connectedDebugAndroidTest :feature:player:connectedDebugAndroidTest \
  :core:data:connectedDebugAndroidTest
```

Для опциональных Chrome/live/update-fixture сценариев нужно явное включение;
их пропуск не считать PASS. После изолированных тестов подписанный release
обновляется `install -r` тем же пакетом и ключом, без clear/uninstall/downgrade.
Сверьте вход, библиотеку, настройки и позицию на настоящем видео. Указывайте
модель/Android/ABI, тесты, пропуски и ограничения в датированном отчёте.
[Текущие результаты](RELEASE_READINESS.md), [плеер](player-input.md).

Согласуйте debug-ключ между контейнерами: иначе обновление debug-пакета
получит ошибку подписи. Ключи, пароли и артефакты пользователя хранятся вне Git.

## Фаззинг

Разбор недоверенных данных проверяется фаззером [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer):
запросы к серверу сопряжения, поля формы и значения сессии (`core/auth`,
`PairingInputFuzzTest`), ответы API Boosty и блоки контента (`core/network`,
`BoostyResponseFuzzTest`). Проверки лежат рядом, в `*Checks.kt`: допустимые
отказы (например, `IllegalArgumentException` для плохой сессии) отделены от
находок — любого другого исключения или нарушенного условия.

Обычный прогон модульных тестов воспроизводит сохранённые входы из
`src/test/resources/**/*FuzzTestInputs`. Фаззить одну цель (2 минуты):

```bash
JAZZER_FUZZ=1 ./gradlew :core:auth:testDebugUnitTest --tests 'io.github.ricox.boostyvp.core.auth.pairing.PairingInputFuzzTest.httpRequest'
```

Найденный падающий вход Jazzer кладёт в ту же папку входов — после исправления
он остаётся регрессионным тестом. Workflow «Fuzzing» фаззит все цели еженедельно
и в PR, меняющих парсеры. В тестах `core/network` `android.util.Log` заменён
пустой заглушкой: мапперы пишут в журнал необычный ввод, а на JVM настоящий
`Log` бросал бы исключение и давал ложные находки.

## OpenSSF

Оценка практик — [OpenSSF Scorecard](https://scorecard.dev/viewer/?uri=github.com/rico-x/BoostyVP)
(workflow «OpenSSF Scorecard»). Готовые ответы для анкеты Best Practices и что
мешает уровню passing — в [OPENSSF_BEST_PRACTICES.md](OPENSSF_BEST_PRACTICES.md).

## Работа с main

Обычный путь — PR, обязательные checks и squash merge. Прямой локальный перенос
возможен только по отдельному явному указанию владельца. Не переписывайте
чужую историю; при работе параллельно учитывайте изменения `origin/main`.
Push/PR/выпуск не следуют автоматически из локального коммита: [AGENTS](../AGENTS.md).
Существенную помощь LLM отмечайте: [AI_ASSISTANCE](AI_ASSISTANCE.md).

## Скриншоты

Оба комплекта: `docs/screenshots/ru` и `docs/screenshots/en`, WebP **1920×1080**.
Снимайте настоящий текущий интерфейс, переводя язык штатной настройкой.
Публикации/имена авторов не переводятся. Не дорисовывайте интерфейс и не используйте
старый кадр как доказательство новой функции. Версию/дату/источник фиксируйте
в [манифесте скриншотов](screenshots/manifest.json).

Владелец разрешил документационные снимки с его живыми подписками. В кадр
не должны попасть собственный профиль, телефон/e-mail, вход, QR/адрес сопряжения,
токены или приватные видеоссылки. Не включайте чувствительные тексты комментариев.
Остальные пользовательские материалы держите вне Git. После съёмки восстановите
язык, настройки и затронутую позицию, остановите воспроизведение.

```sh
adb exec-out screencap -p > /tmp/boostyvp-home.png
cwebp -quiet -q 85 -m 6 -metadata none /tmp/boostyvp-home.png \
  -o docs/screenshots/ru/home.webp
```

Набор/подписи/права и SHA-256 перечислены в [screenshots](screenshots/README.md).
Обновляйте README, руководство и сайт согласованно; `check-docs.mjs` проверяет
локальные ссылки, якоря, языковые пары и размеры изображений.
