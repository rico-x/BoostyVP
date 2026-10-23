# Android TV player focus fix

This fork fixes native Media3 player focus after Compose navigation opens a
dialog and asynchronous loading replaces its content with the video player.

- Request native focus after attachment/window focus, not in the view factory.
- Host Media3 in its own fullscreen native ComponentDialog. A Compose owner can
  no longer steal the native player's D-pad focus. Retain the navigation dialog
  and the underlying screen/scroll position, with an explicit close callback.
- Keep standard Android dispatch for transport, D-pad, Home and volume keys.
- Keep existing hide-controls-then-close Back behavior.
- Test delayed loading, pause/play, seeking, D-pad, secondary-window focus recovery and Back.
- Also decode a locally generated H.264 test clip and verify that remote pause
  freezes its position and remote play resumes playback. No account is needed.

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

The test asset is a generated, silent, 60-second grey H.264 clip (640x360, 24 fps).
Recreate it with:

```sh
ffmpeg -f lavfi -i color=c=0x242424:s=640x360:r=24 -t 60 \
  -c:v libx264 -profile:v baseline -pix_fmt yuv420p -preset fast \
  -movflags +faststart feature/player/src/androidTest/assets/remote-control-test.mp4
```

Back/transport tests disable controller auto-hide so remote injection/idle waits
cannot race its timer. A separate test explicitly checks timed auto-hide.
