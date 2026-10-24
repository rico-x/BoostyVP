# Происхождение и сторонние компоненты

База — ARDcode/boosty-tv-app, `build-5.1`, `b4c9a938ba7bc12103b5dd27fcc5e315fd0dfbea`. Явная лицензия не обнаружена. Права на этот код не назначаются заново: [NOTICE](../NOTICE.md), [область MIT](../LICENSE.md), [вопрос автору](https://github.com/ARDcode/boosty-tv-app/issues/1).

Это сводка основных проектов, **не** полный юридический аудит транзитивных зависимостей. Версии — в `gradle/libs.versions.toml`; дерево — `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`. Тексты лицензий/notices конкретных версий имеют приоритет.

| Компонент | Источник и лицензия проекта |
|---|---|
| Kotlin, kotlinx.coroutines/serialization | [JetBrains/Kotlin](https://github.com/JetBrains/kotlin), [Kotlin](https://github.com/Kotlin): Apache-2.0 |
| AndroidX, Compose, TV Material, Room, DataStore, Security, Media3 | [AndroidX](https://android.googlesource.com/platform/frameworks/support/), [Media3](https://github.com/androidx/media): Apache-2.0 |
| Dagger/Hilt | [google/dagger](https://github.com/google/dagger): Apache-2.0 |
| Retrofit, OkHttp | [Retrofit](https://github.com/square/retrofit), [OkHttp](https://github.com/square/okhttp): Apache-2.0 |
| Coil | [coil-kt/coil](https://github.com/coil-kt/coil): Apache-2.0 |
| ZXing | [zxing/zxing](https://github.com/zxing/zxing): Apache-2.0 |
| JUnit, MockK, Turbine (тесты) | [JUnit4](https://github.com/junit-team/junit4): EPL-1.0; [MockK](https://github.com/mockk/mockk), [Turbine](https://github.com/cashapp/turbine): Apache-2.0 |

SmartTube — ориентир интерфейса, не источник скопированного кода/графики. Логотипы и контент сервисов не становятся MIT-материалами. Скриншоты `docs/screenshots/` сняты на ТВ с текущим BoostyVP и реальными подписками владельца; обложки, названия и изображения публикаций принадлежат их авторам и не становятся MIT-материалами.
