# Release readiness

[Русский](RELEASE_READINESS.md) · [Documentation](README.en.md)

Snapshot: **2026-10-25**, code `e4de85d`, verified local build
**2026.10.25.1 / 26102501**. This document does not announce a public release.

## Confirmed

- Fast-forward integration into local `main` explicitly requested by the owner.
- 259 unique JVM tests passed; debug/release and App/Player test APKs built.
- Lint: 0 errors, 50 warnings. This does not claim absence of vulnerabilities.
- TCL / Android 14: App UI 104 PASS + 4 SKIP, Player 37 PASS;
  **141 PASS, 4 SKIP, 0 FAIL** for .1. Skips are explicit opt-ins for Chrome,
  live refresh and two update fixtures.
- Nine core/data tests passed on the preceding .0 in the same iteration;
  they are not added to .1 totals. Library schema v4 preserves data through migration.
- Same-package/key TV update preserved UID, firstInstallTime, sign-in and library access.
- B1–B4 verified on .0; .1 verified Cx export, real comments/reply, both discussion
  entry points and no BoostyVP media session while reading.
- Public certificate pinned in [release-config](../build-support/release-config.json).
  Local APK signature and 16-KiB ZIP alignment verified.

See [original evidence and limits](verification/2026-10-25-tv-export-comments.md).
Building five ABIs and testing one TV does not cover the device matrix.

## Before publication

| Step | State at this snapshot |
|---|---|
| Documentation and RU/EN screenshots | Complete: 21 RU/EN pairs, 24 new screenshots with manifest; local links/anchors/hashes verified |
| Reconcile `origin/main` | Done: the work is merged as one PR on top of `bb63fed` (pairing HTTP parsing, size-parsing fix, fuzzing, provenance). The `bb63fed` code is covered by JVM tests and fuzz-input replay; the TV verified build .1 without it |
| Inherited-code permissions | ARDcode permission not obtained; [issue #1](https://github.com/ARDcode/boosty-tv-app/issues/1) remains open without replies. MIT only covers original fork contributions |
| Cloud checks of current code | Run in the PR; the release starts only after they pass |
| Dependabot version updates | Disabled for the fork when checked on 2026-10-25; weekly YAML exists. Enable and inspect bot logs with authorized publication |
| Publication authorization | Documentation preparation does not authorize pushing, tagging, VirusTotal upload or a release |
| Five-APK signing/scans/attestation | Release workflow runs these after checking the current commit; local signing does not replace them |
| Final download check | After publication: website links, hashes/certificate/attestation, update-in-place and Q2 |

Read-only GitHub check on 2026-10-25: Releases empty, Pages enabled, active rulesets
for `main` and release tags. A missing legacy branch-protection API response does
not imply no ruleset. GitHub settings were not changed.

The workflow derives versions from tags; local .0/.1 builds do not create public
tags. Account for the installed versionCode when updating: the in-app updater
offers only higher codes. See [publishing procedure](RELEASING.en.md).

## Remaining verification boundaries

Android 6–13, other TVs/ABIs and devices with 16-KiB memory pages were not separately
tested. B2 verified 35 seconds in the background and automatic open-player restore
after process termination; separate Activity recreation and hours-long waits were
not tested. The chosen discussions required no further live pages; pagination
logic has synthetic coverage. No public APK exists yet for the complete update path.
These are documented limits, not checks reported as passed.
