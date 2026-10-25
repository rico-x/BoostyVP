# Сторонний код и материалы

[English](THIRD_PARTY.en.md) · [NOTICE](../NOTICE.md) · [Область MIT](../LICENSE.md)

Исходная база — ARDcode/boosty-tv-app, `build-5.1`,
`b4c9a938ba7bc12103b5dd27fcc5e315fd0dfbea`. Лицензия в базе не обнаружена;
унаследованные части не перелицензированы. [Разрешение](https://github.com/ARDcode/boosty-tv-app/issues/1)
на 2026-10-25 не получено.

Это сводка основных проектов, не полный юридический аудит транзитивных
зависимостей. Тексты/notices конкретных версий имеют приоритет.
Версии — в `gradle/libs.versions.toml`; runtime-дерево:
`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`.

| Проект | Источник / лицензия |
|---|---|
| Kotlin, coroutines, serialization | [JetBrains](https://github.com/JetBrains/kotlin), [Kotlin](https://github.com/Kotlin), Apache-2.0 |
| AndroidX/Compose/TV/Room/DataStore/Security/Media3 | [AndroidX](https://android.googlesource.com/platform/frameworks/support/), [Media3](https://github.com/androidx/media), Apache-2.0 |
| Dagger/Hilt | [google/dagger](https://github.com/google/dagger), Apache-2.0 |
| Retrofit/OkHttp | [Retrofit](https://github.com/square/retrofit), [OkHttp](https://github.com/square/okhttp), Apache-2.0 |
| Coil | [coil-kt/coil](https://github.com/coil-kt/coil), Apache-2.0 |
| desugar_jdk_libs 2.1.5 | [Проект](https://github.com/google/desugar_jdk_libs), GPL-2.0 с Classpath Exception, [POM](https://dl.google.com/dl/android/maven2/com/android/tools/desugar_jdk_libs/2.1.5/desugar_jdk_libs-2.1.5.pom), [текст](../LICENSES/desugar_jdk_libs-GPL-2.0-with-classpath-exception.txt); исходный проект не изменён |
| ZXing | [zxing/zxing](https://github.com/zxing/zxing), Apache-2.0 |
| JUnit4 / MockK / Turbine (тесты) | [JUnit4](https://github.com/junit-team/junit4), EPL-1.0; [MockK](https://github.com/mockk/mockk), [Turbine](https://github.com/cashapp/turbine), Apache-2.0 |

SmartTube — ориентир интерфейса, не источник скопированного кода/графики.
Бренды и контент сервисов не становятся MIT-материалами. Скриншоты — текущий
интерфейс с разрешёнными подписками владельца; обложки, названия и контент
принадлежат авторам. Данные демонстрационного кадра, если используются,
помечаются в [происхождении скриншотов](screenshots/README.md).
