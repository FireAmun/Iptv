# Saved playlists and logins

Version 0.7.0 saves multiple M3U file/URL or Xtream playlists and their root catalogues on this device. One playlist is active at a time. Opening the app or switching playlists restores the selected cache without contacting the provider or starting playback. Each HTTP network/playback operation still needs approval. The app supplies no channels or subscriptions.

## Using it

- Import a file or URL, or enter your Xtream login. A saved timestamp appears after the library is committed; adding a playlist preserves the others.
- Open **Playlists** to select a cached playlist, rename it, or remove a particular playlist. File/URL imports start with a localized type and number; Xtream starts with the supplied playlist name. Friendly names are encrypted and never derived from URLs or credentials.
- **Refresh playlist** reloads a saved URL/server source. For a file, choose the document again; the app keeps its parsed catalogue without retaining document access.
- **Remove playlist** confirms the named playlist, then removes only that playlist's catalogue and protected login. Removing the active playlist selects the oldest remaining playlist; removing the last returns to empty setup. Cancelling the confirmation changes nothing.
- Failed refresh/import keeps the working library. Download/parsing can be cancelled. During the final local save the app shows Saving and completes the atomic commit before another operation.
- If protected data is unreadable, imports into that state are blocked. Use Playlists to select another cached playlist or explicitly remove the damaged one. A failed switch preserves the working source. The app does not silently reset the database or invent a new key for existing ciphertext.

Switching clears pending HTTP consent, closes playback on success, and returns to the selected root catalogue. Series/episodes use that playlist's restored login, but episodes are fetched when opened. Offline episode caching, combined catalogues, source editing/reordering, saved navigation/playback position, favourites, search/EPG and scheduled refresh remain open.

## Protection and schema

`core:storage` uses Room 2.8.5, KSP 2.3.6 and exported schema version 2. A manual migration preserves schema-1 source IDs, item IDs, source envelopes, generation IDs and the existing Keystore alias; there is no destructive fallback. Schema 1 remains exported for migration fixtures.

Credentials/source URLs, friendly playlist names and every playback locator are encrypted BLOBs. A random per-source data key uses standard AES-256-GCM per row; Android Keystore wraps it and the source configuration. Source/generation/item associated data rejects ciphertext substitution. Catalogue labels/categories remain in app-private database columns. Targeted deletion retains the shared wrapping key while any saved source remains. After committed last-source deletion, orphan-key cleanup is best effort; a fresh import into an empty database retries cleanup before creating its new envelope. Existing backup/device-transfer exclusions cover the database; reinstalling requires adding sources again. This does not claim protection from a compromised running app or forensic secure deletion of all filesystem history.

Refresh carries the explicit playlist ID and preserves local item IDs for matched provider kind/IDs or exact private M3U locator/occurrence keys. Duplicate entries and separate imports of the same connection stay distinct. Future favourites/resume must not assume ambiguous matches are reliable. Catalogue replacement and active selection are transactional, separate from identity mappings and other playlists. No raw provider addresses, credentials, bodies or exception causes enter logs/telemetry.

## Verification workflow

JVM tests cover source encoding/redaction/bounds, names, authenticated encryption, directory-aware cached switching without network, failed activation retention, targeted rename/removal, HTTP consent, file refresh and local commit/cancel behavior. Android fixtures cover actual schema-1 encrypted migration, independent source catalogues, protected names, explicit refresh targets, last-key cleanup failure/retry, Room/Keystore reopen, transaction rollback, missing keys and tampered addresses, identity preservation and bilingual phone/TV management. Fixture implementation is not a substitute for actual latest-head device results.

CI's [device wrapper](../../scripts/device-tests.sh) runs instrumentation, then a [separate-process probe](../../scripts/persistence-process-test.sh). On its disposable emulator it reinstalls test APKs after Gradle cleanup, seeds two synthetic playlists and a renamed label, force-stops the app, verifies the directory, active selection and both cached roots with a different PID, then clears the fixtures. Explicit exit propagation and stubbed shell tests prevent timeout/ADB/probe failures from being masked. These scripts can replace app data: do not run them on the owner's interactive emulator without an agreed testing window.

See [progress](../../progress.md) and the delivery PR for actual latest-head results. All fixtures are synthetic. Local device testing is skipped for this slice to preserve the owner's current emulator session.
