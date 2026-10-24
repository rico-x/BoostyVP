# Разработка и проверка

## Сборка

Сборка и тесты — в Docker по [build-support/README.md](../build-support/README.md):
`linux/amd64`, JDK 21, Android SDK 35, Gradle из wrapper. Проверка того же
набора, что и в CI:

```bash
./gradlew testDebugUnitTest :core:domain:test :app:assembleDebug assembleDebugAndroidTest
```

Версия сборки без параметров — сегодняшняя `ГГГГ.ММ.ДД.0`, она не выпуск.
Для release-сборки на ТВ задайте следующую версию явно:
`-Pboostyvp.version=$(node build-support/release-tools.mjs next-version)`.
Правила версий и выпуска — в [RELEASING.md](RELEASING.md).

## Проверки

- Модульные тесты и инструменты выпуска (`node --test build-support/*.test.mjs`).
- Инструментальные тесты на реальном ТВ; обязательный набор перечислен в
  [ROADMAP](ROADMAP.md#процесс-качество-и-безопасность). На CI они только компилируются.
- Подписанный release ставится поверх текущего тем же ключом; вход, настройки
  и позиции должны сохраниться.
- ClamAV проверяет APK в каждом прогоне CI; перед выпуском — ещё и VirusTotal.
- [Проверки плеера и причины исправления управления](player-input.md).

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

## Изменения в main

`main` защищён: изменения — только через pull request со squash-слиянием, после
зелёных проверок «Tests and five APK variants», CodeQL и Dependency review.
Сообщение коммита — одна строка. Подробные правила для агентов — в
[AGENTS.md](../AGENTS.md).

Ключ подписи и секреты хранятся вне репозитория. Непроверенный APK нельзя
выдавать за стабильный выпуск; выпуск делает только workflow «Build and publish».

## Скриншоты документации

Снимаются на ТВ с BoostyVP и реальными подписками владельца — так интерфейс
выглядит живым. В кадр не должны попадать данные аккаунта (вход, профиль,
телефон), QR-код сопряжения и адреса видео.

```bash
adb exec-out screencap -p > home.png
cwebp -q 80 -m 6 -metadata none home.png -o docs/screenshots/home.webp
```

Имена файлов используются в [руководстве](USER_GUIDE.md), README и на сайте
(`build-support/build-pages.mjs`): `home`, `new`, `author`, `player`, `next-up`,
`player-settings`, `interface`.
