# Signing in

The TV has two routes:

- **Войти на этом ТВ** — native phone number and six-digit code fields. The main
  button requests the code, then confirms it. Focus opens the TV keyboard;
  D-pad Up/Down moves through the controls after dismissing the IME with Back.
- **С телефона или компьютера** — browser pairing at `http://<TV-IP>:8765/link`.
  Instructions adapt to Android, iPhone/iPad or a computer. A desktop can save
  the ready-made bookmark by dragging it onto its bookmarks bar.

## Native phone login

`PhoneLoginViewModel` holds phone, code and server verification ID only in memory.
It generates one device UUID for both requests. `BoostyPhoneLogin` uses the same
form-encoded requests as Boosty's public web client (checked 2026-10-19):

- `POST /auth/phone/verification_code/send`: phone, device_id, device_os=web.
- `PUT /auth/phone/verification_code/confirm`: the same values, the opaque code
  from the first response, and the six-digit sms_code entered by the user.

The service uses the unauthenticated API client and never submits bearer tokens
from another session. CAPTCHA requirements and rate limits are surfaced as safe
messages; challenges are not bypassed. A confirmed access/refresh pair is saved
before opening the feed.

## Browser pairing

The local java.net server starts when the QR screen opens and stops after one
successful import or when that screen is left. There is no time limit. Reopening
uses the same short link and a fresh internal key.

The saved bookmark runs on exact HTTPS Boosty, validates the local callback,
reads the session and seals it with AES-GCM. It returns ciphertext in a fragment
to `/link`; the page removes the fragment and asks the user to confirm before
POSTing ciphertext to `/pair`. Legacy keyed `/p/<key>` callbacks and manual form
submissions remain supported.

The 32-byte key is available to the local page. This is a trusted-LAN flow; HTTP
itself does not authenticate the TV. No external relay or third-party token
storage is involved.

## Checks

- `PhoneLoginViewModelTest`: requests, confirmation, cooldown and state resets.
- `PhoneAndRefreshProtocolTest`: HTTP methods, fields, token rotation and 401 replay.
- `PhoneLoginRemoteTest`: native fields, TV IME and remote activation.
- `ChromeBookmarkTest`: opt-in saved-bookmark test in Android Chrome.
- `LiveRefreshTest`: opt-in real refresh and authenticated request after TV login.
