# Saved active library

Owner approved encrypted source saving and Room after PR #6. This is the P3 persistence slice, not completion of P3/P4.

## Behavior

Keep one active source and its root catalogue across process death. Restore the cached library without automatic provider requests or restored HTTP consent. File imports retain the parsed catalogue; refreshing a file asks the user to choose it again. URL and Xtream sources retain protected connection details for explicit manual refresh. Series fetch their episodes through the restored connection; offline episode caching and saved navigation/playback position remain later work.

Add saved/cache-age status, Refresh playlist and Remove saved playlist to phone/TV in both languages and appearances. Removal requires confirmation and clears the active library/credentials. Failed/cancelled downloads/parses/encryption leave the old committed snapshot intact. Imports remain cancellable until the final local save (bounded encryption/staging plus database activation); show Saving and disable cancellation while that commit completes. Process interruption exposes either the old or new committed snapshot, never partial rows. Missing/corrupt keys fail closed with a fixed recoverable storage error; do not silently destroy or overwrite unreadable saved data.

## Storage and boundaries

Retain JVM `core:data` and add Android `core:storage` for Room/Keystore, avoiding a breaking conversion of existing parser/loader test tasks. `LibraryPersistence` and protected source serialization live in data; Android storage implements the boundary and app wiring supplies it to the ViewModel. Current parser limits stay unchanged. No cloud, WorkManager scheduling, search/EPG, multiple-source management or billing is added.

Room version 1 stores the active source, root catalogue entries and source-scoped identity mappings. Refresh stages a complete bounded encrypted snapshot, then atomically replaces entries/source metadata in a transaction. Stable local item IDs match reliable provider kind/ID or a private keyed digest of the exact M3U locator plus occurrence; duplicates stay distinct and labels alone never merge items. Removed identity mappings remain separate from replaceable catalogue rows for later user-state work. No favourites/resume behavior is claimed here.

Credentials and all stream/source URLs are encrypted BLOBs. A random per-source AES-256 data key encrypts row URLs with random GCM IVs and source/item associated data. Android Keystore AES-GCM wraps that data key and the protected source configuration once, avoiding one Keystore IPC per channel on large catalogues. Original labels/categories remain in app-private Room columns for future indexing. Connection/entry objects have protected string representations and no raw exception causes. Removal also deletes the wrapping key. Backup/device-transfer exclusions remain in force. No database query logging or telemetry is introduced.

Use standard AES/GCM/NoPadding and AndroidKeyStore rather than deprecated encrypted-preferences APIs. Room 2.8.5 and KSP 2.3.6 preserve the current Kotlin/AGP baseline; KSP 2.3.6 includes Windows/AGP9 fixes before the later Kotlin-2.3 language bump. Export Room's schema and use no destructive-migration fallback.

## Verification

JVM tests cover protected source serialization, cipher tamper/AAD checks, restore-without-network, persistence failures, refresh identity/atomic replacement and commit/cancel behavior. Android fixtures exercise real Room/Keystore reopen, encryption at rest, transactional rollback, missing/tampered key recovery, file/URL/Xtream cached restore, refresh/removal and phone/TV UI. CI additionally seeds a synthetic source, force-stops the target, verifies it from a different process ID, then clears the fixture. Preserve the owner's running emulator; use cloud devices and inspect actual latest-head results before SHA-bound PR merge.

Sources checked: [Room releases](https://developer.android.com/jetpack/androidx/releases/room), [KSP releases](https://github.com/google/ksp/releases), [Android cryptography](https://developer.android.com/privacy-and-security/cryptography), [Keystore](https://developer.android.com/privacy-and-security/keystore). The existing [architecture](2026-10-24-iptv-architecture-design.md) remains the broader roadmap.
