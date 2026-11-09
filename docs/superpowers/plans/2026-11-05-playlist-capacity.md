# Playlist capacity measurement and performance implementation plan

> **For agentic workers:** Execute with subagent-driven-development, root-owned builds/device operations and scoped independent review. The owner approved100k/200k/300k profiling and fixes; no repeated planning approval is required.

**Goal:** Measure synthetic large-playlist import, memory, refresh, cancellation and cached startup, fix demonstrated bottlenecks and select an evidence-based capacity limit.

**Architecture:** Preserve existing parser/loader and Room contracts first; measure the actual retained-list implementation before changing it. Keep source-scoped stable identities, protected locators, favourites/history and atomic transaction activation. Benchmarks live only in the test APK and use separate disposable fixture databases. Upgrade to streaming import only if bounded batch changes cannot meet measured memory needs.

**Tech stack:** Existing Kotlin/coroutines, Room4 schema, Android instrumentation, synthetic disk fixtures, Python/PowerShell orchestration; no runtime dependencies.

**Spec:** Owner-approved five-step milestone in chat; [data/import architecture](../specs/2026-10-24-iptv-architecture-design.md), [tracker](../../../progress.md).

## Constraints and measurement rules

- Work branch codex/playlist-capacity based on c83b334. Work in place under the existing RAM-conscious workspace ruling; preserve local postmerge plan/tracker, publishing notes and owner backend research. No owner AVD/S25 install/reset or real provider data.
- Superseding6 November host policy: no local AVD launch/recreation and no AEHD/GVM/HAXM. WHPX capability was verified after owner restart, but no fresh AVD is authorized. Keep local builds/JVM/lint; use cloud Ubuntu/KVM for final device suites/probes. No host driver/firewall/reboot changes; reset cause remains unconfirmed. Historical one-AVD benchmark evidence is retained.
- Default100k cap remains until evidence justifies raising it. Experimental store-cap changes must be named in benchmark evidence and never presented as shipped support before delivery.
- Normal byte/line/field/time limits stay active during benchmarks. Record fixture bytes, mixed Arabic/English labels, kinds and artwork presence. Do not allocate whole fixture strings just to feed a stream.
- Record per-stage monotonic elapsed time, sampled used Java heap/native heap and maximum configured Java heap; report PSS separately if measured. Do not call Java heap a whole-process memory measurement. No timing asserts in ordinary CI.
- Production cached startup uses openSession plus paged reads, not legacy restore. Measure first page, category and mixed-language search; repeated refresh must not grow current entry/search rows.
- Distinguish parser/loader cancellation (worker exits/input closes) from VM noncancellable saving. Store cancellation/failed-write rollback must preserve the committed source generation and user state.
- No300k loading-screen claim, new analytics, legal onboarding, billing or unrelated UI work in this milestone.

## Task1: Establish repeatable baseline

**Files:** Create app/src/androidTest/kotlin/com/fireamun/iptv/prototype/CatalogueCapacityProbe.kt; extend ManualFixtureRunner.kt in that folder; create scripts/profile-catalogue.py and docs/development/catalogue-capacity.md when measurement contract is stable.

- [x] Add test-only capacityProbe modes parse/import/refresh/open/clear with explicit size100000/200000/300000. Use the existing optional runner dispatch pattern; ordinary instrumentation remains unchanged.
- [x] Generate synthetic M3U input to disk and parse with ParserLimits(maxEntries=size), retaining all other defaults. Measure baseline parse and100k actual Room save; named fixture DB and dedicated Keystore alias only.
- [x] Preserve raw output and source hash/cap manifest under ignored .tools/capacity/. Confirm successful semantic assertions, process exit, all phases and memory metrics; retain failures honestly.
- [x] Independently review probe validity. No synthetic JUnit XML or claims from a partial process.

## Task2: Fix measured storage/parser bottlenecks

**Files:** Candidate RoomLibraryStore.kt/LibraryDatabase.kt under core/storage; M3uParser.kt only if measured parser CPU warrants it; matching existing JVM/device tests.

- [x] Reproduce observed memory/time/cancellation issue with baseline probe or targeted regression before production edits.
- [x] Implement the smallest correction, initially avoiding simultaneously materialized encrypted rows/batch-list copies. Preserve remapped API output and stable duplicate identity semantics.
- [x] Add meaningful rollback/cancellation/state-preservation regression where behavior changes; run it RED then GREEN. No fixture-only implementation-mirroring tests.
- [x] Re-run identical fixture/build/device stages to compare100k and test200k/300k under explicitly recorded experimental limits. Investigate pressure before raising byte/time budgets.
- [x] Root reviews exact diff/evidence; obtain independent source and benchmark review before any final capacity change.

## Task3: Validate complete flows and deliver

### Measured headroom fix before final validation

Rich300k Xtream with unique artwork passes the bounded legacy save but reaches the full192MiB sampled Java heap. The production ViewModel immediately discards that full remapped return list in favor of openSession. Add an optional lightweight save contract rather than retain this unnecessary production allocation:

- [x] Add LibraryPersistence.saveSession with the exact compatibility default save→openSession() ?: saved.session(). First write a ViewModel fake overriding saveSession whose legacy save fails; run it RED against the old VM.
- [x] Share Room persistence with a retainRemapped switch; preserve legacy save, while saveSession allocates no remapped list or output entry/channel copies and returns actual committed count/generation metadata only after transaction completion. Keep crypto/transaction/cancellation rules unchanged.
- [x] Switch VM commit to saveSession at its existing NonCancellable boundary; preserve directory/progress behavior and explicitly adapt delegating paged test wrappers. Verify fallback/header/refresh/lookup/rollback/legacy contracts.
- [x] Move capacity probe to the production session contract and obtain IDs from bounded pages; label new measurements saveContract=session. Re-run rich300k and original-scanner M3U300k, including actual UI and process-interrupted refresh before selecting the final cap.

**Files:** app instrumentation regression/probe and profile script; capacity guide; progress.md; model cap/resources only if evidence supports a change.

- [x] Verify M3U and Xtream catalogue parsing, initial import, reordered refresh, favourites/movie/episode history, failure/cancel rollback, distinct-process cached reopen, Arabic/Latin search, bounded pages and repeated refresh disk behavior at tested sizes.
- [x] Exercise representative hands-on browsing/search/navigation on one review TV/phone at a time. Physical hardware claims remain pending owner hardware evidence.
- [x] Record chosen cap with measurements/limitations; keep100k if end-to-end results do not justify300k. Expose no unmeasured loading-screen capacity promise.
- [x] Run appropriate Java21 JVM/build/lint and repo docs/tool checks; obtain independent whole-branch review. Final device regressions/probes use cloud CI under the6 November restriction; incomplete local65/91 is not a pass. Preserve owner research/evidence while staging.
- [ ] Push branch/open PR, wait with one background completion watcher, inspect actual latest-head phone/TV XML and all process markers, exact-head merge, FF main, delete branch and verify main CI. Update handoff and provide report/APK as applicable.

## Execution checkpoint — 6 November 2026

Implementation and historical capacity measurements are complete; see the [capacity report](../../development/catalogue-capacity.md). Baseline300k save OOM was reproduced; bounded transactional batches and lightweight session return passed final100k/200k/300k M3U and rich300k Xtream state/refresh/recovery checks. Original production parser retained after the experiment showed no clear speed gain. Independent whole-change and safety reviews passed. Post-handoff local127 JVM tests, lint, app/test APKs and repository checks passed. The final local native suite is incomplete65/91, not passed; remaining full-suite/probe validation and delivery use cloud CI only. The last two checkboxes remain open until complete latest-head cloud evidence, exact-head merge, main verification and branch cleanup. No local AVD launch/recreation or personal-device fixture tests are authorized.
