# Multiple playlists implementation plan

> **For agentic workers:** Execute inline with the executing-plans workflow. Review storage and UI changes independently before delivery.

**Goal:** Save, select, rename and remove independent cached playlists on phone and TV.

**Architecture:** Extend the existing persistence contract and Room database with source ownership and an active pointer. Keep one displayed catalogue and serialize local operations in the ViewModel; add focused phone/TV manager components.

**Tech Stack:** Existing Kotlin/Compose, Room 2.8.5, Coroutines and Android Keystore. No new runtime dependency.

**Spec:** [Multiple-playlist design](../specs/2026-10-26-multiple-playlists-design.md).

## Constraints

- Preserve application ID `com.fireamun.iptv.prototype`, `library.db`, alias `iptv.library.v1`, schema-1 ciphertext and IDs.
- No destructive migrations, provider requests during switching, raw source data in logs or owner-emulator instrumentation.
- Normal branch/PR/exact-head CI/review/merge/delete workflow remains mandatory.

## 1. Contracts, state and regression tests

- [x] Add directory summaries and source-name validation to `core:model/LibraryModels.kt`; add persistence directory/activation/rename/removal methods and a saved source ID in `core:data/LibraryPersistence.kt`.
- [x] First add tests for blank/control/overlong names and two-source state using a small map-backed persistence fixture. Require these observable behaviors:

```kotlin
model.selectSource(firstId)
advanceUntilIdle()
assertEquals(firstId, model.state.value.activeSourceId)
assertEquals("First", model.state.value.channels.single().title)
assertNull(model.playback.value)
assertFalse(model.state.value.needsHttpApproval)
```

- [x] Run `:core:model:test :app:testDebugUnitTest` and confirm new feature tests fail before implementing the ViewModel operations. Update existing persistence fixtures to the explicit contract.
- [x] Implement directory-aware boot restore, separate-source imports, serialized selection/rename/targeted deletion and safe errors. Keep network import cancellation and atomic local commits intact. Repeat JVM checks until green.

## 2. Source-scoped Room schema and migration

- [x] Add Android tests using the real `RoomLibraryStore`: save A/B, reopen B, select A, refresh A, rename A, remove B and verify A remains. Corrupt B's locator and require failed activation to preserve A.
- [x] Create a synthetic encrypted schema-1 database with SQLite, unchanged envelope/AAD/key and known channel ID; open through schema 2 and assert:

```kotlin
assertEquals(42, store.restore()!!.entries.single().channel.id)
assertEquals("https://example.invalid/legacy", store.restore()!!.entries.single().address!!.reveal())
assertEquals(1, store.directory().sources.size)
```

- [x] Extend `LibraryDatabase.kt`: source metadata, `EntryRow.sourceId`, source-filtered DAO mutations and active-pointer table. Add `LibraryMigration.kt` with explicit 1-to-2 SQL and register it in the builder.
- [x] Update `RoomLibraryStore.kt`: extract source decoding, preserve source-specific identities, encrypt labels, perform atomic activation/deletion and retain the wrapping key while other sources exist. Export schema 2 without modifying schema 1.
- [ ] Build test APK/lint; run new Android fixtures on disposable cloud devices with the full suite. Verify actual migration data and crypto retention, not only schema creation.

## 3. Phone/TV manager

- [x] Add `MobileSources.kt` / `TvSources.kt`: selectable lazy source list, active indication, per-source Rename/Remove, bounded rename input and named removal confirmation. Use existing Cinema components and TV window-focus pattern.
- [x] Wire callbacks through `MobileLibrary.kt`, `TvLibrary.kt`, `LibraryActivity.kt`; show Playlists control and localized active label. Add English/Arabic resources and typed name error. Keep legacy active refresh/removal controls working.
- [x] Add phone/TV instrumentation that seeds two synthetic sources, switches without network, renames, cancels then confirms targeted removal, recreates and checks selected source. Save synthetic previews after asserted UI state; retain the known compositor/screenshot limitation as needed.

## 4. Delivery

- [x] Extend the force-stop process probe with two-source directory/active-selection assertions; preserve failure propagation and synthetic cleanup.
- [x] Set version 0.7.0/code 7; update saved-library usage, progress, evidence and next-session handoff before the final CI revision.
- [x] Run Java 21 JVM/build/test-APK/lint checks, 13 Python checks, documentation links, actionlint and whitespace checks. Obtain independent review; fix actual findings and rerun relevant checks.
- [ ] Push/open the PR, inspect all five checks and actual latest-head phone/TV reports and process probes, merge bound to the verified SHA, FF-sync main and delete work branches. Preserve owner data during any requested update.
