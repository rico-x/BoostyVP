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
