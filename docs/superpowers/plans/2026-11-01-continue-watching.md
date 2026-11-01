# Continue Watching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Save unfinished movie/episode positions, offer Continue or Start over, and show a source-scoped Continue Watching row on phone and TV.

**Architecture:** Room schema 4 stores history independently of replaceable catalogues. An application-owned serial recorder orders checkpoints/reset/flush; a per-ViewModel coordinator resolves selections and exposes safe UI state. Media3 reports positions without owning storage.

**Tech Stack:** Existing Kotlin, Coroutines/Flow, Room/AES-GCM/Keystore, Media3 and Compose/Cinema. No new runtime dependency.

**Spec:** [Continue Watching design](../specs/2026-11-01-continue-watching-design.md).

## Global Constraints

- Keep the current 100,000-entry cap. No cloud sync, backend, monitoring events, purchases, automatic provider refresh or automatic playback on launch.
- Preserve Arabic/English, dark/light, existing touch/TV controls and Cinema styling.
- Bookmark only classified MOVIE/EPISODE on prepared, seekable, non-dynamic, non-live timelines. UNKNOWN M3U and live entries keep existing behavior.
- Checkpoint every 5 seconds while playing and on pause, seek, ended, stop and disposal. Persist unfinished positions from 1,000 ms; actual ended state alone marks completion.
- Show latest 20 matching unfinished items; join current root identities before LIMIT. Keep other positions for ordinary selection.
- Identity is source UUID + stable root ID, or stable parent-series ID + actual provider episode key. Never use transient episode indices, titles, URLs or generation.
- Episode snapshots are validated, bounded to 16 KiB and encrypted with the existing source key and generation-independent AAD. Protected holders redact toString.
- Refresh/rename preserve history. Targeted removal and clear remove corresponding history; delayed writes cannot recreate deleted sources. Preserve migration chain 1 -> 2 -> 3 -> 4 and old ciphertext/IDs.
- One disposable emulator at a time; no owner AVD/phone instrumentation/reset. No host firewall, drivers or reboot. Earlier unexpected host shutdown remains unresolved.
- Sol workers implement one task at a time; root coordinates builds/emulators/reviews/commits. No parallel Gradle work or worker Git mutations without explicit root delegation.
- Current feature branch is `feat/continue-watching` in the existing checkout, avoiding additional editor-imported worktrees/RAM. Preserve owner research and its exact local tracker section unstaged.

## Task 1: Stable identity and protected history storage

**Files:** Create `core/data/src/main/kotlin/com/fireamun/iptv/core/data/PlaybackProgressPersistence.kt`, `EpisodeSnapshotCodec.kt`; create `core/storage/src/main/kotlin/com/fireamun/iptv/core/storage/WatchProgressStore.kt`; modify `LibraryDatabase.kt`, `RoomLibraryStore.kt`, `XtreamJson.kt`; export `core/storage/schemas/com.fireamun.iptv.core.storage.LibraryDatabase/4.json`; add parser/codec JVM tests and `app/src/androidTest/kotlin/com/fireamun/iptv/prototype/WatchProgressStorageTest.kt`.

**Interfaces produced** in core:data (constructors validate fields; protected types redact formatting):

```kotlin
data class ProgressTarget(val sourceId: String, val rootItemId: Int,
    val kind: ContentKind, val episodeProviderKey: String? = null) {
    override fun toString() = "[protected progress target]"
}
class EpisodeSnapshot(val providerKey: String, val title: String?,
    val season: Int?, val episode: Int?, val address: MediaAddress)
data class WatchProgress(val positionMs: Long, val durationMs: Long?,
    val completed: Boolean, val watchedAtMs: Long)
class ProgressRecord(val target: ProgressTarget, val progress: WatchProgress,
    val snapshot: EpisodeSnapshot?)
data class ProgressReference(val sourceId: String, val refDigest: String)
data class RecentWatch(val reference: ProgressReference, val kind: ContentKind,
    val title: String?, val parentTitle: String?, val rootItemId: Int,
    val season: Int?, val episode: Int?, val progress: WatchProgress,
    val artwork: LibraryArtwork?)
interface PlaybackProgressPersistence {
    suspend fun read(target: ProgressTarget): ProgressRecord?
    suspend fun read(reference: ProgressReference): ProgressRecord?
    suspend fun save(target: ProgressTarget, progress: WatchProgress,
        snapshot: EpisodeSnapshot? = null)
    suspend fun reset(target: ProgressTarget)
    suspend fun recent(sourceId: String, kind: ContentKind, limit: Int = 20): List<RecentWatch>
}
```

- [ ] Add failing parser/codec tests: reordered episode IDs retain `EPISODE:<positive ID>`; snapshot roundtrip, address/key/numeric validation, 16 KiB bound, redaction. Root EPISODE without key is valid; keyed API EPISODE requires SERIES parent.
- [ ] Add actual Room reopen tests using existing unique database/Keystore fixtures. Initial movie assertion:

```kotlin
val saved = store.save(SourceSpec.file(), listOf(PlaylistEntry(
    Channel(0, "Movie fixture", null, ContentKind.MOVIE),
    MediaAddress.parse("https://example.invalid/movie"))))
val target = ProgressTarget(saved.sourceId!!, saved.entries.single().channel.id, ContentKind.MOVIE)
store.save(target, WatchProgress(12_000, 30_000, false, 100))
assertEquals(12_000L, store.read(target)!!.progress.positionMs)
assertEquals(target.rootItemId, store.recent(saved.sourceId!!, ContentKind.MOVIE).single().rootItemId)
```

- [ ] Observe new-contract RED. Implement `watch_progress` composite PK `(sourceId,refDigest)`, rootItemId, kind, positionMs, nullable durationMs, completed, watchedAtMs, nullable encrypted snapshot; index `(sourceId,kind,completed,watchedAtMs)`. HMAC source key over unambiguous canonical reference; AAD `watch-progress:<sourceId>:<refDigest>`.
- [ ] Reuse envelope unlocking, guarded IO and same store mutex/transaction; zero temporary keys/plaintext. Validate current source/root kind before save. Join current catalogue before LIMIT, decrypt at most 20 snapshots. MOVIE joins MOVIE root; keyed EPISODE joins SERIES parent; unkeyed EPISODE joins EPISODE root. No FK to replaceable entries. Keep progress implementation separate from importer.
- [ ] Add migration 3 -> 4 table/index only, register/export schema. Add deletion to existing remove/clear transactions; leave refresh/rename intact.
- [ ] Device coverage: movie/episode reopen; encrypted fixture values absent from DB files; source separation/rename; reordered refresh; missing root hidden/history retained; bounded recent; targeted remove/clear; delayed write rejection; failed-write rollback/corrupt snapshot; 1/2/3 -> 4 preserving IDs/ciphertext. Reuse existing migration fixtures.
- [ ] Verify Java 21 `:core:data:test :core:model:test :app:assembleDebugAndroidTest :core:storage:lintDebug`, then storage class on disposable TV. Root reviews report/diff and commits task files only.

## Task 2: Media3 checkpoints and resume protocol

**Files:** Create `core/model/src/main/kotlin/com/fireamun/iptv/core/model/PlaybackProgress.kt` and policy JVM tests; modify `core/playback/.../LifecyclePlayerSession.kt`, `PlayerSurface.kt`; add `app/src/androidTest/.../PlaybackCheckpointTest.kt`. Keep a temporary adapter for old Surface call until Task 3 updates Activity.

**Interfaces produced:**

```kotlin
class PlaybackRequest(val sessionId: String, val address: MediaAddress,
    val kind: ContentKind, val initialOffsetMs: Long) {
    override fun toString() = "[protected playback request]"
}
data class PlaybackStart(val instanceId: String, val positionMs: Long)
data class PlaybackCheckpoint(val positionMs: Long, val durationMs: Long?,
    val ended: Boolean, val eligible: Boolean)
```

New Surface parameters: `request:PlaybackRequest`, `startInstance:()->PlaybackStart?`, `onCheckpoint:(String,PlaybackCheckpoint)->Unit`, `onStatus:(PlaybackStatus)->Unit`, `onDisposed:()->Unit`, `modifier:Modifier=Modifier`. Null start means retired request: create no player. Callback String is instance ID; Surface disposal calls onDisposed once after synchronously reporting final checkpoint.

- [ ] Write failing model tests and implement `isBookmarkEligible(kind,prepared,seekable,dynamic,live)`, `normalizedCheckpoint(value)`, `resumeOffset(positionMs,durationMs)`. Invalid/ineligible/negative checkpoints return null; nonpositive/unknown duration normalizes null; known duration bounds positions and resume clamps to duration-1. Eligible sub-second positions may update RAM but are not persisted unfinished.
- [ ] On each lifecycle start obtain instance and latest offset; call Media3 `setMediaItem(item,positionMs)`. Never max(initial,latest): backward seeks must survive restart. Surface keys by sessionId, never changing position.
- [ ] Poll every 5 seconds while playing; report pause, seek discontinuity, ended, stop/disposal. Use content position/duration and prepared + seekable + !dynamic + !live eligibility. Failed preparation never overwrites old progress with eligible zero.
- [ ] Snapshot before detach/release; retire old callbacks, polling and listener; close idempotently. Keep native 30-second seek controls, transport policy, safe errors and disabled provider logs. Updated callbacks must not rebuild player.
- [ ] Actual Media3 loopback MP4 tests: observed initial seek near 12s, backward seek/pause, background resumes lower offset, periodic checkpoint, ended, failed prepare retains offset, retired callback rejection, LIVE/UNKNOWN ineligible. Use existing synthetic 30-second clip and actual player/checkpoint observations.
- [ ] Verify model JVM tests, app/test build, playback lint and actual checkpoint device cases. Root reviews and commits task files.

## Task 3: Serialized recorder, coordinator and application wiring

**Files:** Create `app/.../PlaybackProgressRecorder.kt`, `PlaybackProgressCoordinator.kt` and JVM tests; modify `IptvApplication.kt`, `LibraryViewModel.kt`, `LibraryActivity.kt`; remove old Surface adapter after its sole caller is updated.

**Consumes:** Task 1 storage contracts and Task 2 request/start/checkpoint helpers.

**Produces:** `ContinueWatchingState(sourceId:String?,kind:ContentKind?,items:List<RecentWatch>,prompt:ResumePrompt?,loading:Boolean,error:ProgressProblem?)`, `ResumePrompt(title:String?,positionMs:Long,durationMs:Long?)`, `ProgressProblem { READ, SAVE, RESET }`.

Coordinator exposes `state:StateFlow<ContinueWatchingState>`, `context(sourceId:String?,generation:String?,tab:LibraryTab,nested:Boolean,query:String,category:String?)`, `play(entry:PlaylistEntry,sourceId:String?,parentItemId:Int?)`, `playRecent(reference:ProgressReference)`, `continuePlayback()`, `startOver()`, `cancelChoice()`, `dismissError()`, `startInstance(sessionId:String):PlaybackStart?`, `checkpoint(sessionId:String,instanceId:String,value:PlaybackCheckpoint)`, `retry(request:PlaybackRequest)`, `closing(sessionId:String)`, `disposed(sessionId:String)`. Inject `onReady:(PlaybackRequest,Channel)->Unit`, scope/clock/store/recorder for deterministic tests. UI state contains no provider key or locator.

- [ ] Failing recorder tests use fake persistence/test dispatcher and assert save/reset/flush FIFO, final post-close report accepted, old session/instance report rejected, reset failure prevents zero playback, and failed writes preserve prior state. Hold operations with deferred gates to prove ordering.
- [ ] Application-owned SupervisorJob/IO actor holds no Activity/View/Player. Generate unique session/instance IDs. Synchronize registry + queue submission: new session/reset invalidates old reports immediately; already accepted commands finish in FIFO before later reset. Do not discard acknowledged earlier saves just because a newer session registers.
- [ ] Update latest eligible RAM position synchronously before queueing writes. Retry issues new session using latest offset, including backwards seeks; live/default behavior stays unchanged. Persist only eligible >=1s or ended. Errors expose fixed enums and never stop playback/log inputs.
- [ ] Flush accepted writes before reading positions/history. Root history resolves current catalogue item; API episodes use stable parent ID/key and encrypted cached snapshot. Playing fresh matching API episode refreshes its snapshot on save while retaining position. History must not call provider or materialize full catalogue.
- [ ] Context/selection tokens guard every suspension. Source/tab/query/category/cancel changes cannot later open stale media. Query history only root unfiltered Movies/Series and after playback; bounded 20. Movies requests `recent(sourceId, MOVIE, 20)`; Series requests `recent(sourceId, EPISODE, 20)`, never SERIES.
- [ ] Continue uses saved offset; Start over awaits reset then starts zero; Cancel leaves state untouched. Completed records start zero/no row. Failed reads permit reselect retry; failed reset keeps prompt/action available; save error is dismissible and prior state survives.
- [ ] Wire application recorder and per-VM coordinator through existing factory, no new DI framework. Capture stable parent root before transient episode load. Playback flow becomes PlaybackRequest. Keep import/paging behavior/tests and existing event allowlist; emit playback-start only when an actual request starts.
- [ ] Activity keys by sessionId, delegates retry to VM, supplies start/checkpoint/disposed callbacks. closePlayback clears visible player but keeps final report accepted. Disposal flushes then refreshes row/retire closing binding. Disable stale row activation while history reloads; next task handles completed-row focus fallback.
- [ ] JVM tests: movie/episode choices; completed/none; close-final; backward seek/retry/instance supersession; reset/late callback; failed read/write/reset; source/tab/cancel races; episode reorder; zero startup provider requests. Run all JVM tests and app/test build. Root reviews and commits task files.

## Task 4: Cinema row, resume choices and complete evidence

**Files:** Create `app/.../ContinueWatching.kt`; modify `LibraryBrowser.kt`, `LibraryActivity.kt`, app `values/browse.xml`/`values-ar/browse.xml`; add `ContinueWatchingFlowTest.kt`; extend `CategoryFocusTest.kt`, `PersistenceProcessProbe.kt`, `scripts/persistence-process-test.sh`; add `docs/development/continue-watching.md`; root updates architecture/tracker facts.

**Consumes:** Coordinator safe state/callbacks. UI passes opaque ProgressReference only; never render or log raw provider keys/locators.

- [ ] Full-span grid header before posters, only root unfiltered Movies/Series, matching nonempty history. Permit header while root Paging temporarily empty. Correct all catalogue grid offsets by exactly one when header exists. Keep existing catalogue-count semantics. Render at most 20 with horizontal LazyRow and existing Cinema targets/artwork loader.
- [ ] Positive known duration gives progress bar; otherwise saved timestamp. Media times stay LTR in Arabic. Use 48dp targets, supplied labels and parent-series artwork; opaque digest UI keys.
- [ ] Cinema Dialog/Surface with Continue at timestamp, Start over, Cancel; palette, safe areas, bounded scroll, large text, once-only initial TV focus. Fixed localized read/save/reset errors; reselect/read retry, retained reset prompt and dismissible save error.
- [ ] Save row digest + parent root ID or catalogue ID as a safe focus anchor across playback. Restore once per return/context and never reclaim from nav/top/category/user focus after late Paging. Scroll grid to header index 0 and nested LazyRow to the digest's current index before requesting focus; a removed completed item falls back to next row item or parent catalogue card. Extend existing CategoryFocus regression with history header and late Paging.
- [ ] Real classified fixture journeys on phone and TV: seek/pause/Back, row/time, Activity recreation/cold reopen, Continue observed near saved offset, Start over near zero, Cancel unchanged, ended removes row, reordered episode identity, background/retry and live unchanged. Use existing 30s clip and acknowledged checkpoint before persistence assertions. Inspect English/dark, Arabic/light and compact TV large-text prompts/focus/screenshots.
- [ ] Extend separate-process seed/verify/clear probe for movie+episode positions/row, without weakening existing source/favourite/PID/marker gates. New PID required; no restore provider request. Update shell gate unit tests if marker contract changes.
- [ ] Usage guide explains local scope, UNKNOWN M3U restriction and manual refresh/cached locator behavior. No 300k advertising or physical-hardware claims.
- [ ] Full Java 21 JVM/build/lint/test APK checks with no-daemon/max-workers=2, actual XML; one disposable device at a time, normal/compact journeys and screenshots. Whole-branch independent review before push.
- [ ] Root performs normal PR/full-CI/exact-head merge/main-sync/delete flow. Require actual latest-head phone/TV XML count from compiled test set, no skipped cases and all process probes; verify post-merge main. Preserve owner research/data/environment notes and update handoff.
