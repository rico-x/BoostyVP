# OpenSSF Best Practices: ответы для анкеты

[English](OPENSSF_BEST_PRACTICES.en.md) · [Документация](README.md)

Подготовленные ответы для анкеты уровня **passing** на
[bestpractices.dev](https://www.bestpractices.dev). Анкету заполняет владелец:
вход через GitHub → «Add project» → `https://github.com/rico-x/BoostyVP`, затем
статус и обоснование для каждого критерия. Анкета и рецензенты англоязычные,
поэтому таблица ответов ведётся только в
[английской версии](OPENSSF_BEST_PRACTICES.en.md#answers). После сохранения сайт
выдаёт номер проекта; бейдж добавляется в README:

```markdown
[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/<ID>/badge)](https://www.bestpractices.dev/projects/<ID>)
```

## Что мешает уровню passing

- **`floss_license` (MUST) — не выполнен.** MIT распространяется только на
  оригинальные изменения форка. Разрешение на лицензию для унаследованного кода
  [запрошено у автора исходного проекта](https://github.com/ARDcode/boosty-tv-app/issues/1)
  и пока не получено. До ответа проект будет в статусе **in progress**
  (2 балла в OpenSSF Scorecard), после — **passing** (5 баллов).
- **`warnings_fixed` (MUST) — пока не выполнен.** После обновления зависимостей
  2026-10-25 рабочая сборка выдаёт предупреждения об устаревших API
  (перенос `hiltViewModel` в Hilt, переименованные API фокуса Compose,
  экспериментальный API Coil).
- **`static_analysis_fixed` (MUST)** — один алерт CodeQL высокого уровня
  (запасное хранение сессии при сбое Android Keystore) закрыт как принятый риск
  по решению владельца. Рецензент анкеты может счесть это невыполнением.
- Необязательные пункты: `version_semver` (версии по дате — осознанно),
  `test_most` (покрытие не измеряется), `warnings_strict` (предупреждения не
  считаются ошибками).

Ответы отражают состояние `main` на 2026-10-25. Перед отправкой анкеты
проверьте ссылки и пункты, помеченные как зависящие от времени.
