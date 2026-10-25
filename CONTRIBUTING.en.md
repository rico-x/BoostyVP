# Contributing to BoostyVP

[Русский](CONTRIBUTING.md) · [Documentation](docs/README.en.md)

Start with the [roadmap](docs/ROADMAP.en.md) and Issues. Discuss major features
first. Do not bypass post access or add advertising/telemetry or image processing
without a separate decision.

1. Use a separate branch and a small, complete change.
2. Preserve package ID, signing identity, existing preferences and library migrations.
3. Test failures, focus and remote controls; run [local checks](docs/DEVELOPMENT.en.md).
4. New functionality and fixes come with automated tests: unit tests, plus TV
   tests for remote control, player and screens. Parsing of external data
   (network responses, pairing requests) is also covered by
   [fuzzing](docs/DEVELOPMENT.en.md#fuzzing).
5. UI changes must update RU/EN packs, the guide and both screenshot sets.
6. State actually executed checks/devices, limitations and substantial LLM help
   in the PR. Compiled tests are not executed tests.

Normal integration uses a PR, required checks and squash merge. Owner publication
is a separate step. Local agents follow [AGENTS](AGENTS.md). Dependency updates
are not automatically merged.

Never attach tokens, accounts, pairing QR, keys, paid content or private URLs.
Owner-subscription documentation screenshots are allowed only under the owner's
specific authorization and [screenshot policy](docs/DEVELOPMENT.en.md#screenshots).
Report vulnerabilities privately: [SECURITY](SECURITY.en.md).

By submitting **your original contribution**, you license it under MIT and
confirm the necessary rights. This does not relicense inherited code:
[LICENSE](LICENSE.en.md). Preserve sources, licenses and notices for third-party
materials. [LLM involvement](docs/AI_ASSISTANCE.en.md) proves neither correctness nor originality.
