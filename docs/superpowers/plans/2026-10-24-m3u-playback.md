# M3U Import and Playback Implementation Plan

> **For agentic workers:** Use executing-plans to implement these tasks in order. Execution evidence lives in progress.md.

**Goal:** Import a user-selected M3U file or URL and play a selected entry on mobile/TV.

**Architecture:** Pure JVM model/data modules own protected addresses, parsing and HTTP loading. The app ViewModel coordinates session-only imports and consent; device UI modules display safe state and host a lifecycle-bound Media3 playback surface.

**Tech Stack:** Existing Kotlin/Compose toolchain; Media3 1.11.1, OkHttp 5.3.2, Coroutines 1.10.2, Lifecycle 2.10.0, JUnit 4.13.2 and MockWebServer 5.3.2.

**Spec:** [M3U/playback design](../specs/2026-10-24-m3u-playback-design.md).

## Global constraints

- Prototype minSdk 28, compile/target 36; temporary identity remains unchanged.
- English/Arabic and touch/D-pad; no raw addresses in UI modules, errors, logs or telemetry.
- Session-only library; no provider persistence, Xtream, EPG, pairing or billing.
- Explicit approval for HTTP; default TLS validation and no cross-protocol redirects.
- Limits: 8 MiB input, 16,384 UTF-16 code units per line, 512-code-unit metadata, 10,000 entries, five-second parser budget, 20-second network call timeout.
- Local manual testing is now authorized. One emulator at a time; no host firewall, driver or reboot changes. Obtain review and latest-head CI before merging.

## Task 1: Protected addresses and bounded parser

**Files:** `core/model/build.gradle.kts`, `core/model/src/main/kotlin/.../LibraryModels.kt`, `core/data/build.gradle.kts`, `core/data/src/main/kotlin/.../M3uParser.kt`, matching `src/test/kotlin` tests; settings/version catalogue.

**Interfaces:** `MediaAddress.parse(String): MediaAddress?`, redacted `toString()`, explicit `reveal()`; `Channel(id: Int, title: String?, group: String?)`; `M3uParser.parse(InputStream, checkCancelled: () -> Unit): List<PlaylistEntry>`; `ImportFailure(code: ImportError)` contains safe codes only.

- [x] Add JVM module configuration and failing tests. For example, parse `#EXTM3U\n#EXTINF:-1 group-title="News, World",Example\nhttps://example.invalid/live\n`; expect the exact title/group and a single entry. Add literal limit and invalid/HLS fixtures.
- [x] Run `./gradlew.bat :core:data:test :core:model:test`; confirm behavioral failures against minimal stubs, then implement bounded streaming reads/metadata parsing and rerun to green.
- [x] Add real loopback HTTP loader tests for success, non-success, redirect policy and input limits; implement `HttpPlaylistLoader.load(MediaAddress)` using bounded parser reads and a cancellable OkHttp call.

## Task 2: Import coordination and device screens

**Files:** `app/.../LibraryViewModel.kt`, `LibraryActivity.kt`, entry activities; `ui/mobile/.../MobileWelcome.kt`, `ui/tv/.../TvWelcome.kt`, localized resources; app unit tests.

**Interfaces:** `LibraryState` exposes safe channels/loading/error/HTTP-prompt/selection state. `importFile`, `importUrl`, `approveHttp`, `dismissHttp`, `selectChannel`, and `closePlayback` coordinate actions; only app/playback receive protected addresses.

- [x] Write tests showing a failed second import preserves the first library, a successful second import replaces it, and HTTP requests cannot load/play before approval. Run the tests against initial behavior and verify the expected failures.
- [x] Implement off-main-thread import, cancellation and safe error handling. Register `OpenDocument`; catch missing picker/read permission failures without logging sensitive exception text.
- [x] Extend both welcome screens with masked URL input, import actions, state feedback and virtualized channel rows. Retain language behavior and restore TV selection/focus on return.

## Task 3: Lifecycle-bound Media3 playback and real app tests

**Files:** `core/playback/build.gradle.kts`, `core/playback/.../PlayerSurface.kt`, UI playback composables/resources; `app/src/androidTest/.../PlaylistFlowTest.kt`, test-only provider/manifest and generated video asset.

- [x] Create a self-generated H.264/AAC test clip under test assets and document its provenance. No media is bundled in production.
- [x] Add instrumented tests for file/URL import, HTTP consent, a playing state, Back/library retention and TV focus. Use MockWebServer and test-only document provider, without production test hooks.
- [x] Implement Media3 HLS/DASH/direct support, safe status/retry, native controls and release outside STARTED. Disable address-bearing Media3 logs and cross-protocol redirects.
- [x] Run build/lint/JVM tests and compile instrumentation locally. Run real app journeys locally with screenshots and D-pad/touch inputs. Latest-head cloud suites remain required by the delivery step below.

## Task 4: Review and delivery

- [x] Update progress, build/run documentation, module responsibilities and provisional limits/known gaps. Record local manual observations and actual test results.
- [x] Run repository checks, local Android checks and independent review; address findings before opening the PR.
- [x] Follow authorized branch/PR/check/merge/delete workflow. Bind merge to the reviewed, tested latest SHA; sync main fast-forward only.

Cloud delivery completed in [PR #3](https://github.com/FireAmun/Iptv/pull/3), merged as `2ba4feeed34abee6821eb0f9c39b1a7aa0e861b6`. [Exact-head CI](https://github.com/FireAmun/Iptv/actions/runs/40141636364) at `5137afd44c018d854cda9baa89462e17061ed486` passed, with 8 tests per cloud phone/TV device and no failures/errors/skips. Local Java 21 checks passed 19 JVM tests and eight instrumentation tests each on API 34 phone and API 36 TV. Main synced fast-forward and work branches removed.

## Self-review

Both approved source paths and local manual testing are included. UI libraries see safe metadata only, parser and network failures use stable codes, and source persistence remains explicitly outside this slice. Numeric limits and test fixtures are concrete. Full catalogue/EPG, commercial rules and physical-device compatibility remain later work.
