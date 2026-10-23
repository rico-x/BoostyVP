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
