# Development and testing

[Русский](DEVELOPMENT.md) · [Documentation](README.en.md)

## Environment and builds

Code/history stay local. Use Docker `linux/amd64`: JDK 21, Gradle 8.13,
AGP 8.13.2, compileSdk 36, targetSdk 35, minSdk 23, Java 11 bytecode.
See [commands and cache](../build-support/README.en.md). Do not run concurrent
Gradle builds against one checkout/cache. Apple Silicon uses SDK/JVM emulation.

```sh
./gradlew testDebugUnitTest :core:domain:test :core:localization:test \
  :app:assembleDebug assembleDebugAndroidTest :app:assembleRelease :app:lintDebug \
  --max-workers=1 --no-watch-fs --console=plain
node --test build-support/*.test.mjs
node build-support/test-pairing-brand.mjs
node build-support/check-docs.mjs
node build-support/build-pages.mjs
```

Without an override the version is today's `.0`, not a public release.
Use explicit `-Pboostyvp.version=YYYY.MM.DD.N` with a suitable versionCode for
local release builds. The workflow assigns public versions/tags:
[publishing](RELEASING.en.md). See [dependency policy](DEPENDENCIES.en.md).

## Device verification

CI runs JVM/Node checks and compiles test APKs/five ABI builds; it **does not
execute** instrumentation on the runner. Real-TV access requires separate owner
authorization. Use the debug package and synthetic data; never delete production
library data to test privacy controls.

Regressions include HomeRowFocus, PlayerReturn, NewFeedFocus, ContinueWatching,
HistoryActions, PostReader, BilingualLibraryUi, SettingsFileExport,
SettingsExportDialog, VideoCommentsNavigation, PlayerControls,
RemoteSeekDevice, PlaybackReliabilityDevice and core/data migrations.
Full class names live under each module's `src/androidTest`.

```sh
./gradlew :app:connectedDebugAndroidTest :feature:player:connectedDebugAndroidTest \
  :core:data:connectedDebugAndroidTest
```

Chrome/live/update-fixture cases require opt-in; skipped cases are not PASS.
After isolated tests, update the signed release via `install -r` with the same
package/key, without clear/uninstall/downgrade. Verify sign-in, library, preferences
and position with real video. Record model/Android/ABI, tests, skips and limitations
in a dated report. [Current results](RELEASE_READINESS.en.md), [player](player-input.en.md).

Share the existing debug key between builders to avoid debug-package signature
mismatch. Keep keys, passwords and user artifacts outside Git.

## Fuzzing

Parsing of untrusted data is fuzzed with [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer):
pairing-server requests, form fields and session values (`core/auth`,
`PairingInputFuzzTest`), Boosty API responses and content blocks (`core/network`,
`BoostyResponseFuzzTest`). The checks live next to them in `*Checks.kt`: accepted
rejections (for example `IllegalArgumentException` for a bad session) are kept
apart from findings, which are any other exception or a broken invariant.

A normal unit-test run replays the saved inputs from
`src/test/resources/**/*FuzzTestInputs`. Fuzz one target for two minutes:

```bash
JAZZER_FUZZ=1 ./gradlew :core:auth:testDebugUnitTest --tests 'io.github.ricox.boostyvp.core.auth.pairing.PairingInputFuzzTest.httpRequest'
```

Jazzer saves a crashing input into the same input folder; once fixed, it stays as
a regression test. The Fuzzing workflow fuzzes every target weekly and in pull
requests that change the parsers. `core/network` tests replace `android.util.Log`
with an empty stub: mappers log unusual input, and the real `Log` would throw on
the JVM and produce false findings.

## OpenSSF

Practices are scored by [OpenSSF Scorecard](https://scorecard.dev/viewer/?uri=github.com/rico-x/BoostyVP)
(the OpenSSF Scorecard workflow). Prepared answers for the Best Practices form and
what blocks the passing level are in [OPENSSF_BEST_PRACTICES.en.md](OPENSSF_BEST_PRACTICES.en.md).

## Working with main

The normal path is a PR, required checks and squash merge. A direct local transfer
requires a separate explicit owner instruction. Preserve others' history and
account for concurrent `origin/main` changes. A local commit does not authorize
push/PR/publication: [AGENTS](../AGENTS.md). Disclose substantial
[LLM assistance](AI_ASSISTANCE.en.md).

## Screenshots

Sets: `docs/screenshots/ru` and `docs/screenshots/en`, **1920×1080** WebP.
Capture the real current interface and change language through normal settings.
Do not translate posts/creator names, redraw UI or use an old image as proof of
a new feature. Record version/date/source in the [manifest](screenshots/manifest.json).

The owner authorized documentation captures with live subscriptions. Exclude
the owner's profile, phone/email, sign-in, pairing QR/address, tokens and private
media URLs. Avoid sensitive comment text. Keep other user artifacts outside Git.
Restore language, preferences and any affected position; stop playback afterward.

```sh
adb exec-out screencap -p > /tmp/boostyvp-home.png
cwebp -quiet -q 85 -m 6 -metadata none /tmp/boostyvp-home.png \
  -o docs/screenshots/en/home.webp
```

See [screenshots](screenshots/README.md) for the set, captions, rights and hashes.
Keep README, guide and site consistent. `check-docs.mjs` checks local links,
anchors, language pairs and image dimensions.
