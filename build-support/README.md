# Local Android build

Run from the repository root. The image contains JDK 21 and the official Android
SDK; Gradle's wrapper pins Gradle 8.13. No signing keys or account data are needed.

With Node.js 20+ on the host, check the renamed browser handoff offline:

```sh
node build-support/test-pairing-brand.mjs
```

```sh
docker build --platform linux/amd64 -f build-support/Dockerfile \
  -t boostyvp-builder:reproducible build-support

docker run --rm --platform linux/amd64 --cpus=4 --memory=8g \
  --mount type=bind,source="$PWD",target=/workspace \
  --mount type=volume,source=boostyvp-gradle-cache,target=/gradle \
  boostyvp-builder:reproducible \
  ./gradlew testDebugUnitTest :app:assembleRelease \
    :feature:player:assembleDebugAndroidTest \
    --max-workers=2 --no-watch-fs --console=plain
```

Do not run this concurrently with another build using the same checkout/cache.
The volume preserves downloaded dependencies. `--no-watch-fs` avoids file-watcher
errors observed with Docker Desktop bind mounts under x86 emulation.

Linux Android build tools require x86-64, so this image uses `linux/amd64` even on
Apple Silicon. JVM compilation/optimization also runs under emulation; a local
build is not automatically faster than GitHub's native x86-64 runner.

The release APK in `app/build/outputs/apk/release/` is unsigned. Sign it locally
using a persistent private key stored **outside** this repository. Never commit
keys, passwords, login/session data, or a user's diagnostics/screenshots.

## Release 0.0.3

The version comes from `version.properties`. Use `-PsplitApks=true` with
`:app:assembleRelease` to produce four ABI APKs plus universal. Omit the flag
for ordinary debug/device builds. A universal APK is recommended when the
Android OS ABI is unknown; CPU bitness alone is not enough.

Node.js 20+ tooling has no npm dependencies:

```sh
node --test build-support/release-tools.test.mjs
node build-support/build-pages.mjs
shellcheck build-support/sign-release.sh build-support/install-sdk.sh
```

The Android runner also runs:

```sh
./gradlew testDebugUnitTest :core:domain:test :app:assembleDebug assembleDebugAndroidTest
./gradlew :app:assembleRelease -PsplitApks=true
node build-support/release-tools.mjs check-apks app/build/outputs/apk/release
```

The final command requires Node.js and Android SDK Build Tools 35.0.0 in the
same environment; the minimal Gradle Docker image does not bundle Node.js.
Device APKs are compiled by CI, not run on physical TVs by the runner.
Signing uses the restricted GitHub `release` environment, never PR secrets.
See [release procedure and certificate verification](../docs/RELEASING.md),
[security limits](../SECURITY.md) and [license scope](../LICENSE.md).
