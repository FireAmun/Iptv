# Library browser implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline in the authorized project branch. Steps use checkbox syntax for tracking.

**Goal:** Ship Settings-only playlist management, six destinations, fast paged cached browsing, search, favourites and Cinema poster layouts without losing the owner's library.

**Architecture:** Add bounded metadata browsing and lazy private-locator resolution to the JVM persistence contract. Room schema 3 implements queries/index/favourites; the app owns PagingSource/Pager and navigation state; device UI renders paged content and existing source forms inside Settings.

**Tech Stack:** Existing Kotlin/Compose/Media3/Room; Paging 3.5.3 and Coil 3.3.0.

**Spec:** [Design and follow-up matrix](../specs/2026-10-27-library-browser-design.md).

## Global constraints

- Preserve `com.fireamun.iptv.prototype`, minSdk 28, compile/target 36, existing keys/ciphertext/IDs and owner My IPTV data.
- No automatic catalogue refresh on startup or navigation; explicit Settings refresh only.
- No private source URLs/credentials in Channel/UI, cache keys, docs, test inputs, logs or telemetry.
- Six destinations in the requested order; no management controls on content pages; no HTTP confirmation.
- One local emulator, no owner instrumentation/reset/uninstall, no host firewall/driver/reboot changes.
- Follow-up features remain explicitly marked in tracker; no fake ratings, artwork or quality choices.

### Task 1: Model, search and data contract

Files: core:model `LibraryModels.kt`, new `BrowseModels.kt`; core:data `LibraryPersistence.kt`, `XtreamJson.kt`, `M3uParser.kt`; new JVM `BrowseModelsTest.kt` and metadata cases in `XtreamLoaderTest.kt`.

Produces `LibraryTab`, `BrowseRequest`, `BrowsePage`, `LibrarySession`, `LibraryArtwork`; Channel gains only safe metadata (year/rating/category key/artwork flag/favourite). PlaylistEntry privately holds artwork MediaAddress. Persistence gains session/page/item/artwork/favourite methods with legacy defaults for fake stores.

- [ ] Write failing normalization/query/privacy/metadata tests. Core example:
```kotlin
assertEquals("اهلا", normalizeSearch("أَهْلًاـ"))
assertEquals("\"bein*\" \"1\"", searchExpression("beIN 1"))
```
- [ ] Run `:core:model:test :core:data:test` and confirm the missing behavior fails.
- [ ] Implement bounded input normalization, safe literal FTS terms and optional provider artwork/year/rating parsing. Preserve original labels and fixed error behavior.
- [ ] Run the same JVM tasks; inspect actual XML.

### Task 2: Room migration and lazy cache

Files: `LibraryDatabase.kt`, `LibraryMigration.kt`, `RoomLibraryStore.kt`, new storage browse operations file if needed; exported schema 3; new app Android storage tests.

Consumes task 1 DTOs. Produces fast source session/header, metadata-only bounded `page`, selected `item`, on-demand `artwork`, categories and persistent `setFavourite` under existing IO/mutex/transaction protection.

- [ ] Add real storage tests for migration, 100k bounded session/page, FTS Arabic/mixed words, type/category filtering, favourite refresh/reopen/removal and address corruption isolated to selection.
- [ ] Demonstrate the old eager/default behavior cannot satisfy lazy corruption/metadata expectations.
- [ ] Implement schema 2-to-3 without changing locator/source encryption contexts; index/backfill metadata only. Add FTS/favourites to atomic save/refresh/remove.
- [ ] Build test APK/lint; inspect schema SQL and test assets. Device test execution is cloud-only.

### Task 3: ViewModel paging, navigation and direct HTTP actions

Files: `LibraryViewModel.kt`, new `LibraryPagingSource.kt`; app Gradle/catalog; source-state JVM tests.

Consumes sessions/pages/resolvers. Produces `catalogue: Flow<PagingData<Channel>>`, destination/category/query/filter state and actions. Full-catalogue lists are not retained for saved roots; episodes remain on-demand. Source generation/query changes create a fresh Pager; cached paging survives player return.

- [ ] Add failing tests for opening cached header without network/full restore, independent destinations/search/favourites, selected-item resolution and direct HTTP import/playback.
- [ ] Implement session startup/switch and direct HTTP actions. Remove the confirmation requirement; preserve validation and transport rules.
- [ ] Keep existing atomic import/cancel/refresh/source-removal tests meaningful; adjust expected consent behavior, not assertions about preservation.
- [ ] Run all JVM tasks; confirm passed cases from reports.

### Task 4: Artwork and device shells

Files: new shared icons/artwork components; mobile/TV shell, browser and settings files; reuse private source dialogs/managers; LibraryActivity wiring; bilingual resources.

- [ ] Add Paging/Coil dependencies with the pinned compatible versions. Implement protected on-demand artwork fetching and opaque cache keys, not URL-bearing UI models.
- [ ] Implement six pinned bottom destinations, content pages with categories/paging, poster grid/details for movies/series, root favourites and global selected-source search.
- [ ] Place source actions/count/time/refresh/name/removal and preferences only in Settings. Empty installs open Settings. Reuse masked-cleared forms and targeted removal confirmation.
- [ ] Preserve scroll/focus across player/tab changes and support Arabic/light mode. Keep only real controls/features visible.
- [ ] Configure Media3 native seek increments to 30 seconds; leave advanced track/PiP/resume items in the explicit follow-up matrix.

### Task 5: Device journeys, performance and integration

Files: app Android journeys and synthetic preview fixtures; developer docs and progress.md; existing CI unchanged unless actual evidence requires a correction.

- [ ] Update old journeys for Settings-only controls, type tabs and absent HTTP modal. Add browsing/search/favourite/paging/lazy-selection journeys on phone and TV.
- [ ] Capture synthetic English-dark/Arabic-light previews for each destination; inspect typography, poster fallback, bottom navigation, settings separation and TV focus.
- [ ] Run Java 21 app/test APK/build/lint/JVM tasks, repository checks and independent code review.
- [ ] Update the owner emulator with install-r, preserve its source, profile cached ready time and verify real Settings/content routes without exposing private labels or forcing a catalogue refresh.
- [ ] Push/open PR, require all five latest-head checks and actual phone/TV XML plus process probes. Merge with exact tested head; confirm identical tree, FF-sync main and delete work branches.
- [ ] Record delivered versus requested follow-up features and actual evidence in progress.md/live PR. Do not equate plans or expected test counts with results.
