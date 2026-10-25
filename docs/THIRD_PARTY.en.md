# Third-party code and materials

[Русский](THIRD_PARTY.md) · [NOTICE](../NOTICE.en.md) · [MIT scope](../LICENSE.en.md)

Inherited base: ARDcode/boosty-tv-app, `build-5.1`,
`b4c9a938ba7bc12103b5dd27fcc5e315fd0dfbea`. No license was found in the base;
inherited portions were not relicensed. [Permission](https://github.com/ARDcode/boosty-tv-app/issues/1)
has not been obtained as of 2026-10-25.

This summarizes major projects, not a full legal audit of transitive dependencies.
Specific-version license texts/notices take precedence. Versions live in
`gradle/libs.versions.toml`; inspect runtime dependencies with:
`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`.

| Project | Source / license |
|---|---|
| Kotlin, coroutines, serialization | [JetBrains](https://github.com/JetBrains/kotlin), [Kotlin](https://github.com/Kotlin), Apache-2.0 |
| AndroidX/Compose/TV/Room/DataStore/Security/Media3 | [AndroidX](https://android.googlesource.com/platform/frameworks/support/), [Media3](https://github.com/androidx/media), Apache-2.0 |
| Dagger/Hilt | [google/dagger](https://github.com/google/dagger), Apache-2.0 |
| Retrofit/OkHttp | [Retrofit](https://github.com/square/retrofit), [OkHttp](https://github.com/square/okhttp), Apache-2.0 |
| Coil | [coil-kt/coil](https://github.com/coil-kt/coil), Apache-2.0 |
| desugar_jdk_libs 2.1.5 | [Project](https://github.com/google/desugar_jdk_libs), GPL-2.0 with Classpath Exception, [POM](https://dl.google.com/dl/android/maven2/com/android/tools/desugar_jdk_libs/2.1.5/desugar_jdk_libs-2.1.5.pom), [terms](../LICENSES/desugar_jdk_libs-GPL-2.0-with-classpath-exception.txt); upstream library unmodified |
| ZXing | [zxing/zxing](https://github.com/zxing/zxing), Apache-2.0 |
| JUnit4 / MockK / Turbine (tests) | [JUnit4](https://github.com/junit-team/junit4), EPL-1.0; [MockK](https://github.com/mockk/mockk), [Turbine](https://github.com/cashapp/turbine), Apache-2.0 |

SmartTube inspires usability, not copied code/artwork. Service brands/content
are not MIT materials. Screenshots show the current interface and authorized
owner subscriptions; publication artwork, titles and content belong to creators.
Any demonstration data is identified in [screenshot provenance](screenshots/README.md).
