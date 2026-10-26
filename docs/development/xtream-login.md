# Server login

Home now offers **Server login**, **M3U URL** and **File** on both phone and TV, in English/Arabic and dark/light appearance.

Choose Server login and enter a local playlist name, the provider's base server URL (including HTTP/HTTPS and port when supplied), username and password. This route supports Xtream-compatible Player API servers. It is a provider login, not an app account. M3U-only providers continue to use URL/file import. The player supplies no content subscriptions.

Successful login loads live channels, movies and series with their supplied categories. Selecting a series loads its episodes; Back returns to the catalogue and restores the selected series. Missing season/episode metadata stays missing. Failed login, cancelled import or empty series leaves the working library available.

## Current boundaries

- Version 0.5.0 [saves the active source and root catalogue](saved-library.md) using Room/protected connection storage. Startup uses the cached root without a provider request. Episodes are fetched when opened; episode caching and playback resume remain open.
- Password entry is masked and cleared on submission/dismissal. Credentials and generated addresses stay out of shared UI state, documentation, error messages and telemetry.
- Each HTTP login, episode request and playback operation requires explicit unencrypted-connection consent. HTTPS is preferable when the provider supports it.
- Requests stay on the entered server. API redirects, response-supplied alternate hosts/direct-source fields, cookies, cache and logging interceptors are excluded.
- Version 0.7.2 enables OkHttp connection-failure recovery for read-only catalogue/episode GETs, within the existing deadlines. Providers can close a pooled socket while Android parses a large response; the next request must recover on a new connection instead of failing the whole import. Authentication rejection still terminates login, cancellation still cancels the call, and API redirects remain disabled. No custom indefinite retry loop is added.
- Version 0.7.1 resource guards: 64 MiB decompressed per response, 100,000 combined catalogue/episode entries, 120-second parser/call budget and 300-second overall API import budget. Depth 32, 512-character fields/credentials and 128-character playlist names are unchanged. Room save/restore uses the same entry capacity. These guards do not define a subscription tier; timeout and malformed-field messages are separate from capacity errors.
- Recognized account download links can use the [same-server API fallback](m3u-playback.md) when their original M3U endpoint is unavailable. This preserves series episode support and protected source restoration.
- This is an initial compatibility adapter. Physical boxes, provider playback/codec compatibility and endurance remain unverified; unsupported responses produce fixed local errors. An owner-supplied account was used only for read-only import investigation, with no private inputs or responses retained in project documentation or fixtures.

## Verification

JVM fixtures cover encoded/redacted credentials, auth/account state, catalogue kinds/categories, episode mapping, unsupported optional endpoints, redirects, malformed JSON/UTF-8, bounded parsing, cancellation, atomic replacement, HTTP consent and parent navigation. Device fixtures exercise the real four-field form, rejected login/retry, catalogue, series episode playback and Back/focus on phone and TV, alongside existing M3U/language/appearance checks.

The stale-connection regression deliberately closes a previously working socket before the series-category response, verifies one recovery on a new connection and retains same-server targeting. Both server-login and account-link phone/TV journeys inject that failure and verify cached relaunch makes no provider requests. JVM/Android fixture definitions alone are not delivery evidence; consult the live PR's actual reports.

See [progress](../../progress.md) for actual results and delivery status. All test accounts and media are synthetic; never add private provider details to fixtures or screenshots.
