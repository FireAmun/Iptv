# Server login

Home now offers **Server login**, **M3U URL** and **File** on both phone and TV, in English/Arabic and dark/light appearance.

Choose Server login and enter a local playlist name, the provider's base server URL (including HTTP/HTTPS and port when supplied), username and password. This route supports Xtream-compatible Player API servers. It is a provider login, not an app account. M3U-only providers continue to use URL/file import. The player supplies no content subscriptions.

Successful login loads live channels, movies and series with their supplied categories. Selecting a series loads its episodes; Back returns to the catalogue and restores the selected series. Missing season/episode metadata stays missing. Failed login, cancelled import or empty series leaves the working library available.

## Current boundaries

- Version 0.5.0 [saves the active source and root catalogue](saved-library.md) using Room/protected connection storage. Startup uses the cached root without a provider request. Episodes are fetched when opened; episode caching and playback resume remain open.
- Password entry is masked and cleared on submission/dismissal. Credentials and generated addresses stay out of shared UI state, documentation, error messages and telemetry.
- Each HTTP login, episode request and playback operation requires explicit unencrypted-connection consent. HTTPS is preferable when the provider supports it.
- Requests stay on the entered server. API redirects, response-supplied alternate hosts/direct-source fields, cookies, cache and logging interceptors are excluded.
- Limits: 8 MiB decompressed per response, depth 32, 512-character provider fields/credentials, 128-character playlist name, 10,000 catalogue/episode entries, five-second parse budget, 20-second calls and 60-second import budget. These are provisional protection limits.
- This is an initial compatibility adapter. Physical boxes and private providers have not been validated; unsupported responses produce fixed local errors.

## Verification

JVM fixtures cover encoded/redacted credentials, auth/account state, catalogue kinds/categories, episode mapping, unsupported optional endpoints, redirects, malformed JSON/UTF-8, bounded parsing, cancellation, atomic replacement, HTTP consent and parent navigation. Device fixtures exercise the real four-field form, rejected login/retry, catalogue, series episode playback and Back/focus on phone and TV, alongside existing M3U/language/appearance checks.

See [progress](../../progress.md) for actual results and delivery status. All test accounts and media are synthetic; never add private provider details to fixtures or screenshots.
