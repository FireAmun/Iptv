# Xtream-compatible server login

Date: 25 October 2026. Owner requested a third home-screen source option with playlist name, server URL, username and password. Existing M3U URL/file routes remain available.

## Scope and UI

Use the approved Cinema colours, light/dark appearance and English/Arabic layouts. Present three clear setup choices: Server login, M3U URL, File. Server login opens a labelled four-field form, masks the password, validates required values and clears credentials on submit/dismiss. Do not save credential fields in instance state. TV form scrolls with the keyboard and has explicit initial focus, reachable actions and Back/cancel behavior.

Connect using the Xtream Player API, not by silently constructing a downloadable M3U URL. Authenticate and load real live, movie and series catalogues; keep content kinds distinct in row metadata. Selecting a series retrieves its supplied seasons/episodes, selecting an episode plays it, and Back restores the parent catalogue/selection. Never invent missing movie/series hierarchy for M3U imports.

Playlist name is a local display label. No app account or hosted backend is introduced. This slice is session-only, like current sources; saved credentials wait for the separate Keystore/Room persistence slice.

## Data and privacy

`core:data` owns a protected connection object and bounded streaming API parser. No credential-bearing data class/toString, response logging, interceptor logging, raw error causes, cookies, cache or request redirection. Encode username/password as query parameters and path segments; derive playback URLs from the entered server, not response-supplied alternate hosts or direct-source fields. Base URL accepts HTTP/S, port and path prefix; reject embedded credentials, query and fragment. HTTP login/import/episode retrieval and playback require the existing per-operation consent.

Validate auth and active/expiry state before catalogue requests. Surface fixed local errors for invalid input, bad credentials, inactive/expired accounts, malformed/unsupported response, network failure and empty catalogue. Failed/cancelled replacements leave the working library and its private connection intact. Successful replacements commit catalogue and source metadata together; generation checks prevent stale updates.

Provisional limits: 8 MiB decompressed response, depth 32, 512-character provider labels, 128-character playlist name, 512 characters per credential, 10,000 categories or catalogue/episode entries, five-second parse budget per response. Calls are cancellable with 20-second call/8-second connect/read limits and a 60-second import budget. Reject malformed UTF-8/JSON. Empty arrays/objects/null are accepted only as explicit empty collections; unsupported optional movie/series/category actions may return 404/405, while auth/other server errors fail safely.

Keep credentials only in private ViewModel/data-layer memory; UI state contains safe labels, content kind, selection and navigation flags. Use synthetic accounts and self-generated video for tests. Do not read, record or publish the owner's private provider details. The owner's interactive emulator is left untouched until an app update/test window is available; do not start another emulator alongside it.

## Verification and delivery

JVM tests cover encoding/redaction, auth variants and expiry, all content kinds, episode mapping, category/metadata gaps, size/depth/field/time limits, cancellation, non-JSON/error responses, redirects, atomic replacement, consent and parent navigation. Device tests exercise four-field login, synthetic playback, series/episode/back and safe failed login on mobile/TV, preserving existing language/appearance/import tests. Inspect actual app layouts/RTL and run latest-head cloud phone/TV suites. Independent review, PR/check/merge/delete workflow and progress handoff remain required.

References: [Player API implementation](https://github.com/gtaman92/XtreamCodesExtendAPI/blob/master/player_api.php), [XC_VM player API](https://vateron-media.github.io/XC_VM/2.6.1/_media/swagger-ui.html?spec=player), [Gson streaming reader source](https://github.com/google/gson/blob/main/gson/src/main/java/com/google/gson/stream/JsonReader.java). Only streaming reader APIs are used; no reflective model deserialization.
