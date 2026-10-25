# Saved playlists and logins

Version 0.5.0 saves the active M3U file/URL or Xtream source and its root catalogue on this device. Opening the app restores that cache without contacting the provider or starting playback. Each HTTP network/playback operation still needs approval. The app supplies no channels or subscriptions.

## Using it

- Import a file or URL, or enter your Xtream login. A saved timestamp appears after the library is committed.
- **Refresh playlist** reloads a saved URL/server source. For a file, choose the document again; the app keeps its parsed catalogue without retaining document access.
- **Remove playlist** asks for confirmation, then removes this device's saved source, catalogue and protected login. Reopening the app stays empty until another import.
- Failed refresh/import keeps the working library. Download/parsing can be cancelled. During the final local save the app shows Saving and completes the atomic commit before another operation.
- If protected data is unreadable, source replacement is blocked until explicit removal. The app does not silently reset the database or invent a new key for existing ciphertext.

Only one active source is saved in this slice; importing another successfully replaces it. Series/episodes use the restored login, but episodes are fetched when opened. Offline episode caching, saved navigation/playback position, favourites, search/EPG, scheduled refresh and multiple-source management remain open.

## Protection and schema

`core:storage` uses Room 2.8.5, KSP 2.3.6 and exported schema version 1. Credentials/source URLs and every playback locator are encrypted BLOBs. A random per-source data key uses standard AES-256-GCM per row; Android Keystore wraps it and the source configuration. Source/generation/item associated data rejects ciphertext substitution. Catalogue labels/categories remain in app-private database columns. Existing backup/device-transfer exclusions cover the database; reinstalling requires adding the source again. This does not claim protection from a compromised running app or forensic secure deletion of all filesystem history.

Refresh preserves local IDs for matched provider kind/IDs or exact private M3U locator/occurrence keys. Duplicate entries stay distinct; future favourites/resume must not assume ambiguous matches are reliable. Catalogue replacement is transactional, separate from identity mappings. No raw provider addresses, credentials, bodies or exception causes enter logs/telemetry.

## Verification workflow

JVM tests cover source encoding/redaction/bounds, authenticated encryption, cached restore without network, save failure retention, HTTP consent, file refresh and local commit/cancel behavior. Android fixtures verify actual Room/Keystore reopen, encrypted database contents, failed encryption/transaction rollback, missing keys and tampered addresses, multi-batch identity-preserving refresh, cached phone/TV navigation, refresh and confirmed removal.

CI's [device wrapper](../../scripts/device-tests.sh) runs instrumentation, then a [separate-process probe](../../scripts/persistence-process-test.sh). On its disposable emulator it reinstalls test APKs after Gradle cleanup, seeds a synthetic source, force-stops the app, verifies restoration with a different PID and clears the fixture. Explicit exit propagation and stubbed shell tests prevent timeout/ADB/probe failures from being masked. These scripts can replace app data: do not run them on the owner's interactive emulator without an agreed testing window.

See [progress](../../progress.md) and the delivery PR for actual latest-head results. All fixtures are synthetic. Local device testing is skipped for this slice to preserve the owner's current emulator session.
