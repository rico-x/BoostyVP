# BoostyVP roadmap

[Русский](ROADMAP.md) · [Documentation](README.en.md)

Updated 2026-10-25 for code `e4de85d`, integrated into local `main`.
Done means implemented with the stated verification, not a published APK.
Earlier incremental records remain in the [original-language history](ROADMAP.history.md).

## Implemented

| Area | Available features |
|---|---|
| Sign-in | Browser bookmark/QR over LAN, phone/code, repeatable sign-in server startup |
| Catalog | Creator subscriptions, paginated New feed, creator pages, filters/sorting, loaded-metadata search |
| Library | History, positions, Watch later, favorite creators/videos, manual/automatic watched markers; per-account isolation, Room v4 |
| Privacy | Independent recording controls, confirmed deletion of selected current-account data groups |
| Posts | Details, text/images, read-only comments/replies; video comments without playback |
| Player | Media3, accelerated seeking, quality, per-creator speed, signed-link recovery, sleep timer, end actions, stream statistics |
| S7–S10 | Configurable CH±/FF/REW/digits, double Back, video geometry, INFO/Stream |
| S14–S17 | MediaSession, Android TV/Projectivy channels, non-secret settings transfer, manual update checks |
| V0–V7, V9–V12 | Card/text sizes, captions, density, focus, full title, creator headers, themes/accents, player appearance |
| Menu and language | Width/density/font/order/visibility, collapsing sidebar; independent RU/EN packs |
| D1 / D4 | Compatible dependency updates retaining minSdk 23; unused HTTP logging removed |

### High-priority fixes

| ID | Result |
|---|---|
| B1 | Expanded sidebar reserves space and shifts the catalog; focus/navigation verified on TCL |
| B2 | Background pause and position/intent preservation; Home and automatic restoration of the open player after process termination verified. Separate Activity recreation and hours-long background waits remain in the test matrix |
| B3 | Ordinary settings save immediately; closing a dialog is not a save confirmation |
| B4 | Preview alternates focus states and demonstrates outline, zoom and transition duration |
| B5 | Export offers a file manager; Cx saved JSON into the chosen folder and cancellation preserved settings |
| B6 | Shared video-card comments action; reading/replies and creator-page focus return verified without a media session |

Evidence: [library, languages, Media3](verification/2026-10-24-library-localization-media3.md),
[B1–B4 and dependencies](verification/2026-10-25-priority-fixes-dependencies.md),
[TV, export and comments](verification/2026-10-25-tv-export-comments.md).
These original-language reports and [release readiness](RELEASE_READINESS.en.md)
state the boundaries of each check.

## Not implemented

| ID | Task | Scope |
|---|---|---|
| A1 | Audio-track and subtitle selection | Only tracks actually available; no placeholder controls |
| S11 | Buffer sizing and device compatibility workarounds | Based on confirmed failures and testing on the specific TV; no forced software decoding |
| S12 | Automatic quality reduction during repeated buffering | Extend existing stall detection and quality selection |
| Q1 | Android/TV/ABI matrix | Android 6–13, other models/firmware; building five APKs is not device verification |
| Q2 | Updating from a public release | End-to-end update with a real published APK and preserved data; synthetic verification is insufficient |
| D2 | Running UI/remote tests in CI | CI currently only compiles test APKs; emulator execution is a separate task |
| D3 | More blog/model/home unit coverage | Additional states, failures and models; requires an explicit owner instruction |
| D1, external step | Enable Dependabot version updates for the fork | Disabled as of 2026-10-25; coordinate with authorized publication of the current catalog and check bot logs |
| E1 | ARDcode permission to apply MIT to inherited code | External request; [issue #1](https://github.com/ARDcode/boosty-tv-app/issues/1) has no reply as of 2026-10-25 |

Technical debt is not active work without a separate instruction. The owner
selects upcoming work; this table sets no deadlines. Before publication, the
separate `origin/main` commit also needs reconciliation with the verified local
branch: [release readiness](RELEASE_READINESS.en.md).

## Depends on service and device capabilities

- C1: global Boosty search needs a confirmed API; local search already exists.
- C2: cloud library synchronization needs a confirmed protocol and data policy.
- C3: live streams need reference streams and protocol support; chat is excluded.
- C4: AFR requires a suitable test TV with multiple display modes.

### Someday

**S13: timeline frame previews** is postponed indefinitely, without a plan
or deadline. The inspected Boosty sample exposed no storyboard/image tracks;
that does not prove their absence for every post.

### Excluded by the owner

V8 (additional row modes), SponsorBlock, DeArrow, YouTube recommendations,
browser LAN-pairing hardening and changes to the broken-Keystore fallback.
These are not mandatory unfinished features. Known storage/HTTP limitations
remain documented in [Security](../SECURITY.en.md).

## Process, quality and security

Development and history stay local; pushing, PRs and publishing require an
explicit owner request. The normal `main` process uses a PR, required checks
and squash merge; a separate explicit instruction can authorize a local transfer.
See [AGENTS](../AGENTS.md), [Development](DEVELOPMENT.en.md), [Publishing](RELEASING.en.md).

Remote, focus, library and migration checks use a separate debug package.
A signed release updates with the same package/key without clearing data.
Real TV testing requires owner authorization. Do not describe compiled test
APKs as executed tests, synthetic API responses as the live service, or one
TCL as the full Android matrix. Historical evidence is not rewritten to fit new results.
