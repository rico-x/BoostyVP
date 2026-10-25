# Security

[Русский](SECURITY.md) · [Documentation](docs/README.en.md)

Development takes place on `main`; after publication the latest release is supported.
There is no fixed SLA, independent audit or guarantee of zero vulnerabilities.

## Report a vulnerability

Use [GitHub private reporting](https://github.com/rico-x/BoostyVP/security/advisories/new).
If unavailable, open an issue only requesting a secure contact, **without**
exploit details or secrets. Ordinary bugs belong in Issues. Provide app version,
Android/ABI, minimal reproduction and impact. Remove tokens, cookies, passwords,
keys, pairing QR/addresses, paid content and signed media URLs from evidence.

## Known limitations

- Browser sign-in uses a temporary TV HTTP server. Encrypting the payload does
  not authenticate the HTTP page; its key is available on the LAN. Use a trusted
  network, do not expose the port and do not share the address/QR.
- Broken Keystore falls back to ordinary private preferences, logging an error
  without a separate UI warning. Protection is weaker on rooted/compromised OSes.
  The format/fallback are retained by owner decision.
- Local library/preferences are not separately encrypted. Confirmed selective
  cleanup is implemented; deleting history does not delete its position.
  Sign-out is not a full data wipe.
- File-manager export grants temporary reading of one settings copy without
  tokens. LAN settings transfer uses HTTP and requires a trusted network.
- The API is unofficial. Boosty/CDNs/the OS are outside project control;
  compatibility may change.

LAN-pairing hardening and changes to the Keystore fallback were excluded from
the plan by the owner. This records a decision, not elimination of those risks.

## Release protection

PR checks receive no signing key. A separate `main` job signs after Verify in the
`release` environment. Temporary keys are removed before artifact upload.
The certificate is pinned in `build-support/release-config.json`. Published
APKs/tags are not overwritten; Actions are pinned to commit SHAs.

Every release ships an SLSA provenance file (`*.intoto.jsonl`). Parsing of
network responses and incoming pairing requests is fuzzed with Jazzer:
[development](docs/DEVELOPMENT.en.md#fuzzing).

ClamAV, VirusTotal, CodeQL, dependency review, Dependabot, fuzzing and rulesets supplement
review; they do not prove safety. YAML does not prove a completed run or enabled
fork version updates. See [release readiness](docs/RELEASE_READINESS.en.md)
for actual status and [APK verification](docs/RELEASING.en.md#apk-verification).
