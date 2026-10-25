# Локальная Android-сборка

[English](README.en.md) · [Разработка](../docs/DEVELOPMENT.md) · [Выпуск](../docs/RELEASING.md)

Запускайте из корня репозитория. Docker содержит JDK 21 и официальный SDK:
compileSdk 36, build tools 35.0.0; Gradle 8.13 из wrapper, AGP 8.13.2.
Для обычной сборки не нужны аккаунт, токены или ключ подписи.

```sh
docker build --platform linux/amd64 -f build-support/Dockerfile \
  -t boostyvp-builder:reproducible build-support

docker run --rm --platform linux/amd64 --cpus=4 --memory=8g \
  --mount type=bind,source="$PWD",target=/workspace \
  --mount type=volume,source=boostyvp-gradle-cache,target=/gradle \
  boostyvp-builder:reproducible \
  ./gradlew testDebugUnitTest :core:domain:test :core:localization:test \
    :app:assembleDebug assembleDebugAndroidTest :app:assembleRelease :app:lintDebug \
    --max-workers=1 --no-watch-fs --console=plain
```

Один checkout/кеш — один Gradle-процесс. Для параллельного разработчика нужен
отдельный checkout и отдельный кеш. На Apple Silicon SDK/JVM работают под
эмуляцией x86-64; маленький проект не означает мгновенную оптимизацию R8.
`--no-watch-fs` избегает проблем Docker bind mounts. При нехватке памяти задайте
например `-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m` одним аргументом.

Без версии сборка имеет сегодняшнюю `.0`. Для устанавливаемой локальной проверки
задайте `-Pboostyvp.version=YYYY.MM.DD.N`, где N выше уже установленного
versionCode этого дня. Это не создаёт тег/выпуск. `-PsplitApks=true` даёт пять
release APK; без флага APK универсальный. Выходы — `app/build/outputs/apk/`.
Release из Gradle unsigned; постоянный ключ держите вне Git, применяйте
`sign-release.sh` по [процедуре](../docs/RELEASING.md).

## Инструменты на хосте

Node.js 20+, unzip; shellcheck для shell-проверок, cwebp для скриншотов.

```sh
node --test build-support/*.test.mjs
node build-support/test-pairing-brand.mjs
node build-support/check-docs.mjs
node build-support/build-pages.mjs
shellcheck build-support/sign-release.sh build-support/install-sdk.sh build-support/clamav-scan.sh
```

Pages создаются локально в `build/pages`: RU в корне, EN в `en/`.
Без release metadata ссылок APK нет. Для снимка настоящих выпусков передайте
`node build-support/build-pages.mjs build build/release`, где releases.json
и SHA256SUMS получены из одного выбранного выпуска. `check-links` проверяет
ссылки download/tag; сам ничего не публикует.

Read-only аудит официальных репозиториев:

```sh
node build-support/dependency-audit.mjs
node build-support/android-dependency-compat.mjs
```

Детали — в [зависимостях](../docs/DEPENDENCIES.md). ClamAV локально не отправляет
APK в облако; `virustotal-scan.mjs` отправляет и требует разрешения владельца.

## Device APK и скриншоты

CI компилирует instrumentation APK, не выполняет их. Используйте отдельный
debug/test package и согласованный debug key; production не удаляйте.
Подключение к ТВ и запуск — только с разрешения владельца.

Документационный next-up fixture запускается отдельно от регрессий:
класс `io.github.ricox.boostyvp.feature.player.DocumentationCaptureTest`,
аргумент `documentationScreenshots=true`. Он снимает текущий нативный overlay
на вымышленных данных в RU/EN, не открывая аккаунт или видео. PNG лежат
в external files target test-пакета; это явно отмечается в манифесте снимков.
Обычный прогон этот capture-сценарий пропускает. [Правила](../docs/DEVELOPMENT.md#скриншоты).
