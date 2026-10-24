# BoostyVP workflow

- The working repository and its complete Git history live here:
  `/Users/rico-x/Documents/GitHub/BoostyVP`.
- Develop, build, test, and create commits locally. Do not push commits, create
  pull requests, publish releases, or upload project artifacts to GitHub unless
  the user explicitly requests that publication. Earlier permission to push is
  superseded by this rule.
- Commits in this repository must use `Rico-X` and
  `6303971+rico-x@users.noreply.github.com`, configured locally. Never change the
  user's global Git identity or corporate repository settings.
- Preserve upstream authorship and attribution. Do not rewrite published
  history without an explicit request.
- Use the local Docker workflow in `build-support/README.md`; keep signing keys,
  account/session data, private video URLs, and user screenshots outside Git.
  Exception approved by the owner: documentation screenshots in `docs/screenshots`
  may show the owner's live subscriptions, never account identity, pairing QR
  codes or video URLs (see `docs/DEVELOPMENT.md`).
- `main` accepts changes only through pull requests with squash merge after the
  required checks pass. Releases are published only by the `Build and publish
  BoostyVP` workflow, on the owner's request; agents may run it themselves then.
- Install updates with the same package and persistent signing key; preserve
  the user's login, preferences, and playback positions.
