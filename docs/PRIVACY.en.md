# Data and privacy

[Русский](PRIVACY.md) · [Documentation](README.en.md)

Current-code description as of 2026-10-25, not an anonymity promise or independent audit.

## Network

Boosty and publication CDNs receive ordinary request data: IP, parameters and
API tokens where needed. Requests cover sign-in/refresh, subscriptions, metadata,
text, comments, images and video. Phone/code or a browser session is supplied
to the service, not the fork developer.

There is no project advertising, analytics, telemetry or watch-history collection
server. Viewing content is not sent to an LLM; LLM involvement concerns development.
Boosty, CDN, Android and launcher policies belong to their respective providers.

Manual update checks contact GitHub API/files for `rico-x/BoostyVP`. GitHub receives
request network data, not the Boosty session. The APK contains no GitHub secrets;
there is no automatic installation/download.

## On the TV

| Data | Storage and purpose |
|---|---|
| Session/device/token expiry | Normally Keystore/EncryptedSharedPreferences; weaker failure fallback documented in SECURITY |
| History and positions | Per-account Room `library.db` v4: IDs, creator, title, artwork, position and viewing time |
| Bookmarks and markers | Watch later, favorite creators/videos, watched state; metadata rather than downloaded videos |
| Preferences and images | Appearance/player settings, creator speeds, ordinary image cache |
| Search/queue | Up to 600 metadata entries / 100 videos in session memory; cleared on account switch/process termination |

The database/index do not store full texts or signed media URLs. Open posts,
comments and URLs remain in memory as needed for loading/playback. This does
not claim the absence of every OS network cache. Migration v3 → v4 added favorite
videos without removing existing data.

Three independent switches stop history, position and automatic watched recording.
Disabling recording does not erase old data. Library and privacy deletes selected
**current-account** groups after confirmation. Sign-in, other accounts and server
subscriptions are unchanged. Removing one history entry preserves its position.

Sign-out clears the session, not every local record. Android's clear-data action
removes sign-in, library, caches and preferences. Android system backup is disabled
(`allowBackup=false`).

## System and launcher data

The default New channel sends titles, artwork, creators and app-opening links
for accessible videos to the launcher. Channels/sources/card counts are configurable;
Watch Next is off by default. Sign-out/account switches clear this app's cards.
Tokens and signed media URLs are not passed to the launcher.

The open player exposes title/creator, state, position and controls through Android
MediaSession. Closing releases it; there is no background player.

## Pairing, export and website

The browser transfers its session directly to the TV over LAN. The temporary
HTTP server stops after successful sign-in or leaving the screen. An AES-GCM
envelope does not prevent every attack on the HTTP page/LAN-accessible key:
[Security](../SECURITY.en.md).

Exported JSON only contains allowed player/appearance/channel preferences,
not sign-in, tokens, library, positions or creator speeds. File-manager export
prepares an immutable private-cache copy with temporary read access. A file the
user saves to an external folder is not automatically removed by library cleanup.

The alternative settings-transfer HTTP page is user-started, lasts up to 10 minutes
and closes on leaving the section/backgrounding. Import application requires
confirmation on the TV. Use trusted LAN and do not share its address/QR.
This does not change the browser sign-in protocol.

GitHub Pages is static documentation without project sign-in forms, trackers or
remote fonts. GitHub processes data under its own policies. Never enter a password,
phone or token on the project site, or attach them to Issues.
