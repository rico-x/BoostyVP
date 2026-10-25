# Dependencies and compatibility

[Русский](DEPENDENCIES.md) · [Documentation](README.en.md)

Verified snapshot: 2026-10-25. Versions come from `gradle/libs.versions.toml`.
This is neither a continuously updated latest-version list nor a vulnerability audit.

## Pinned set

| Family | Version |
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

## Compatibility boundaries

Android 6 is retained. In the inspected set, newer families required SDK 37,
minSdk 24, AGP 9 or Kotlin 2.4/R8. AAR/Kotlin metadata checks are not disabled
to force upgrades. Actual failures and old/new comparisons remain in the
[original report](verification/2026-10-25-priority-fixes-dependencies.md).
The Android 6–13/other-TV matrix remains open despite successful builds.

- Retrofit uses the official Kotlin serialization converter.
- Coil 3 uses `coil-network-okhttp` and `coil-network-cache-control`, preserving
  Cache-Control/no-store. Desugaring supports API 23–25.
- Unused `logging-interceptor` was removed; body/token logging is not enabled.
- The Room upgrade did not alter schema. A separate feature migration v3 → v4
  for favorites is retained/tested; destructive recreation is not used.
- The private Media3 icon resource is no longer used.
- Two scoped TV-channel RestrictedApi suppressions cover public Builders
  inheriting setters from a hidden base, not arbitrary hidden API use:
  [TV Provider 1.1.0 source](https://dl.google.com/dl/android/maven2/androidx/tvprovider/tvprovider/1.1.0/tvprovider-1.1.0-sources.jar).

## Dependabot and auditing

YAML schedules weekly checks, Gradle Mondays at 06:00 Europe/Moscow, up to ten
PRs and grouped related minor/patch updates. There is no automatic merging.
The latest manual GitHub check on 2026-10-25 found **version updates Disabled**,
with security updates/dependency graph enabled. YAML does not enable fork
version updates by itself: [GitHub rule](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/configure-version-updates#enabling-version-updates-on-forks).
Enable and inspect bot logs with authorized publication of the current code.

```sh
node --test build-support/*.test.mjs
node build-support/dependency-audit.mjs
node build-support/android-dependency-compat.mjs
```

Audits read official Maven metadata, AAR minSdk/minCompileSdk/AGP and POM data;
they do not rewrite the catalog. Node.js 20+, network and unzip are required.
These inspect direct constraints and do not replace Kotlin/Hilt/R8 builds or
device tests. See [Development](DEVELOPMENT.en.md) and actual
[release-readiness evidence](RELEASE_READINESS.en.md).
