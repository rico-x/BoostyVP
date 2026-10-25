# Документация и подготовка выпуска: 2026-10-25

## Русский

Код `e4de85d` перенесён fast-forward в локальный `main` по прямому указанию
владельца. Документационная итерация подготовлена в
`docs/release-preparation-2026-10-25` и включена в локальный `main`;
публикации/Actions не запускались.

- Переписаны README и руководство, заметный блок руководства сверху README.
- 21 актуальная пара RU/EN: пользовательские, технические и правовые документы.
  Старый подробный roadmap сохранён как история; датированные доказательства
  не выдаются за текущий план и не переведены задним числом.
- Старые семь изображений заменены 24 WebP 1920×1080 с manifest/SHA-256.
  Production — .1, next-up — явно помеченный native fixture без аккаунта/сети.
- Новый экземпляр Luna завершил съёмку после лимита первого. Изменялся язык,
  открыт тот же paused-ролик около 01:33 без перемотки. Обновлён только
  изолированный test APK. Production для остановки был force-stop и вновь
  запущен, без очистки данных; возвращены русский и каталог без воспроизведения.
- Pages собирает RU и EN из общих данных, ссылки руководств/галереи локализованы,
  изображения открываются в полном размере. При пустых Releases нет APK-ссылок.
- Исправлена одна устаревшая UI-подсказка RU/EN о раскрытии меню поверх каталога:
  теперь она описывает уже реализованный сдвиг. Логика плеера/данных не менялась.
- Английские legal/privacy тексты добавлены в prepareLegalAssets будущего APK.

Проверки:

| Проверка | Результат |
|---|---|
| Node tools | 26 PASS, 0 FAIL/SKIP |
| check-docs | Все локальные ссылки/якоря, 21 языковая пара, 24 размера/хеша PASS |
| core:localization:test | PASS: полнота обоих словарей, аргументы, EN |
| app:prepareLegalAssets | PASS, оба языка в generated legal assets |
| feature/player test APK | Собран; отдельный opt-in DocumentationCaptureTest дал RU/EN next-up |
| Браузерный просмотр | RU/EN hero/руководство и EN-галерея отображаются, изображения загружаются |
| Полные Android-регрессии | Не повторялись для документации; базовый .1-отчёт отдельно |

Первичные PNG/служебные материалы вне Git. Телевизор сохранил авторизацию,
новый production APK не устанавливался. Авторские права на публикации
не назначаются проекту. Версия/источник каждого изображения в manifest.

GitHub читался без изменений: Releases пуст, активны rulesets main/tag;
запрос ARDcode без ответа. Отдельный remote `bb63fed` требует согласования
с локальным main перед публикацией. Эти реальные внешние этапы перечислены
в [готовности к выпуску](../RELEASE_READINESS.md).

## English summary

Verified code `e4de85d` was fast-forwarded into local main as requested.
Documentation is also integrated into local main, including 21 RU/EN pairs and 24 fresh 1920×1080 WebP
screenshots with provenance/hashes. Next-up is explicitly synthetic native UI;
other images show production .1. Luna restored Russian/catalog and stopped video;
the same paused position around 01:33 was retained without seeking. Only the
isolated test APK was updated; production was restarted without clearing data.

Node tools: 26 PASS. Local links/anchors/pairs/images, localization tests and legal
asset preparation passed. Both static site languages were reviewed in the browser.
Full Android regressions were not rerun for documentation; earlier .1 results
remain separate. A stale RU/EN sidebar hint was corrected; playback/data logic
was unchanged. No push, Actions run, scan upload or release was performed.
Remote-commit reconciliation and inherited-code permission remain before publication.
