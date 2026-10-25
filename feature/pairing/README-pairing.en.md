# Sign-in and pairing protocol

[Русский](README-pairing.md) · [User sign-in guide](../../docs/USER_GUIDE.en.md#sign-in)

## On-TV sign-in

PhoneLoginViewModel holds phone, six-digit code and verification ID in memory.
One device UUID is used for both requests. The implementation follows Boosty's
web forms: POST `/auth/phone/verification_code/send`, then PUT
`/auth/phone/verification_code/confirm` with the same device_id/device_os=web,
the first response's opaque code and the entered sms_code. Another session's
bearer token is not submitted. CAPTCHA/rate limits are reported safely, not
bypassed. Tokens are saved before opening the catalog.

## Browser sign-in

The screen starts a local java.net HTTP server, normally at
`http://<TV-IP>:8765/link`. It stops after successful import or leaving the screen.
This is distinct from the 10-minute settings-transfer server; sign-in has no
such timeout. Reopening uses the short address and a new internal key.

The bookmark runs on the exact HTTPS Boosty domain, validates its local callback,
reads the session and seals it with AES-GCM. It returns ciphertext in the `/link`
fragment; the page removes the fragment and asks for confirmation before POST
`/pair`. Legacy `/p/<key>` and manual form import remain supported. The 32-byte
key is available to the local page; HTTP does not authenticate the TV. This is
a trusted-LAN flow without external relay/token storage. See [limitations](../../SECURITY.en.md).

RU/EN browser HTML/JS lives in `localization/{ru,en}/src/main/resources`.
Bookmarks, text and instructions use the selected locale. Never log or retain
passwords/codes/sessions/private URLs in reports.

## Verification

PhoneLoginViewModelTest, PhoneAndRefreshProtocolTest, PairingPageTest,
PairingServerTest, MiniHttpServerTest and offline handoff tests for both languages.
PhoneLoginRemoteTest covers IME/remote use. ChromeBookmarkTest and LiveRefreshTest
require explicit opt-in and a suitable separate test environment. Compiled APKs
are not evidence of successful execution against the real service.
