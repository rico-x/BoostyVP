# Зависимости и совместимость

[English](DEPENDENCIES.en.md) · [Документация](README.md)

Проверенный срез на 2026-10-25; источник версий — `gradle/libs.versions.toml`.
Это не постоянно обновляемый список последних версий и не аудит уязвимостей.

## Закреплённый набор

| Группа | Версия |
|---|---|
| JDK / Gradle / AGP | 21 / 8.13 / 8.13.2 |
| Android | minSdk 23, targetSdk 35, compileSdk 36; Java 11 bytecode |
| Kotlin / KSP / Hilt / Hilt Navigation | 2.3.21 / 2.3.12 / 2.58 / 1.3.0 |
| Core / Lifecycle / Activity | 1.18.0 / 2.10.0 / 1.13.0 |
| Compose BOM / UI / Navigation | 2026.06.01 / 1.11.4 / 2.9.8 |
| TV Material / TV Provider / Media3 | 1.1.0 / 1.1.0 / 1.11.1 |
| Coil / desugar_jdk_libs | 3.4.0 / 2.1.5 |
| Paging / Room / DataStore / Security Crypto | 3.5.3 / 2.8.5 / 1.2.1 / 1.1.0 |
| Retrofit / OkHttp | 3.0.0 / 5.4.0 |
| Serialization / Coroutines / ZXing | 1.11.0 / 1.11.0 / 3.5.4 |
| AndroidX JUnit / Runner / UI Automator | 1.3.0 / 1.7.0 / 2.4.0 |
| JUnit / MockK / Turbine | 4.13.2 / 1.14.11 / 1.2.1 |

## Почему обновление ограничено

Сохраняется Android 6. В проверенном наборе более новые семейства требовали
SDK 37, minSdk 24, AGP 9 или Kotlin 2.4/R8. Каталог не продвигается через
отключение AAR/Kotlin metadata проверок. Конкретные отказы и сравнение с прежним
набором сохранены в [отчёте](verification/2026-10-25-priority-fixes-dependencies.md).
Матрица Android 6–13 и других ТВ остаётся открытой, даже при успешной сборке.

- Retrofit использует официальный Kotlin serialization converter.
- Coil 3 подключён через `coil-network-okhttp` и `coil-network-cache-control`;
  `Cache-Control`/`no-store` сохраняются, для API 23–25 включён desugaring.
- Неиспользуемый `logging-interceptor` удалён; body/token logging не включён.
- Обновление Room не меняло схему. Отдельная функциональная миграция v3 → v4
  для избранных видео сохранена и проверена; деструктивная пересборка не используется.
- Приватный ресурс значков Media3 больше не используется.
- Два scoped `RestrictedApi` suppression в TV-каналах относятся к публичным
  Builder, наследующим setters скрытого базового класса. Они не разрешают
  произвольные вызовы скрытых API; [источник TV Provider 1.1.0](https://dl.google.com/dl/android/maven2/androidx/tvprovider/tvprovider/1.1.0/tvprovider-1.1.0-sources.jar).

## Dependabot и проверка

YAML: еженедельно, Gradle по понедельникам в 06:00 Europe/Moscow, до 10 PR,
связанные minor/patch сгруппированы; автоматического слияния нет.
Последняя ручная проверка GitHub 2026-10-25 показала **version updates Disabled**,
при включённых security updates и dependency graph. YAML сам не включает
version updates на форке: [правило GitHub](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/configure-version-updates#enabling-version-updates-on-forks).
Включение/проверка журнала совмещаются с разрешённой публикацией актуального кода.

```sh
node --test build-support/*.test.mjs
node build-support/dependency-audit.mjs
node build-support/android-dependency-compat.mjs
```

Аудиты читают официальные Maven-метаданные, AAR minSdk/minCompileSdk/AGP и POM,
не переписывают каталог. Node.js 20+, сеть и unzip обязательны. Это прямые
ограничения, не замена реальной сборке Kotlin/Hilt/R8 и device-проверкам.
Повторяемая сборка — в [DEVELOPMENT](DEVELOPMENT.md); фактически выполненные
проверки — в [готовности к выпуску](RELEASE_READINESS.md).
