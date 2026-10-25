# Local Android builds

[Русский](README.md) · [Development](../docs/DEVELOPMENT.en.md) · [Releases](../docs/RELEASING.en.md)

Run from the repository root. Docker includes JDK 21 and the official SDK:
compileSdk 36, build tools 35.0.0; Gradle 8.13 wrapper, AGP 8.13.2.
Ordinary builds need no account, token or signing key.

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

Use one Gradle process per checkout/cache; parallel developers need separate
checkouts/caches. Apple Silicon emulates x86-64 SDK/JVM tools, so even small
projects can take time in R8. `--no-watch-fs` avoids Docker bind-mount problems.
If needed, pass e.g. `-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m`
as one argument.

Without a version override, builds use today's `.0`. For local installation,
set `-Pboostyvp.version=YYYY.MM.DD.N`, with a code above the installed build
for that date. This creates no tag/release. `-PsplitApks=true` produces five
release APKs; without it the APK is universal. Outputs: `app/build/outputs/apk/`.
Gradle release APKs are unsigned; keep the persistent key outside Git and use
`sign-release.sh` following the [release procedure](../docs/RELEASING.en.md).

## Host tools

Node.js 20+, unzip; shellcheck for shell checks, cwebp for screenshots.

```sh
node --test build-support/*.test.mjs
node build-support/test-pairing-brand.mjs
node build-support/check-docs.mjs
node build-support/build-pages.mjs
shellcheck build-support/sign-release.sh build-support/install-sdk.sh build-support/clamav-scan.sh
```

Local Pages output: `build/pages`, RU at root and EN under `en/`.
Without release metadata there are no APK links. Build a real release snapshot
with `node build-support/build-pages.mjs build build/release`; releases.json
and SHA256SUMS must correspond to the chosen release. `check-links` validates
download/tag links; it never publishes.

Read-only official-repository audits:

```sh
node build-support/dependency-audit.mjs
node build-support/android-dependency-compat.mjs
```

See [dependency policy](../docs/DEPENDENCIES.en.md). Local ClamAV does not upload
APKs; `virustotal-scan.mjs` does and requires owner authorization.

## Device APKs and screenshots

CI compiles instrumentation APKs but does not execute them. Use a separate
debug/test package with the shared existing debug key; never uninstall production.
TV connection and execution require owner authorization.

The documentation next-up fixture runs separately from regressions:
class `io.github.ricox.boostyvp.feature.player.DocumentationCaptureTest`, argument
`documentationScreenshots=true`. It captures the current native overlay using
synthetic RU/EN data, opening no account or actual video. PNGs are saved in the
target test package's external files directory; mark this origin in the screenshot
manifest. Ordinary runs skip this capture case. [Policy](../docs/DEVELOPMENT.en.md#screenshots).
