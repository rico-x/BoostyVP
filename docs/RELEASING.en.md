# Builds, signing and publishing

[Русский](RELEASING.md) · [Release readiness](RELEASE_READINESS.en.md)

This describes a process, not a completed release. Pushing, tagging, VirusTotal
upload and publication require an explicit owner request. Documentation preparation
does not authorize them.

## Versions and architectures

Versions use the UTC date and a daily sequence: `YYYY.MM.DD.N`, N from 1 to 99.
The workflow chooses the next number from `v<date>.N` tags; no version.properties
file exists. The `version` input sets the number explicitly, for example to stay
above a local `.1` already installed: only today's UTC date and a number above the used ones pass. `versionCode` is `YYMMDDNN` (`2026.10.25.1` → `26102501`). Without
an override, local builds use today's `.0`, not a public release. Use explicit
`-Pboostyvp.version=YYYY.MM.DD.N` or `BOOSTYVP_VERSION` locally. Account for the
installed local versionCode: the updater requires a higher code.

```sh
./gradlew :app:assembleRelease -PsplitApks=true -Pboostyvp.version=YYYY.MM.DD.N
```

This produces `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` and `universal`.
Without the flag, the build is universal. minSdk 23, targetSdk 35, compileSdk 36.
Codes exceed earlier local 0.x builds; updates preserve data with the same key.
Validation checks package/version, min/target SDK, TV declaration and actual
`lib/<ABI>` contents. Five APKs do not prove all-TV compatibility. 16-KiB ZIP
alignment does not prove every ELF/device supports 16-KiB memory pages.
AAB/Google Play publishing is not part of this process.

## Workflows

| Workflow | Action |
|---|---|
| Verify and build BoostyVP | Wrapper, Node/site/docs, shell checks, JVM tests, debug/test APKs and five unsigned release APKs; ClamAV. Does not execute instrumentation on the runner |
| Build and publish BoostyVP | Manual main-only run; version, Verify, separate signing, certificate/hashes, GitHub attestation and an SLSA provenance file, ClamAV/VirusTotal, draft → publication → Pages |
| Deploy project Pages | Static RU/EN site for the latest published release, including pre-releases; no release means no APK links |
| CodeQL analysis | Kotlin/Java and JavaScript analysis; findings require separate review |
| Dependency review / graph / Scorecard | PR dependency review, graph for Dependabot and repository-practice assessment |
| Fuzzing | Jazzer fuzzing of the parsers weekly and in pull requests that change them: [development](DEVELOPMENT.en.md#fuzzing) |

`dry_run` does not publish, but signs and uploads APKs to VirusTotal: it is
**not** a completely local/private check. Releases default to pre-release.
VirusTotal findings block publication except for an explicitly chosen, reviewed
`virustotal_max_malicious` threshold. Scans/checksums describe specific files,
not proof of zero defects.

## Persistent signing identity

The `release` environment must permit main and provide:

| Secret | Purpose |
|---|---|
| ANDROID_KEYSTORE_BASE64 | Persistent PKCS12 as base64 |
| ANDROID_KEYSTORE_PASSWORD | Keystore password |
| ANDROID_KEY_ALIAS | Existing key alias |
| ANDROID_KEY_PASSWORD | Key password |
| VIRUSTOTAL_API_KEY | APK scanning service key |

Use protected input/stdin, not visible secret-valued arguments. No Boosty session
is needed for builds. Publication uses GitHub's `GITHUB_TOKEN`, not a separate PAT.
Verify environment/secrets before running; never print values into docs/logs.

Do not create a new key for each release. Temporary RUNNER_TEMP key files are
removed before artifact upload. Expected certificate SHA-256:

```text
3f86f9845707ef4d3adf3e3d81c4ee6080b9a06f570556bdd65c0a02579b1b4c
```

Pinned in [release-config](../build-support/release-config.json); public and distinct
from an APK checksum. Published tags/APKs are never overwritten.

## APK verification

Download the APK, SHA256SUMS and release-manifest.json from one Releases tag.
Linux: `sha256sum -c SHA256SUMS`; macOS: `shasum -a 256 -c SHA256SUMS`.
If downloading one APK, compare that file's line. A checksum establishes integrity
against the manifest, not trust in an unknown source.

```sh
apksigner verify --verbose --print-certs file.apk
gh attestation verify file.apk --repo rico-x/BoostyVP
gh attestation verify file.apk --repo rico-x/BoostyVP --bundle boostyvp-v<version>.intoto.jsonl
```

The second form checks the SLSA provenance file from the same release without asking
GitHub for the attestation; the release workflow checks every APK against it before publishing.

The certificate must match the pinned value. Attestation ties a file to workflow
and commit, not safety. The site takes links only from the newest published
release's real assets and checks HTTP 200.

## Release procedure

1. Review [readiness](RELEASE_READINESS.en.md), code rights, main and remote changes.
2. Update CHANGELOG and concise bilingual `release-notes/next.md`.
3. Run local checks and authorized device regressions; record update-in-place
   evidence with preserved sign-in/data.
4. Push only when explicitly requested; wait for checks of that revision.
5. Verify release environment/certificate, then run the workflow when authorized,
   accounting for VirusTotal uploads even in dry_run.
6. Verify all five APKs, SHA256SUMS, manifest, scans and attestation.
7. Check RU/EN Pages, links/versions, download an APK and update in place.

Roll back with a corrective higher-versionCode release, not uninstalling the app
or replacing the key. Earlier green CI, local APKs and documentation screenshots
are not evidence of a verified public release.
