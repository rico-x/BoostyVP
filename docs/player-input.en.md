# Player input and focus

[Русский](player-input.md) · [User controls](USER_GUIDE.en.md#player)

Media3 lives in its own fullscreen ComponentDialog inside a retained navigation
route. Native focus is requested after attachment/window focus, not only in the
View factory: asynchronous loading and Compose windows must not steal remote
input. The underlying catalog retains scroll and selected card.

TvPlayerView preserves ordinary Android transport/D-pad/Home/volume dispatch.
Menu reveals controls without pausing. Back closes stream information, controls,
then the player; optional double Back confirms only the final step. Hidden
Left/Right accumulates/accelerates seeking and applies on release. CH±, FF/REW
and digits are remapped only when enabled.

ON_PAUSE/ON_STOP checkpoint before pausing. ViewModel/SavedStateHandle retain
the current post, position and intent, including autoplay. A new external link
opens a new target. Writes are serialized so an old instance cannot overwrite
an explicit restart. Disabling disk-position recording remains respected.

## Regressions

PlayerControlsTest: delayed loading, native focus, Play/Pause, Menu and Back.
RemoteSeekDeviceTest: steps, accumulation, holding and paused state.
PlaybackReliabilityDeviceTest: keepScreenOn, next-up and sleep timer.
RemoteOptionsDeviceTest: remapping, geometry and INFO.
App PlayerReturnTest checks the underlying catalog. JVM PlaybackSessionTest and
PlayerViewModelTest cover restoration and recording boundaries.

```sh
./gradlew :feature:player:connectedDebugAndroidTest
```

Tests use synthetic state/local clips without a Boosty account. The 60-second
grey H.264 fixture, 640×360/24fps, can be recreated with:

```sh
ffmpeg -f lavfi -i color=c=0x242424:s=640x360:r=24 -t 60 \
  -c:v libx264 -profile:v baseline -pix_fmt yuv420p -preset fast \
  -movflags +faststart feature/player/src/androidTest/assets/remote-control-test.mp4
```

An explicit wake key exits Dreaming; wakeUp() alone is insufficient with the
display on. Back/transport tests control auto-hide; a separate case checks its
timer. DocumentationCaptureTest is opt-in RU/EN next-up capture, not a new live
post test; synthetic data is labeled in docs. See [coverage/limits](RELEASE_READINESS.en.md).
