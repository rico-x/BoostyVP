# OpenSSF Best Practices: ответы для анкеты

Подготовленные ответы для анкеты уровня **passing** на
[bestpractices.dev](https://www.bestpractices.dev). Анкету заполняет владелец:
вход через GitHub → «Add project» → `https://github.com/rico-x/BoostyVP`, затем
статус и обоснование из таблицы ниже для каждого критерия. Обоснования на
английском — так их проще проверить рецензентам анкеты. После сохранения сайт
выдаёт номер проекта; бейдж добавляется в README:

```markdown
[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/<ID>/badge)](https://www.bestpractices.dev/projects/<ID>)
```

## Что мешает уровню passing

- **`floss_license` (MUST) — не выполнен.** MIT распространяется только на
  оригинальные изменения форка. Разрешение на лицензию для унаследованного кода
  [запрошено у автора исходного проекта](https://github.com/ARDcode/boosty-tv-app/issues/1)
  и пока не получено. До ответа проект будет в статусе **in progress**
  (2 балла в OpenSSF Scorecard), после — **passing** (5 баллов).
- **`static_analysis_fixed` (MUST)** — один алерт CodeQL высокого уровня
  (запасное хранение сессии при сбое Android Keystore) закрыт как принятый риск
  по решению владельца. Рецензент анкеты может счесть это невыполнением.
- Необязательные пункты: `english` (документация на русском),
  `version_semver` (версии по дате — осознанно), `test_most` (покрытие не
  измеряется), `warnings_strict` (предупреждения не считаются ошибками).

Ответы отражают состояние `main` на 2026-10-24. Перед отправкой анкеты
проверьте ссылки и пункты, помеченные как зависящие от времени.

## Ответы

`R` = `https://github.com/rico-x/BoostyVP`.

### Basics

| Criterion | Level | Status | Justification |
|---|---|---|---|
| description_good | MUST | Met | The README explains what the software does: an independent Android TV client for Boosty subscriptions. `R/blob/main/README.md` |
| interact | MUST | Met | The README and the project site explain how to obtain the app, report problems and contribute: `https://rico-x.github.io/BoostyVP/`, `R/issues`, `R/blob/main/CONTRIBUTING.md` |
| contribution | MUST | Met | The contribution process (branch, pull request, required checks) is in `R/blob/main/CONTRIBUTING.md` and `R/blob/main/docs/DEVELOPMENT.md` |
| contribution_requirements | SHOULD | Met | Requirements for contributions (tests, device checks, licensing, no secrets) are listed in CONTRIBUTING.md and the pull request template |
| floss_license | MUST | Unmet | Original fork contributions are MIT. The inherited upstream code has no license grant yet; permission was requested: `https://github.com/ARDcode/boosty-tv-app/issues/1`. See `R/blob/main/LICENSE.md` |
| floss_license_osi | SUGGESTED | Met | Original contributions use MIT, an OSI-approved license (`R/blob/main/LICENSES/MIT.txt`) |
| license_location | MUST | Met | `R/blob/main/LICENSE.md` with the license texts in `R/tree/main/LICENSES` |
| documentation_basics | MUST | Met | README, user guide `R/blob/main/docs/USER_GUIDE.md`, developer guide `R/blob/main/docs/DEVELOPMENT.md` |
| documentation_interface | MUST | Met | The user guide documents the remote-control interface and settings; the pairing HTTP interface is documented in `R/blob/main/feature/pairing/README-pairing.md` |
| sites_https | MUST | Met | The repository, releases and the project site (`https://rico-x.github.io/BoostyVP/`, HTTPS enforced) use HTTPS only |
| discussion | MUST | Met | GitHub Issues and Discussions are searchable, addressable by URL and open to new participants |
| english | SHOULD | Unmet | Documentation is in Russian for the project's users; bug reports in English are accepted |
| maintained | MUST | Met | The project is actively developed (see the commit history and `R/blob/main/docs/ROADMAP.md`) |

### Change control

| Criterion | Level | Status | Justification |
|---|---|---|---|
| repo_public | MUST | Met | Public Git repository `R` |
| repo_track | MUST | Met | Git tracks every change, its author and time |
| repo_interim | MUST | Met | All changes land through public pull requests on `main`, not only at release time |
| repo_distributed | SUGGESTED | Met | Git |
| version_unique | MUST | Met | Each release has a unique version: the release date and the number of the release that day, `YYYY.MM.DD.N`, assigned by the release workflow (`R/blob/main/docs/RELEASING.md`) |
| version_semver | SUGGESTED | Unmet | Calendar versioning is used deliberately: Android needs a monotonic versionCode and users see the release date |
| version_tags | SUGGESTED | Met | Every release is tagged `v<version>`; a repository ruleset forbids moving or deleting these tags |
| release_notes | MUST | Met | Each release carries human-readable notes from `R/blob/main/release-notes/next.md`; history in `R/blob/main/CHANGELOG.md` |
| release_notes_vulns | MUST | N/A | No publicly known vulnerability has been fixed yet; such fixes would be listed in the release notes |

### Reporting

| Criterion | Level | Status | Justification |
|---|---|---|---|
| report_process | MUST | Met | Bugs are reported through GitHub Issues with templates: `R/issues/new/choose` |
| report_tracker | SHOULD | Met | GitHub Issues |
| report_responses | MUST | Met | No bug reports were left unacknowledged in the last 12 months (time-dependent: re-check before submitting) |
| enhancement_responses | SHOULD | Met | No enhancement requests were left unanswered in the last 12 months (time-dependent) |
| report_archive | MUST | Met | Issues and their discussion stay public and searchable |
| vulnerability_report_process | MUST | Met | `R/blob/main/SECURITY.md` |
| vulnerability_report_private | MUST | Met | GitHub private vulnerability reporting is enabled: `R/security/advisories/new` |
| vulnerability_report_response | MUST | Met | No vulnerability reports were received in the last 6 months (time-dependent) |

### Quality

| Criterion | Level | Status | Justification |
|---|---|---|---|
| build | MUST | Met | Gradle wrapper builds everything; a Docker builder gives the same toolchain locally (`R/blob/main/build-support/README.md`) |
| build_common_tools | SUGGESTED | Met | Gradle, the standard Android build system |
| build_floss_tools | SHOULD | Met | OpenJDK, Kotlin, Gradle and the Android Gradle Plugin are open source; the Android SDK platform is used under its own license, as every Android app does |
| test | MUST | Met | JUnit and Jazzer tests run with Gradle; device tests run on a real TV (`R/blob/main/docs/DEVELOPMENT.md`) |
| test_invocation | SHOULD | Met | `./gradlew testDebugUnitTest` |
| test_most | SUGGESTED | Unmet | Domain, network, player and release tooling are covered, but coverage is not measured yet |
| test_continuous_integration | SUGGESTED | Met | Every pull request and push to `main` runs the tests: `R/actions/workflows/verify-build.yml` |
| test_policy | MUST | Met | CONTRIBUTING.md: new functionality and fixes come with automated tests |
| tests_are_added | MUST | Met | Recent changes added tests with the functionality, e.g. `PlaybackReliabilityDeviceTest`, `PairingRestartDeviceTest`, the release tooling tests and the fuzz tests |
| tests_documented_added | SUGGESTED | Met | CONTRIBUTING.md and the pull request template checkbox |
| warnings | MUST | Met | The Kotlin compiler reports warnings on every build; CodeQL (`security-extended`) runs on every pull request |
| warnings_fixed | MUST | Met | Production code compiles without warnings; a clean build on 2026-10-24 showed two deprecation warnings, both in device-test code (time-dependent: check the latest CI log before submitting) |
| warnings_strict | SUGGESTED | Unmet | Warnings are not yet treated as errors |

### Security

| Criterion | Level | Status | Justification |
|---|---|---|---|
| know_secure_design | MUST | Met | The maintainer applies secure design: release signing separated from pull request builds, pinned signer certificate, AES-GCM sealed pairing, least-privilege workflow tokens (`R/blob/main/SECURITY.md`) |
| know_common_errors | MUST | Met | Known risks are documented in SECURITY.md; CodeQL `security-extended`, dependency review and fuzzing guard against common errors |
| crypto_published | MUST | Met | AES-256-GCM (pairing), Android Keystore AES-256-GCM (session storage), platform TLS |
| crypto_call | SHOULD | Met | `javax.crypto`, AndroidX Security and the platform TLS stack; no custom cryptography |
| crypto_floss | MUST | Met | Platform crypto (Conscrypt/BoringSSL) and AndroidX Security are open source |
| crypto_keylength | MUST | Met | 256-bit AES keys |
| crypto_working | MUST | Met | No broken algorithms (no MD5, SHA-1, DES or RC4 for security) |
| crypto_weaknesses | SHOULD | Met | No algorithms with known serious weaknesses are used |
| crypto_pfs | SHOULD | Met | Network traffic uses OkHttp's default TLS configuration with ECDHE key exchange |
| crypto_password_storage | MUST | N/A | The app stores no user passwords; only session tokens, in EncryptedSharedPreferences |
| crypto_random | MUST | Met | Pairing keys and IVs come from `SecureRandom` |
| delivery_mitm | MUST | Met | Releases are served over HTTPS, APKs are signed with a pinned certificate, with `SHA256SUMS` and a SLSA provenance bundle (`*.intoto.jsonl`) |
| delivery_unsigned | MUST | Met | Hashes are never retrieved over HTTP |
| vulnerabilities_fixed_60_days | MUST | Met | No known unpatched vulnerabilities; Dependabot alerts: 0 (time-dependent) |
| vulnerabilities_critical_fixed | SHOULD | Met | No critical vulnerabilities are known |
| no_leaked_credentials | MUST | Met | Secret scanning with push protection; signing keys exist only as release environment secrets |

### Analysis

| Criterion | Level | Status | Justification |
|---|---|---|---|
| static_analysis | MUST | Met | CodeQL on every push, pull request and weekly: `R/actions/workflows/codeql.yml` |
| static_analysis_common_vulnerabilities | SUGGESTED | Met | CodeQL `security-extended` query suite |
| static_analysis_fixed | MUST | Met | All CodeQL alerts are triaged. One high-severity alert (cleartext fallback when the Android Keystore fails) is an accepted risk by the maintainer's decision, documented in SECURITY.md; it needs a device with a failed Keystore and app-private storage access |
| static_analysis_often | SUGGESTED | Met | On every commit to `main` and every pull request |
| dynamic_analysis | SUGGESTED | Met | Jazzer fuzzing of the pairing HTTP input, session values and Boosty API responses: `R/actions/workflows/fuzz.yml` |
| dynamic_analysis_unsafe | SUGGESTED | N/A | Kotlin and Java are memory-safe |
| dynamic_analysis_enable_assertions | SUGGESTED | Met | Fuzz checks assert invariants on every input |
| dynamic_analysis_fixed | MUST | Met | Fuzzing findings are fixed and kept as regression inputs (time-dependent: check the Fuzzing workflow before submitting) |
