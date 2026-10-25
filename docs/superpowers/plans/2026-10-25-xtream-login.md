# Xtream login implementation plan

> **For agentic workers:** Use executing-plans task-by-task. Work in the owner's existing workspace on a dedicated branch.

**Goal:** Offer working server/username/password login alongside file and M3U URL import on phone and TV.

**Architecture:** Protected data-layer connection and cancellable streaming Player API adapter feed the existing ViewModel/library/player. Safe content-kind and parent-navigation metadata extend shared state; UI holds credential text only while its form is open.

**Tech Stack:** Existing Kotlin/Compose/TV/Media3/OkHttp plus Gson streaming JsonReader (no reflective binding).

**Spec:** [Xtream login design](../specs/2026-10-25-xtream-login-design.md).

## Constraints

Preserve Cinema dark/light and Arabic/English, minSdk 28/SDK 36, source privacy and per-operation HTTP consent. Session-only credentials, no backend, no Room/Keystore persistence yet. Use bounded streaming reads and fixed safe errors. One local emulator at a time; do not stop the owner's interactive phone or change firewall/drivers/reboot. Latest-head CI and reviewed PR delivery required.

## Task 1: Data adapter

Files: new `core/data/.../XtreamConnection.kt`, `XtreamLoader.kt`, `XtreamJson.kt`, JVM tests; model/state types and dependency catalogue.

- [x] Write failing tests for protected credentials, encoded endpoints, successful auth/catalogue/episodes and rejected login/redirect/limits.
- [x] Add content kinds and optional series targets while preserving M3U entries.
- [x] Implement protected connection, bounded streaming parser and cancellable same-origin requests.
- [x] Run core JVM tests and inspect failure/privacy behavior.

## Task 2: App import/navigation state

Files: `LibraryViewModel.kt`, JVM tests, `LibraryActivity.kt`.

- [x] Add failing tests for HTTP login consent, safe errors, atomic source replacement, cancellation and series/back state.
- [x] Integrate connection/catalogue loading, private source memory, episode navigation and library Back handling.
- [x] Verify existing file/URL and lifecycle tests still pass.

## Task 3: Three setup routes

Files: mobile/TV source forms, library row/header metadata, bilingual resources, activity callbacks; app version.

- [x] Add named server-login action and four-field forms with masked password, required-value validation and credential clearing.
- [x] Preserve URL/file actions, dark/light, RTL, focus/keyboard/cancel and HTTP consent.
- [x] Show source title/content kind and episode/parent navigation without fictional metadata.
- [x] Compile app/test APK and run lint/JVM checks.

## Task 4: Device verification and delivery

- [x] Add synthetic Player API instrumentation fixtures and mobile/TV login/playback/series/error journeys.
- [x] Inspect cloud phone/TV layouts and device reports; fix observed RTL label/form clipping and short-landscape cancellation visibility. Owner explicitly declined updating their interactive emulator; preserve its session and use cloud screenshots for visual review.
- [x] Update progress/docs, obtain independent review, fix actionable findings and complete local checks.

Delivery gate: [PR #6](https://github.com/FireAmun/Iptv/pull/6) contains the implementation and reviewed corrections. Initial run `40194446164` passed all 14 phone tests and 13/14 TV tests; the existing cancellation visibility failure is recorded in progress. Require all five checks and both complete device suites at the revised exact head before SHA-bound merge, fast-forward sync and branch removal. Use the live PR for final integration/check state rather than treating the first run as passing evidence.
