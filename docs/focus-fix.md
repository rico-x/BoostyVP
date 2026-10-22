# Android TV player focus fix

This fork fixes native Media3 player focus after Compose navigation opens a
dialog and asynchronous loading replaces its content with an AndroidView.

- Request native focus after attachment/window focus, not in the view factory.
- Add an explicit Compose/native hand-off after the first frame.
- Forward transport/D-pad keys if the Compose host regains focus; do not
  intercept Back, Home or volume keys, or duplicate native-child dispatch.
- Keep existing hide-controls-then-close Back behavior.
- Test delayed loading, pause/play, seeking, D-pad, host-focus recovery and Back.

The release package is `ru.ardcode.boostytv.focusfix`, labeled
**Boosty TV FocusFix**, version `0.02-focusfix.1`. It deliberately installs beside
the upstream app. The upstream private signing key is not available and its
encrypted account data must not be deleted or copied. Sign in normally in the
new app; the original installation remains unchanged.

The `Build FocusFix APK` workflow runs unit tests and produces an unsigned,
minified release APK plus a self-contained player instrumentation APK. It has
read-only repository permissions, needs no signing/account secrets, and does
not publish a GitHub release. Sign the release locally with your own persistent
key before installation. Do not upload the key or any login/session data.

Run the device regression suite (no Boosty login/network/video required):

```sh
./gradlew :feature:player:connectedDebugAndroidTest
```

The focused regression class is
`ru.ardcode.boostytv.feature.player.PlayerControlsTest`.
