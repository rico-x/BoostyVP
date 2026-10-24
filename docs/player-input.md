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

The release package is `io.github.ricox.boostyvp`, labeled **BoostyVP**,
versioned by release date (`YYYY.MM.DD.N`, see [RELEASING.md](RELEASING.md)). It retains its persistent signing key.
It cannot reuse another package's encrypted account storage. Sign in normally
in the new app; uninstalling a previous client is a separate, explicit action.
Updates within BoostyVP retain the package/signing identity and preserve login.

An earlier local iteration named 0.2.0 added player preferences (seek increments, controller timeout,
remembered speed) and a native Menu-key action that only reveals the controls.
The quality picker restores the configured timeout rather than a hardcoded value.
The player suite includes Menu and custom-increment transport regressions; app
navigation tests cover the sidebar, recycled rows, settings persistence and focus
restoration. Preferences tests use only the disposable debug package's storage.

The `Verify and build BoostyVP` workflow runs unit tests, builds instrumentation
APKs and five unsigned release variants. It has read-only repository permissions
and no signing/account secrets. The separate `Build and publish BoostyVP`
workflow signs only after verification, in the restricted `release` environment,
then publishes checksums and provenance. See [RELEASING.md](RELEASING.md).
Never upload login/session data; signing secrets belong only in protected storage.

Run the device regression suite (no Boosty login/network/video required):

```sh
./gradlew :feature:player:connectedDebugAndroidTest
```

The focused regression class is
`io.github.ricox.boostyvp.feature.player.PlayerControlsTest`.

The test asset is a generated, silent, 60-second grey H.264 clip (640x360, 24 fps).
Recreate it with:

```sh
ffmpeg -f lavfi -i color=c=0x242424:s=640x360:r=24 -t 60 \
  -c:v libx264 -profile:v baseline -pix_fmt yuv420p -preset fast \
  -movflags +faststart feature/player/src/androidTest/assets/remote-control-test.mp4
```

Back/transport tests disable controller auto-hide so remote injection/idle waits
cannot race its timer. A separate test explicitly checks timed auto-hide.
Before each player test, an explicit wake key dismisses Google TV's screensaver:
UiDevice.wakeUp() alone does nothing when the display is on but the TV is Dreaming.
