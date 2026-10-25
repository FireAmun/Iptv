# IPTV development progress

Last updated: 25 October 2026 (Asia/Riyadh)  
Overall status: M3U/playback and Cinema A dark/light merged in [PR #3](https://github.com/FireAmun/Iptv/pull/3) and [PR #4](https://github.com/FireAmun/Iptv/pull/4). Standalone phone launching is merged in [PR #5](https://github.com/FireAmun/Iptv/pull/5). Xtream-compatible server login is the current feature task.  
Current phase: P3 Xtream source slice in progress; P4 appearance and P2 playable prototype integrated. Broader P3/P4 capabilities remain open.  
Next task: Finish and verify three setup routes (server login, M3U URL, file), real Player API catalogue/series navigation, and exact-head PR delivery. Then implement encrypted source persistence and Room storage so the library survives app restart.

## 1. Start here in a new chat

1. Read this tracker, then the linked product brief and architecture sections relevant to the task.
2. Inspect the actual workspace and current user instructions before relying on a recorded status. Later user decisions supersede earlier recommendations.
3. Continue from the next task rather than restarting product research or rewriting completed documents.
4. Keep this file current after meaningful work: deliverables, evidence, open items, blockers, and the next concrete task.
5. Never store provider credentials, private playlist/stream URLs, purchase tokens, signing keys, or identity documents here.

This tracker records project state; it is not a substitute for source code, test results, the detailed specification, or the user's latest authorization. The user authorized starting, directory setup, downloading missing tools, and GitHub delivery. For ordinary changes the standing instruction is branch -> push -> PR -> inspect latest-head CI -> merge after success -> delete branch. See [the delivery workflow](docs/development/github-workflow.md). The prototype Gradle app now exists; there is no hosted application backend, purchase integration, or Play publication. Do not request authorization again for work already authorized.

## 2. Project context and decisions to preserve

| Topic | Current direction | Decision status |
| --- | --- | --- |
| Product | IPTV player for the user's own compatible sources; no supplied channels/content subscriptions. | Confirmed |
| Initial devices | Android phones, tablets, Android TV/Google TV. Other TV operating systems and Apple platforms are outside initial scope. | Confirmed |
| Languages/markets | Arabic and English with proper RTL; Saudi Arabia and Egypt initially. | Confirmed direction |
| Content | Live TV, movies, and series together where the source supplies them. | Confirmed |
| Phone companion | Assisted TV setup and remote control over the same local network. | Confirmed |
| External idea validation | Proceed with the information available. Interviews, surveys, or discussing the idea with others are not prerequisites. | Explicit user preference |
| App stack | Kotlin, Compose/Material 3, Compose for TV, Media3; shared core and separate device UI. | Working recommendation |
| Data/network tools | Room/Paging 3, DataStore, Coroutines/Flow, Hilt, OkHttp/Retrofit, kotlinx.serialization, Coil, WorkManager. | Architecture baseline |
| App packaging | Prefer one application ID/Play listing across mobile and TV; separate UI modules. | Architecture recommendation |
| Hosted services | Managed owner monitoring; no custom hosted application backend or mandatory app account for the baseline. | Working launch direction |
| Monetization | One-time purchase or yearly renewal under consideration. Annual auto-renewal and manual prepaid renewal differ. | Open; do not silently choose one |
| Purchase verification | Separate from monitoring. Client-only tradeoffs versus managed secure verification must be decided before billing work. | Open |
| Advanced scope | Cloud sync, recording/downloads, multiview, casting, extra playback engines, and web dashboard deferred. | Proposed first-release boundary |
| GitHub delivery | `FireAmun/Iptv`, base `main`; PR/check/merge/delete cycle authorized for normal changes. | Confirmed user instruction |
| Visual design | Cinema A approved with matching light mode; shared layout and saved explicit appearance. [Editable concepts](https://www.figma.com/design/2Hg2v6XoCHTlmyDTZSI9te), [implemented previews](docs/design/app/README.md). Broadcast's denser guide layout follows when guide data exists. | Approved 25 October; prototype implemented |

The architecture is a delivered draft, not evidence of implemented or tested functionality. Branding, the complete free/paid offer, detailed UI design, and release compatibility remain unsettled.

## 3. Documents and delivered artifacts

| ID | Deliverable | Location | Status / evidence |
| --- | --- | --- | --- |
| A01 | Product brief, decisions, feature inventory, research, and launch proposal | [iptv-product-brief.md](codex/docs/iptv-product-brief.md) | Delivered; updated through 24 October 2026. |
| A02 | Architecture: modules, screens, data/imports, playback, pairing, purchases, monitoring, hardware tests | [Architecture specification](docs/superpowers/specs/2026-10-24-iptv-architecture-design.md) | Draft delivered and self-reviewed. Local document links and UTF-8 readback checked. |
| A03 | Cross-chat progress tracker | [progress.md](progress.md) | Delivered; required sections, P0–P9, UTF-8 readback, and local links checked. |
| A04 | Automatic workspace handoff instructions | [AGENTS.md](AGENTS.md) | Delivered; directs future project agents to read/update this tracker; linked tracker exists. |
| A05 | First Android build implementation plan | [First-build plan](docs/superpowers/plans/2026-10-24-first-android-build.md) | Delivered for the app-shell slice; M3U/playback follows separately. |
| A06 | Android app shell and debug APK | [Build guide](docs/development/first-build.md), `:app`, `:ui:mobile`, `:ui:tv`, [PR #2](https://github.com/FireAmun/Iptv/pull/2) | Implemented: phone/TV launch, persistent English/Arabic selection, RTL and remote focus. Debug app/test APKs and lint passed locally on Java 21. Original shell runtime results are in PR #2; the playback slice follows in A13. |
| A07 | Project directory skeleton and structure guide | [README.md](README.md), `app/`, `core/`, `ui/`, `gradle/`, `scripts/`, `docs/` | Delivered; module responsibility READMEs and source/resource/test placeholders. App/mobile/TV plus core model/data/playback are configured; other core areas remain reserved. |
| A08 | Local toolchain inventory | [environment.md](docs/development/environment.md) | Updated with Java 21 build evidence and local SDK/device-test restrictions. |
| A09 | Tool downloads and environment activation | [android-env.ps1](scripts/android-env.ps1), [environment inventory](docs/development/environment.md), ignored local binaries | Downloads verified. Project TV AVD recreated and ADB online observed, then stopped. Local testing was suspended during the shell milestone and later re-authorized; SDK Manager copy integrity still needs repair. |
| A10 | GitHub CI and standing PR workflow | [ci.yml](.github/workflows/ci.yml), [delivery guide](docs/development/github-workflow.md), [bootstrap PR #1](https://github.com/FireAmun/Iptv/pull/1) | Verified: seven local tests, repository/actionlint/PowerShell checks passed; GitHub push and PR CI passed at `e80af5a`. [PR run](https://github.com/FireAmun/Iptv/actions/runs/40038721667). PR link is the live source of delivery/merge state. Automatic remote branch deletion enabled. |
| A11 | Android CI, device tests, and APK delivery | [PR #2 checks](https://github.com/FireAmun/Iptv/pull/2/checks), [workflow](.github/workflows/ci.yml) | Build/lint/test-APK job, phone API 28 and TV API 36 smoke jobs, and required aggregate gate. APK/report artifacts retained seven days. Each device runs both activity tests covering language round trip, RTL/LTR, recreation, TV focus and D-pad activation. Require actual latest-head green results before merging; the earlier local suspension was superseded by D07. |
| A12 | M3U/playback slice design and implementation plan | [Design](docs/superpowers/specs/2026-10-24-m3u-playback-design.md), [plan](docs/superpowers/plans/2026-10-24-m3u-playback.md) | Delivered and implemented. Owner selected file plus URL import and explicitly re-authorized local hands-on testing alongside CI. |
| A13 | Session M3U library and Media3 player | [Playback guide](docs/development/m3u-playback.md), `core/model`, `core/data`, `core/playback`, app/mobile/TV | Implemented: bounded streaming parser, cancellable imports, HTTP consent, safe labels/errors, virtualized lists, lifecycle release, native playback controls, Arabic/English. Local 19 JVM tests, 8 API 34 phone tests and 8 API 36 TV tests passed locally; independent review passed. [PR #3](https://github.com/FireAmun/Iptv/pull/3) records cloud verification and delivery state. Session-only library, no database/source persistence. |
| A14 | Cinema appearance, version 0.3.0/code 3 | [PR #4](https://github.com/FireAmun/Iptv/pull/4), [concepts/palette](docs/design/README.md), [actual app previews](docs/design/app/README.md), [design](docs/superpowers/specs/2026-10-25-cinema-design.md), [plan](docs/superpowers/plans/2026-10-25-cinema.md) | Implemented shared palette, bundled Manrope/Cairo with notices, saved dark/light appearance and launch/system-bar contrast, redesigned setup/library/player, gold TV focus and rounded source dialogs. Build/lint, 20 JVM tests, ten local tests per phone/TV emulator and independent review passed. Hands-on cold-start appearance, real file picker, video/pause/seek/error/Back verified. Live PR records cloud checks and integration. Figma light export QA was limited by Starter cap; actual app light mode was visually checked. |
| A15 | Phone emulator without Android Studio | [PR #5](https://github.com/FireAmun/Iptv/pull/5), [start-phone.ps1](scripts/start-phone.ps1), [usage](docs/development/first-build.md) | Standalone window uses the installed SDK/AVD and declines another emulator while any QEMU emulator runs. Machine-local **IPTV Phone** desktop shortcut and ignored VS Code **IPTV: Start phone emulator** task invoke the shared script. Actual visible window, completed boot, installed app launch and one-emulator guard verified. No SDK download, firewall, driver or reboot change. Live PR records cloud checks/integration. |
| A16 | Xtream-compatible server login, version 0.4.0/code 4 | [Usage/boundaries](docs/development/xtream-login.md), [design](docs/superpowers/specs/2026-10-25-xtream-login-design.md), [plan](docs/superpowers/plans/2026-10-25-xtream-login.md) | Implemented three source choices, masked login forms, real Player API auth/live/movie/series catalogue, episodes/parent Back, fixed safe errors and session-only private connection. Local build/lint, 31 JVM and seven repository tests passed. Cloud device/visual checks and delivery remain pending; owner declined disturbing their current emulator session. |

## 4. Status vocabulary

- **Not started:** no deliverable exists for the work item.
- **In progress:** work is underway; record what remains.
- **Delivered:** the named artifact exists, but any required verification or acceptance must be stated separately.
- **Verified:** relevant checks passed; record the command/report, date, and environment/device.
- **Blocked:** a specific dependency prevents the next task; name it and the action needed to unblock it.
- **Deferred:** intentionally outside the active scope.

Do not equate a document, mockup, compile, emulator run, physical-device test, and production release. Each proves something different. Mark a development phase complete only when its exit checks are met.

## 5. Development phases

This sequence is the working roadmap, not a promise that every proposed feature or package has been approved. Break each phase into reviewable implementation tasks when it becomes active.

| Phase | Status | Main deliverables | Exit checks |
| --- | --- | --- | --- |
| P0 — Product and architecture | Delivered — draft baseline | Product brief, architecture specification, progress tracker. | Documents exist, link correctly, separate confirmed/open choices, and identify implementation boundaries. |
| P1 — Plan and development readiness | Delivered for the app-shell slice | First-build plan, toolchain inventory, compatible dependency/minSdk baseline, Git/CI, environment limitations. | App-shell plan/checks exist; physical hardware and authorized playback media remain open for the next slice. |
| P2 — Foundation and playable proof | Delivered prototype — PR #3 merged; local and cloud phone/TV verified | Android project, one app identity, minimal mobile/TV entry points, safe source entry, initial M3U import, Media3 playback, basic error states. | Synthetic playback and D-pad checks passed; physical/provider compatibility remains a later requirement. |
| P3 — Sources, catalogue, search, and EPG | In progress: session Xtream source slice | Xtream-compatible adapter, all agreed content kinds, multiple sources, Room/Paging, atomic generation refresh, Arabic-aware search, XMLTV/guide mapping, favourites/progress storage. | Large-fixture imports, cancellation/process interruption, identity preservation, search, and guide time-zone checks pass. Missing metadata never becomes invented hierarchy. |
| P4 — Full device experience and playback | In progress for Cinema appearance; remaining capabilities not started | Approved dark/light source/library/player UI, then full phone/tablet/TV flows, stable focus restoration, series/episodes, resume, audio/subtitles, parental PIN/restrictions, settings. | Core journeys work with touch and D-pad in Arabic/English; restrictions apply through shared rules; representative device checks recorded. |
| P5 — Phone-to-TV setup and remote | Not started | Discovery, QR/manual fallback, protected transport, TV approval, revocable phone trust, remote catalogue browsing, approved source transfer. | Unpaired/revoked phones denied; expiry/reconnect/network changes tested; credentials stay protected; remote browsing opens no additional stream. |
| P6 — Commercial access and billing | Not started | Selected offer, entitlement component, Play purchase/acknowledgement, chosen verification, restoration, expiry/refund/offline behavior. | D01–D05 resolved; Play licence tests cover selected products and lifecycle paths; no debug unlock in release builds. |
| P7 — Managed monitoring and operations | Not started | Play Console reporting setup, redacted Crashlytics/Analytics events, selected feature flags, safe defaults, privacy/data inventory. | Telemetry inspected for secrets; startup/playback does not depend on Remote Config; disclosures match SDK behavior. Add a minimal safe diagnostic baseline earlier where necessary. |
| P8 — Compatibility and release hardening | Not started | Physical-device matrix results, playback endurance, large-catalogue profiling, import limits/performance budgets, regression fixes, native-library checks if applicable. | Named-device reports and required checks pass; unsupported cases documented; release build/install and secret scans complete. |
| P9 — Google Play launch | Not started | Developer/account readiness, applicable test track, signing/versioning, store assets, authorized demos, support/privacy pages, required declarations. | Current official requirements checked; applicable testing/access approvals complete; release candidate reviewed; publication explicitly authorized. |

P1 must include TV feasibility in the earliest proof. Do not build an extensive phone-only app before investigating TV playback and focus. Unresolved pricing should not block unrelated playback/data work. Account and tester preparation can run alongside development when authorized; user research interviews remain excluded.

## 6. Open decisions and dependencies

| ID | Item | Current position / action needed | Blocks |
| --- | --- | --- | --- |
| D01 | Payment model | Owner chooses one-time, annual auto-renewal, or manually renewed annual access; no price selected. | P6 |
| D02 | Free access / trial / Pro boundaries | Define what users can test before buying and whether phone remote/setup is free. | P6 and final purchase UX |
| D03 | Purchase ownership/device policy | Baseline restores using purchasing Google account; cross-account households and a custom device cap are not designed. | P6 |
| D04 | Purchase verification | Choose client-only limitations or a managed verifier without a custom hosted backend. | P6 |
| D05 | Offline access, refunds, annual expiry | Set last-known-access behavior and any expiry/grace rules; distinguish service errors from confirmed revocation. | P6 |
| D06 | Product name, identity, visual direction | No final name/logo chosen. Prototype ID is `com.fireamun.iptv.prototype`; select permanent identity before Play registration. | Branding and permanent Play setup; not local prototype research |
| D07 | Hardware and authorized source inventory | No physical devices/private provider verified. Owner explicitly re-authorized local hands-on app testing on 24 October 2026: one emulator at a time, no host firewall/driver/reboot changes. Earlier unexpected shutdown remains unresolved. Use a self-generated video and synthetic playlists; never put credentials here. | Physical-device/provider compatibility claims |
| D08 | Toolchain and version control | Updated tools installed and executable checks passed; Git initialized with specified remote and a work branch. Wrapper and app-shell pins now exist; local Java 21 build/lint passed. See A08/A09. | First Android build configuration |
| D09 | minSdk / compatible dependency versions | Prototype minSdk 28, compile/target 36; Gradle 9.6.0 / AGP 9.4.0 with fixed Compose/TV dependencies. Android 9 remains a test baseline, not a release support promise. | Dependency pinning and release support claims |
| D10 | Remote transport implementation | Ktor CIO is a candidate; prove Android TLS, lifecycle, and reconnect compatibility. QR-first pairing; code-only pairing is not finalized. | P5 transport choice |
| D11 | Import resource/performance limits | Prototype limits implemented: 8 MiB, 16,384 code units/line, 512/label, 10,000 entries, five-second parse budget. Real-device/performance measurements remain outstanding. | Import hardening / P8 |
| D12 | Credential persistence integration | Prove Keystore-backed encryption, optional Tink integration, and backup exclusions; DataStore does not encrypt by itself. | Source storage and P5 |
| D13 | Codec gaps | No device codec gaps demonstrated; FFmpeg audio or libVLC is not included by default. | Only any demonstrated unsupported codec requirement |
| D14 | Google Play readiness | Account type/date, verification state, store projects, test resources, and signing setup not inspected. Recheck current requirements when relevant. | P9; prepare independently when authorized |
| D15 | Final scope and design review | Cinema A with light mode approved; see A14. Implement existing journeys first. Future catalogue/guide screens require their data capabilities. | Production visual refresh; no further design-choice question |
| D16 | Server-enforced branch protection | GitHub rejected protection with HTTP 403: this private repository needs Pro. Auto-merge remains unavailable; remote merged-branch deletion is enabled. Agents still enforce the authorized PR/CI/merge flow. | Server-side enforcement only; no plan or visibility changes authorized/performed |

## 7. Current blockers and risks

M3U/playback PR #3 is merged after the test-cleanup fix and complete cloud phone/TV results. [Investigation](docs/development/tv-test-cleanup.md) preserves the captured failure and bounded harness changes; no production/GPU root cause is claimed. Cinema's local checks and hands-on verification pass. [PR #4](https://github.com/FireAmun/Iptv/pull/4) records its cloud checks and integration; require complete latest-head phone/TV results before merging. No failing or incomplete result is eligible for merge.

- Physical hardware/source availability remains unknown. Local manual testing is re-authorized with one emulator at a time; no host firewall/driver/reboot changes. Preserve the earlier incident and SDK tool-copy limitation in environment notes.
- The app dependency combination builds on installed JDK 21. CI also uses Java 21; local activation still defaults to bundled JBR 25, so follow the build guide's explicit Java selection.
- Server branch protection is unavailable on the current private-repository plan; do not mistake policy instructions for GitHub-enforced restrictions.
- Billing decisions are open; keep them out of the core playback path and do not let them stall local source/playback planning.
- Large catalogues, weak TV boxes, RTL focus, authenticated pairing, and provider connection allowances are identified technical risks, not observed bugs.
- Managed monitoring is the preferred launch approach. A custom owner dashboard/backend is not an outstanding build requirement.

## 8. Verification record

| Date | Scope | Result / evidence |
| --- | --- | --- |
| 24 October 2026 | Architecture and product documents | PowerShell readback and local Markdown link checks passed; architecture required-section/placeholder scans passed; no UTF-8 replacement characters found. Documentation checks only. |
| 24 October 2026 | Progress tracker and handoff instructions | PowerShell validation passed: all four Markdown documents read as UTF-8, seven local links resolved, required tracker sections and P0–P9 were present. Workspace inventory contained only those four documents. |
| 24 October 2026 | Development environment | JDK executable reported 21.0.1; SDK/platform/build-tool and Gradle-cache directories inspected; `adb devices -l` returned no devices; `git rev-parse --show-toplevel` confirmed the workspace is not a repository. See A08. |
| 24 October 2026 | Directory skeleton | PowerShell checks confirmed 16 key directories, 17 UTF-8 Markdown documents, and 13 resolving local links. Twenty source/resource/test leaf directories have `.gitkeep` files. No Gradle, manifest, or Kotlin application files present. |
| 24 October 2026 | Installed toolchain | Download checksums verified for Studio, Gradle, tools 23 and Java SDK Manager 22; Java/Gradle/CLI/ADB/emulator versions checked. `sdkmanager --list_installed` includes API 36, Build Tools 36.0.0, platform-tools 37.0.1, emulator 37.2.12, and TV API 36 x86_64 revision 4. Emulator acceleration check exited 0 (AEHD 2.0). |
| 24 October 2026 | Local bootstrap checks | `python -m unittest discover -s tests -v` passed seven tests after three expected initial red-test failures; `python scripts/check_repository.py`, PowerShell parser checks, `git diff --cached --check`, and actionlint 1.7.12 passed. Independent reviewer found no actionable issues. Headless review CLI rejected its configured model, so an available reviewer agent provided the independent review. |
| 24 October 2026 | GitHub bootstrap CI | Push run [40038710374](https://github.com/FireAmun/Iptv/actions/runs/40038710374) and PR run [40038721667](https://github.com/FireAmun/Iptv/actions/runs/40038721667) completed successfully at head `e80af5a957183ea59fbc8319278ab46f1a82e043`. Repository checks and final CI gate passed; Android job explicitly skipped because no Gradle app exists. Further pushes require their own latest-head checks before merge. |
| 24 October 2026 | First Android compilation | Java 21 / Gradle 9.6.0: `testDebugUnitTest lintDebug assembleDebug :app:assembleDebugAndroidTest` succeeded, including final language callback wiring. App JVM tasks have NO-SOURCE; not behavioral coverage. Independent review passed after fixing test locale isolation. No playback/physical-device/Billing tests. |
| 24 October 2026 | First cloud device probe | [Run 40040649939](https://github.com/FireAmun/Iptv/actions/runs/40040649939): Android build/lint passed; phone API 28 ran both tests and caught intentionally missing locale callbacks at the Arabic assertion. TV API 36 exited before boot: 3.8 GB available versus 7.4 GB required userdata space. CI now bounds userdata to 2 GB and frees unused SDK space on that disposable runner. Green checks for the wired implementation remain required on [PR #2](https://github.com/FireAmun/Iptv/pull/2). |
| 24 October 2026 | Bootstrap completion | PR #1 merged as `050ef7860f160746d233e55c91ab88237cefffef`; branch deleted, main synced. Postmerge CI run [40038960591](https://github.com/FireAmun/Iptv/actions/runs/40038960591) succeeded (bootstrap Android skip). |
| 24 October 2026 | App-shell delivery | PR #2 merged as `3fe4b3e80295a3ef55e7490c977cb425c5963aa8`; local/remote work branch removed. [Exact-head CI](https://github.com/FireAmun/Iptv/actions/runs/40042526208) passed, with 2 tests per emulator and no failures/errors/skips. Postmerge main CI also succeeded; actual state inspected before this slice. |

| 24 October 2026 | M3U/playback local checks | Java 21 full build/lint passed: `:core:model:test :core:data:test testDebugUnitTest lintDebug assembleDebug :app:connectedDebugAndroidTest`. Reports contain 19 JVM and 8 API 34 device tests, zero failures/errors/skips. Regressions covered quote-aware attributes, EOF budget, URL incompatibility, blocking-read/provider-open cancellation, immediate background player release and TV dialog focus. Final independent review found no actionable issues. |
| 24 October 2026 | Hands-on phone use | API 34 emulator: real Downloads picker imported two synthetic entries; actual video frames, native pause/seek controls, HTTP consent cancel/accept, missing-stream error/Retry, and Back to retained library observed. Visual inspection prompted status-bar contrast fix. No physical/private-provider compatibility claim. |

| 24 October 2026 | Local API 36 TV reproduction | Focused TV URL/playback case passed, then `:app:connectedDebugAndroidTest` passed all 8 tests in 1m 17s, zero failures/errors/skips. Current XML: `app/build/outputs/androidTest-results/connected/debug/`; ignored log `.tools/tv-full-repro.log`. D-pad English/Arabic round trip and RTL/focus appearance were also inspected manually. Local emulator stopped afterward. |
| 24 October 2026 | First M3U cloud probe | [Run 40121096692](https://github.com/FireAmun/Iptv/actions/runs/40121096692) at `f4179f97e84b04385bea0e47dd0efde03fba95bc`: repository/build/lint/JVM and all 8 API 28 phone tests passed. TV timed out at 25 minutes during file-import activity recreation; partial logs show TV URL/playback completed and repeated emulator ColorBuffer failures. No complete TV result from this run. CI now uses supported `swiftshader` instead of deprecated `swiftshader_indirect`, matching successful local runs. This is a hypothesis tested by subsequent PR checks, not proof of root cause. |

For future checks, record the exact meaningful command or report path, outcome, relevant tool/build version, and physical device when applicable. Never replace a failure with an assumed pass or claim completion from another agent's summary without checking evidence.

25 October diagnostic verification: `:app:assembleDebugAndroidTest` passed. A forced two-second CancellationFlowTest run failed with instrumentation termination and retained `IptvTestDiagnostics` stacks in its report logcat. The complete API 36 TV suite then passed all 8 tests in 1m 20s with the 90-second per-test limit; XML has zero failures/errors/skips. Independent diagnostic review found no actionable issues. Repository seven tests, Markdown links, actionlint and diff checks passed. Cloud run [40123533464](https://github.com/FireAmun/Iptv/actions/runs/40123533464) at `72aa0bb` had 8 phone passes but no complete TV result; the diagnostic revision needs fresh CI.

25 October cleanup fix verification: diagnostic run [40140423491](https://github.com/FireAmun/Iptv/actions/runs/40140423491) captured the same cleanup synchronization stack as the local reproduction. After moving locale restoration outside Compose/scenario lifetime, the complete local API 36 TV suite passed all 8 tests in 1m 41s, zero failures/errors/skips. Logs show scenario closure preceding locale reset. Independent review approved the test-only fix; fresh CI on its pushed revision remains required.

25 October M3U delivery: [CI 40141636364](https://github.com/FireAmun/Iptv/actions/runs/40141636364) at `5137afd44c018d854cda9baa89462e17061ed486` passed all five checks. Downloaded reports confirm 8 API 28 phone tests and 8 API 36 TV tests, zero failures/errors/skips. PR #3 merged bound to that head as `2ba4feeed34abee6821eb0f9c39b1a7aa0e861b6`. Merged tree matched the tested head, local main pulled fast-forward, remote/local `feat/m3u-playback` removed, and Cinema work restored on its branch based on current main.

25 October Cinema verification: build and final `lintDebug` passed on Java 21; 20 JVM tests have zero failures. Complete API 36 TV suite passed ten tests (`.tools/cinema-tv-visual-final.log`), then complete API 34 phone suite passed ten (`.tools/cinema-phone-final-verified.log`), zero failures/errors/skips; report copies retained under ignored `.tools/cinema-tv-results` and `.tools/cinema-phone-results`. Cold-process light-mode retention was visually verified on both devices, including different PIDs. Phone real Downloads picker imported two synthetic entries; theme change retained them; native video frames/pause/seek to 18 seconds, HTTP cancel/accept, 404 error/Retry and Back/library retention observed. TV D-pad language/theme/URL entry/IME/Back and both modes/RTL visually checked. [Screenshots](docs/design/app/README.md) saved. Independent final review found no actionable issues. Fixture server stopped gracefully and emulators stopped; no firewall, driver or reboot changes.

25 October standalone phone: PowerShell launcher opened a native `Android Emulator - Iptv_Phone_API_34:5554` window, ADB reported one device and completed boot, and IPTV opened. A second invocation reused the running-device guard; no second emulator started. Local VS Code task JSON and desktop shortcut target/arguments were inspected. PowerShell syntax, seven repository tests, Markdown links, actionlint and whitespace checks passed; independent review approved after broadening the guard to all QEMU emulator processes. The owner's interactive phone is intentionally left running.

## 9. Next-session handoff

**Task:** Finish Xtream server login verification and exact-head PR delivery on `feat/xtream-login`; inspect actual state and the current-work section below. Then implement Keystore-backed source storage, Room catalogue identities and atomic refresh. PR #3 merged as `2ba4feeed34abee6821eb0f9c39b1a7aa0e861b6`; Cinema PR #4 as `fbb0859898cf93d0e4b438509def653c6fe90c1c`; standalone-phone PR #5 as `57e36ac969f1dbf32ba009995c8406b3deed0ace`, with main synced and work branches removed. Do not repeat resolved design questions or recreate merged branches.

The owner is using the standalone emulator and explicitly declined updating it during this feature on 25 October. Preserve that session and use cloud devices for this slice. Desktop/VS Code shortcuts are intentionally local; the PowerShell script and instructions are versioned.

**Inputs:** [M3U/playback guide](docs/development/m3u-playback.md), [slice plan](docs/superpowers/plans/2026-10-24-m3u-playback.md), A01/A02, actual GitHub and workspace state.

**Constraints:** Local-first, Arabic/English, early TV coverage, no external idea discussions, no credentials in logs. Local hands-on testing is now explicitly authorized: one emulator at a time, no host firewall/driver/reboot changes. Earlier shutdown cause and SDK Manager copy issue remain unresolved. Use synthetic sources unless the owner supplies authorized media.

**Verification source:** Cinema local checks: 20 JVM + ten tests each on API 34 phone and API 36 TV, plus hands-on [screenshots](docs/design/app/README.md). Current device XML contains the phone run; both report sets were inspected and copied before replacement. Exact-head [PR #4 checks](https://github.com/FireAmun/Iptv/pull/4/checks) separately record cloud API 28 phone and API 36 TV results. Treat the live PR as the source for completed merge/branch cleanup. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`; CI artifact `iptv-debug` expires after seven days.

**Known limits:** Library survives activity/language/appearance recreation, not process death. Language and appearance preferences persist. Background playback releases the player and prepares afresh on return. No Xtream, EPG, source/catalogue persistence, pairing, billing or telemetry. Provisional import limits need hardware measurements; private providers, physical TV boxes, endurance and codec gaps remain unverified.

## 10. Session log

| Date | Work delivered / decisions | Remaining next step |
| --- | --- | --- |
| 24 October 2026 | Read/reviewed existing product brief; recorded no external idea discussions; prepared native Android/managed-monitoring direction. | Architecture document. |
| 24 October 2026 | Delivered architecture specification and linked it from the brief; checked document integrity. Application code not started. | First implementation plan. |
| 24 October 2026 | Created progress tracker and root agent handoff instructions for continuing across chats. | First implementation plan; inspect toolchain/hardware availability. |
| 24 October 2026 | User prioritized directory setup first. Created module/source/test layout, module READMEs, root guide, ignore/line-ending rules, and toolchain inventory. No application code or build configuration added. | First-build implementation plan and compatible Gradle setup. |
| 24 October 2026 | Downloaded/verified missing Android tools; SDK TV image installed. New CLI TV-image resolution and legacy SDK layout needed workarounds; TV AVD boot remains unverified. | First app build and TV AVD validation. |
| 24 October 2026 | User supplied GitHub remote and authorized branch/PR/CI/merge/delete workflow. Initialized Git/empty base, connected remote, added CI and checker tests, enabled merged-branch deletion. Branch protection rejected by current plan. | Review, run remote CI, merge bootstrap PR, delete work branch. |
| 24 October 2026 | Independent review and local checks passed; opened [PR #1](https://github.com/FireAmun/Iptv/pull/1); both push and PR CI passed for its first revision. Delivery status is available at the live PR link. | Recheck latest-head CI after the handoff update, merge/delete, then first Android build plan. |
| 24 October 2026 | Bootstrap PR #1 merged and branch deleted. Implemented first phone/TV debug build, language switching and device smoke CI in [PR #2](https://github.com/FireAmun/Iptv/pull/2). Local Java 21 build/lint and repository checks passed; independent review issue fixed. Cloud phone red tests caught absent callbacks; TV runner storage failure diagnosed and corrected. Local device tools stopped after firewall/restart report. | PR #2 is the live source for final CI/merge state; finish delivery only if outstanding, then M3U-to-playback. |

| 24 October 2026 | Implemented file + URL M3U import, separate phone/TV library UIs and Media3 playback; 19 JVM + 8 local tests per phone/TV emulator, hands-on synthetic phone playback and TV RTL/focus inspection, and independent review passed. [PR #3](https://github.com/FireAmun/Iptv/pull/3) records integration state. Local testing explicitly re-authorized by owner. | Finish latest-head cloud CI and PR integration, then persistence. |

## 11. Maintenance rules

- Update the date, overall/current phase, and next task first so the top of this file stays useful.
- Keep decisions and deliverables linked to their detailed documents; avoid copying full specifications here.
- When a decision changes, update its row and the relevant source document, then record the change in the session log. Preserve why an earlier decision was replaced when that affects implementation.
- Add concise work-item IDs under the active phase when a detailed plan exists; do not create a second conflicting task list.
- Record actual delivered files, check results, unresolved failures, and unblock actions. If work is interrupted, state the last completed step and what remains.
- Before a handoff, verify that referenced local files exist and that current statuses agree with the workspace. Mark unknowns explicitly.
- Keep the session log concise: one meaningful row per work session or milestone, with more detailed evidence linked elsewhere.

## Current Xtream login work — 25 October 2026

Owner requested playlist-name/server/username/password login alongside file and M3U URL. [Design](docs/superpowers/specs/2026-10-25-xtream-login-design.md) and [plan](docs/superpowers/plans/2026-10-25-xtream-login.md) define real Player API auth, live/movie/series catalogue and episode navigation. Credentials remain session-only; persistence is not delivered. Streaming Gson JsonReader is used without reflective binding.

Working branch: `feat/xtream-login`. Implemented API adapter, ViewModel atomic source replacement and episode/back state, four-field phone/TV forms, bilingual errors/content metadata and version 0.4.0/code 4. Build/lint and 31 JVM tests passed (2 model, 22 data, 7 app; zero failures/errors/skips). Review found dot-only credential and empty-episode edge cases, reproduced as two failing regressions then fixed; fresh independent review found no further actionable issues. Added synthetic rejected-login/retry/catalogue/episode/playback device flows and Arabic/light form/cancel checks, with cloud screenshots for visual review. Latest device build/CI and screenshot inspection remain pending. The owner explicitly chose to preserve the current emulator session; no local device update/tests are performed for this slice.

PR #5 was merged as `57e36ac969f1dbf32ba009995c8406b3deed0ace` after all five latest-head checks passed in run `40190833522` at `5ffe081690b82efccf5cfa4b9a7b0cd2b30a03f1`; downloaded XML recorded ten tests on each phone API 28 and TV API 36, zero failures/errors/skips. Local main was fast-forward synced and merged branches removed before starting this feature.
