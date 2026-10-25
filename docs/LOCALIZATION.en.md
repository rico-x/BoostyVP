# RU / EN localization

[Русский](LOCALIZATION.md) · [Documentation](README.en.md)

`localization/ru` and `localization/en` are independent JVM modules.
`localization/api` defines typed `UiText` keys and argument counts;
`core/localization` selects/formats packs without Android. ViewModels,
enum captions and browser pages share the language. There is no network or
machine translation at runtime.

Every key needs both translations. Code/tests determine the key count; no manual
count is maintained here. Pairing HTML/JS lives in each pack's resources;
the protocol is identical and offline tests check both languages. Native Media3
controls use the Activity locale. Posts, creator names and external app names
are not translated.

Choices: System default / Русский / English. System default uses Russian for a
Russian OS, otherwise English. The choice lives in interface preferences, outside
the account database; Activity recreation preserves sign-in. Enum labels are
resolved on access.

Use numbered `%1$s`; literal percent signs use `%%`. Do not lose arguments,
leave Russian UI in EN or freeze captions at class initialization. UI changes
must update the [RU guide](USER_GUIDE.md), [EN guide](USER_GUIDE.en.md) and both
screenshot locales.

```sh
./gradlew :core:localization:test testDebugUnitTest :core:domain:test
node build-support/test-pairing-brand.mjs
```

Tests format every message and verify completeness, arguments and English.
`BilingualLibraryUiTest` covers settings, six appearance categories, menu density,
details, favorites and confirmed cleanup using a separate debug package.
CI compiles device tests; TV execution is a separate check.
