# Multiple saved playlists

Continue the approved P3 roadmap from merged Amar Iptv PR #8. Extend the existing local import/storage flow; no backend or account is introduced.

## User behavior

- Adding a file, M3U URL or Xtream login saves a separate playlist and makes it active. Other saved playlists remain intact.
- Phone and TV gain a Playlists manager listing friendly names and the active selection. Select one cached playlist at a time; switching performs no provider request and never starts playback.
- Rename any playlist with a trimmed, nonempty name of at most 128 characters and no control characters. File/URL imports initially use a localized type and stable ordinal; Xtream uses the supplied playlist name. Never derive visible names from private URLs or usernames.
- Refresh replaces only the active playlist's catalogue, preserving its reliable matched IDs and friendly name. File refresh still asks for a document.
- Confirm removal of the named playlist. Other playlists and credentials survive. Removing the active playlist selects the oldest remaining saved playlist; removing the last returns to empty setup. A corrupt remaining playlist can show a recoverable storage error without undoing explicit deletion or deleting other playlists.
- Switching discards episode navigation and pending HTTP consent and closes playback on success. Failed switching keeps the working playlist. Disable management during imports/local commits; failed/cancelled imports retain every committed playlist.

## Storage and error handling

Keep the application ID, database name, Keystore alias, source UUIDs, existing encrypted envelopes, generation IDs and channel IDs. Room schema 2 adds source ownership to catalogue rows, encrypted display-name metadata and an active-source pointer. A manual 1-to-2 migration assigns existing rows to their existing source and preserves the active selection. Retain exported schema 1 and add schema 2; no destructive fallback.

The directory exposes only opaque IDs, optional display names, type and stable ordinal. Friendly labels are encrypted separately from connection details, so renaming does not change provider identity or invalidate refresh matching. A source-specific label/config failure must not erase other sources. Missing keys fail closed; explicit targeted removal remains available. Retain the shared wrapping key until no saved sources remain; deleting one playlist must never destroy another playlist's key.

`LibraryPersistence` owns directory, activation, rename and targeted removal. Room/Keystore stays in `core:storage`; JVM adapters and app state tests retain their boundary. Operations are serialized through the existing shared store mutex and local commit boundary. Catalogue/item identity operations are source-scoped; active selection is transactional. Directory labels never contain derived credentials, and protected object representations/logging stay redacted.

## Verification and scope

Use JVM tests for naming rules and observable switching/import/error/consent state. Disposable Android fixtures verify two-source retention, isolated refresh/removal, encrypted rename, cold reopen/selection, failed activation and a real version-1 encrypted database migration. Exercise phone/TV selection and rename with touch/D-pad, Arabic/English and dark/light modes. Extend the separate-process probe to retain multiple sources and the selected source across force-stop. Do not run destructive instrumentation on the owner's emulator.

Version 0.7.0/code 7 is intended after verification. No combined catalogue, source editing/reordering, automatic refresh, search/EPG, favourites or playback resume is added. Actual CI/device evidence and final integration belong in the delivery PR and progress tracker. Keep the prior unexplained shutdown and environment restrictions.

Migration reference: [Android Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions). Keep the existing pinned Room 2.8.5 toolchain; this slice does not migrate to Room 3.
