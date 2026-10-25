# Saved library implementation plan

> **For agentic workers:** Use executing-plans task-by-task in the dedicated owner-workspace branch. Implementation delegation is not required.

**Goal:** Keep the active playlist and protected login across app restart, with refresh and confirmed removal.

**Architecture:** JVM source/persistence contracts feed an Android Room/Keystore implementation. App ViewModel restores cached data without network and commits successful imports before showing them as saved.

**Tech Stack:** Existing Kotlin/Compose/TV/Media3/OkHttp, Room 2.8.5, KSP 2.3.6, standard AES-GCM and Android Keystore.

**Spec:** [Saved-library design](../specs/2026-10-25-saved-library-design.md).

## Constraints

minSdk 28, compile/target 36, Java 17 bytecode/build JDK21. Keep current parser bounds and shared safe UI state. No raw credentials/URLs in logs, error causes, public IDs, saved-instance state or tracked fixtures. Preserve the owner's emulator session; CI device testing only for this slice. No direct main push, destructive migrations or missing-check merge.

## 1. Protected source and persistence boundary

Files: `core/data/.../SourceSpec.kt`, `LibraryPersistence.kt`, source codec tests; Xtream connection/parser identity metadata.

- [x] Write and run failing round-trip, redaction, malformed/oversized source tests using only synthetic details.
- [x] Implement protected binary source encoding, FILE/URL/XTREAM source kinds and persistence snapshot contract.
- [x] Add provider kind/ID metadata to parsed Xtream entries; keep M3U duplicate semantics.
- [x] Run `:core:data:test` and inspect all regressions.

Boundary:
```kotlin
interface LibraryPersistence {
    suspend fun restore(): SavedLibrary?
    suspend fun save(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean): SavedLibrary
    suspend fun clear()
}
```

## 2. Room and protected fields

Files: new `core/storage` Android module, schema/entities/DAO, `RoomLibraryStore.kt`, `AesGcm.kt`, `KeystoreEnvelope.kt`, module/JVM tests and app Android storage fixtures.

- [x] Write failing AES round-trip/AAD/tamper tests and Room reopen/transaction/at-rest fixtures.
- [x] Add pinned dependencies/module and exported version-1 Room schema.
- [x] Implement envelope encryption, bounded staged snapshots and atomic activation with stable private identity matching.
- [x] Verify rollback keeps prior library, key loss never silently resets data, and clear removes source rows/key.
- [x] Build app/test APK and run JVM/lint checks.

## 3. Restore, refresh, remove

Files: app ViewModel/factory/activity, model flags/errors, mobile/TV library controls/resources, app version, persistence-state tests.

- [x] Add failing tests for offline restore, failed save/refresh retention, final-commit cancellation and confirmed-clear state.
- [x] Restore cached roots using injected persistence, preserve connection privately and require new HTTP approval per operation.
- [x] Save successful file/URL/Xtream imports, mark the final commit phase, and refresh the saved root without breaking series/back navigation.
- [x] Add visible saved/age state and phone/TV Refresh/Remove confirmation with focus and Arabic/light coverage.
- [x] Bump prototype to 0.5.0/code 5 and verify original import/playback tests remain valid with isolated stored fixtures.

## 4. Process/device verification and delivery

Files: persistence device flows, test isolation, process probe/runner, CI device script, usage/progress/architecture notes.

- [ ] Exercise cached file/URL/Xtream root and phone/TV removal/restart with synthetic sources; inspect encrypted storage and atomic failure cases.
- [ ] CI process probe: seed, force-stop, verify different PID with real Room/Keystore, clear; preserve every failure exit code.
- [ ] Run appropriate local checks and independent review; fix actionable findings.
- [ ] Update progress with PR #6's actual merge evidence and current persistence artifacts/limits.
- [ ] Commit/push/open one PR; inspect exact-head complete phone/TV XML and process-probe evidence, merge bound to SHA, FF-sync main and remove work branch.
