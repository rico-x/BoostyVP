# Управление и фокус плеера

[English](player-input.en.md) · [Пользовательское управление](USER_GUIDE.md#плеер)

Media3 размещён в собственном полноэкранном ComponentDialog внутри сохраняемого
маршрута навигации. Запрос native focus выполняется после attachment/window focus,
а не только при создании View: асинхронная загрузка и Compose-окно не должны
забирать пульт. Каталог под маршрутом сохраняет прокрутку и выбранную карточку.

TvPlayerView сохраняет обычную Android-доставку transport/D-pad/Home/volume.
Menu показывает панель без паузы. Back закрывает сведения, затем панель,
потом плеер; опция двойного Back подтверждает только последний шаг.
Скрытые ←/→ используют накопление/ускорение seek и применяют его на отпускании.
CH±, FF/REW и цифры переназначаются только при включённой настройке.

ON_PAUSE/ON_STOP сохраняют checkpoint перед паузой. ViewModel/SavedStateHandle
держат текущую публикацию, точку и намерение, включая autoplay. Новая внешняя
ссылка открывает новый target. Записи сериализованы, старый экземпляр не должен
затереть явный перезапуск. Приватность отключённой записи на диск сохраняется.

## Регрессии

PlayerControlsTest: задержка загрузки, native focus, Play/Pause, Menu, Back.
RemoteSeekDeviceTest: шаги, накопление, удержание и сохранение состояния паузы.
PlaybackReliabilityDeviceTest: keepScreenOn, next-up, таймер сна.
RemoteOptionsDeviceTest: переназначения, геометрия, INFO.
App PlayerReturnTest проверяет каталог под плеером. JVM PlaybackSessionTest
и PlayerViewModelTest проверяют восстановление и границы записи.

```sh
./gradlew :feature:player:connectedDebugAndroidTest
```

Тесты используют синтетические состояния/локальные клипы без Boosty-аккаунта.
Grey H.264 fixture 640×360/24fps, 60 с можно воссоздать:

```sh
ffmpeg -f lavfi -i color=c=0x242424:s=640x360:r=24 -t 60 \
  -c:v libx264 -profile:v baseline -pix_fmt yuv420p -preset fast \
  -movflags +faststart feature/player/src/androidTest/assets/remote-control-test.mp4
```

Wake key выводит ТВ из Dreaming; одного wakeUp() при включённом дисплее мало.
Для регрессий Back/transport автоскрытие контролируется явно, таймер проверяется
отдельно. DocumentationCaptureTest — opt-in съёмка RU/EN-next-up, а не новый
live-тест публикации; его синтетические данные помечаются в документации.
Полный текущий охват/ограничения: [готовность](RELEASE_READINESS.md).
