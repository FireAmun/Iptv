# IPTV development progress

Last updated: 24 October 2026 (Asia/Riyadh)  
Overall status: Directory skeleton, local Android tools, and GitHub CI delivered; bootstrap CI passed on GitHub. Application build configuration and code have not started.  
Current phase: P1 — first-build readiness.  
Next task: Complete any remaining bootstrap PR delivery steps, then write the scoped first-build plan and configure the minimal Android build with early TV coverage.

## 1. Start here in a new chat

1. Read this tracker, then the linked product brief and architecture sections relevant to the task.
2. Inspect the actual workspace and current user instructions before relying on a recorded status. Later user decisions supersede earlier recommendations.
3. Continue from the next task rather than restarting product research or rewriting completed documents.
4. Keep this file current after meaningful work: deliverables, evidence, open items, blockers, and the next concrete task.
5. Never store provider credentials, private playlist/stream URLs, purchase tokens, signing keys, or identity documents here.

This tracker records project state; it is not a substitute for source code, test results, the detailed specification, or the user's latest authorization. The user authorized starting, directory setup, downloading missing tools, and GitHub delivery. For ordinary changes the standing instruction is branch -> push -> PR -> inspect latest-head CI -> merge after success -> delete branch. See [the delivery workflow](docs/development/github-workflow.md). No application Gradle configuration/code, hosted application backend, purchase, or Play publication exists yet. Do not request authorization again for work already authorized.

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

The architecture is a delivered draft, not evidence of implemented or tested functionality. Branding, the complete free/paid offer, detailed UI design, and release compatibility remain unsettled.

## 3. Documents and delivered artifacts

| ID | Deliverable | Location | Status / evidence |
| --- | --- | --- | --- |
| A01 | Product brief, decisions, feature inventory, research, and launch proposal | [iptv-product-brief.md](codex/docs/iptv-product-brief.md) | Delivered; updated through 24 October 2026. |
| A02 | Architecture: modules, screens, data/imports, playback, pairing, purchases, monitoring, hardware tests | [Architecture specification](docs/superpowers/specs/2026-10-24-iptv-architecture-design.md) | Draft delivered and self-reviewed. Local document links and UTF-8 readback checked. |
| A03 | Cross-chat progress tracker | [progress.md](progress.md) | Delivered; required sections, P0–P9, UTF-8 readback, and local links checked. |
| A04 | Automatic workspace handoff instructions | [AGENTS.md](AGENTS.md) | Delivered; directs future project agents to read/update this tracker; linked tracker exists. |
| A05 | Implementation plan for the first playable proof | No file created yet. | Not started. |
| A06 | Android application / build / executable proof | Directory skeleton exists; no Gradle configuration or application source yet. | Not started; no build or runtime evidence. |
| A07 | Project directory skeleton and structure guide | [README.md](README.md), `app/`, `core/`, `ui/`, `gradle/`, `scripts/`, `docs/` | Delivered; module responsibility READMEs and source/resource/test placeholders. These are not configured Gradle modules yet. |
| A08 | Local toolchain inventory | [environment.md](docs/development/environment.md) | Delivered; JDK/SDK/caches inspected, ADB queried; no build or emulator run. |
| A09 | Tool downloads and environment activation | [android-env.ps1](scripts/android-env.ps1), [environment inventory](docs/development/environment.md), ignored local binaries | Delivered. Studio/JBR, Gradle, command-line tools, SDK Build Tools/platform-tools/emulator, and TV image installed; versions and acceleration checked. TV AVD definition still needs validation/boot. |
| A10 | GitHub CI and standing PR workflow | [ci.yml](.github/workflows/ci.yml), [delivery guide](docs/development/github-workflow.md), [bootstrap PR #1](https://github.com/FireAmun/Iptv/pull/1) | Verified: seven local tests, repository/actionlint/PowerShell checks passed; GitHub push and PR CI passed at `e80af5a`. [PR run](https://github.com/FireAmun/Iptv/actions/runs/40038721667). PR link is the live source of delivery/merge state. Automatic remote branch deletion enabled. |

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
| P1 — Plan and development readiness | In progress — directory skeleton and environment inventory delivered | First-proof implementation plan, toolchain inventory, compatible dependency/minSdk proposal, Git setup decision, test fixture/hardware inventory. | Plan identifies files, bounded tasks, checks, assumptions, and missing resources; no invented hardware or test credentials. |
| P2 — Foundation and playable proof | Not started | Android project, one app identity, minimal mobile/TV entry points, safe source entry, initial M3U import, Media3 playback, basic error states. | Reproducible build; authorized source plays on phone and TV; minimal D-pad path works; record actual hardware/stream limitations. |
| P3 — Sources, catalogue, search, and EPG | Not started | Xtream-compatible adapter, all agreed content kinds, multiple sources, Room/Paging, atomic generation refresh, Arabic-aware search, XMLTV/guide mapping, favourites/progress storage. | Large-fixture imports, cancellation/process interruption, identity preservation, search, and guide time-zone checks pass. Missing metadata never becomes invented hierarchy. |
| P4 — Full device experience and playback | Not started | Phone/tablet/TV screen flows, RTL, stable focus restoration, series/episodes, resume, audio/subtitles, parental PIN/restrictions, settings. | Core journeys work with touch and D-pad in Arabic/English; restrictions apply through shared rules; representative device checks recorded. |
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
| D06 | Product name, identity, visual direction | No name/logo chosen. Select permanent package identity before Play registration; use an explicitly temporary prototype ID if needed. | Branding and permanent Play setup; not local prototype research |
| D07 | Hardware and authorized source inventory | No connected physical devices or authorized provider verified. Phone AVDs exist; TV API 36 image installed. Generated TV AVD has profile/config warnings and is not boot-verified. Never put credentials here. | Runtime/physical-device verification; local development can proceed |
| D08 | Toolchain and version control | Updated tools installed and executable checks passed; Git initialized with specified remote and a work branch. App wrapper and dependency pins remain. See A08/A09. | First Android build configuration |
| D09 | minSdk / compatible dependency versions | No floor or versions approved. Android 9 is an initial lower-device test candidate, not a compatibility promise. | Dependency pinning and release support claims |
| D10 | Remote transport implementation | Ktor CIO is a candidate; prove Android TLS, lifecycle, and reconnect compatibility. QR-first pairing; code-only pairing is not finalized. | P5 transport choice |
| D11 | Import resource/performance limits | Architecture defines bounded import behavior; choose numerical limits from fixture/device measurements. | Import hardening / P8 |
| D12 | Credential persistence integration | Prove Keystore-backed encryption, optional Tink integration, and backup exclusions; DataStore does not encrypt by itself. | Source storage and P5 |
| D13 | Codec gaps | No device codec gaps demonstrated; FFmpeg audio or libVLC is not included by default. | Only any demonstrated unsupported codec requirement |
| D14 | Google Play readiness | Account type/date, verification state, store projects, test resources, and signing setup not inspected. Recheck current requirements when relevant. | P9; prepare independently when authorized |
| D15 | Final scope and design review | Working scope is documented; any additions/changes need to be reconciled with the first implementation plan. Detailed visual screens are not yet designed. | A stable implementation baseline; not more external idea discussions |
| D16 | Server-enforced branch protection | GitHub rejected protection with HTTP 403: this private repository needs Pro. Auto-merge remains unavailable; remote merged-branch deletion is enabled. Agents still enforce the authorized PR/CI/merge flow. | Server-side enforcement only; no plan or visibility changes authorized/performed |

## 7. Current blockers and risks

No blocker prevents the first implementation plan. No Android application build or playback test has been attempted. Bootstrap CI and repository tests are separate from app coverage.

- Physical hardware/source availability remains unknown. TV AVD profile/config warnings must be resolved before claiming an emulator boot.
- Updated tools execute successfully; the complete app dependency combination and Android build remain unverified. CI uses Java 21, while local activation selects bundled JBR 25.
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
| 24 October 2026 | Application build, unit/instrumentation tests, physical devices, Play Billing | Not run. Directory skeleton only; no buildable Gradle project exists. |

For future checks, record the exact meaningful command or report path, outcome, relevant tool/build version, and physical device when applicable. Never replace a failure with an assumed pass or claim completion from another agent's summary without checking evidence.

## 9. Next-session handoff

**Task:** Check the live status of [bootstrap PR #1](https://github.com/FireAmun/Iptv/pull/1); finish CI/merge/delete only if still outstanding, then prepare the first-build/source-to-playback plan and minimal Android project.

**Inputs:** A01, A02, A07–A10 and the latest GitHub PR/CI state. Preserve the existing layout, local-first operation, Arabic/English, early TV coverage, managed monitoring, and no external idea discussions.

**Steps:** Inspect the latest PR and CI rather than assuming a pending run passed. Follow the standing delivery flow, then use the environment activation script and inventory; select a minimal authorized/synthetic fixture path and compatible app dependency versions; write bounded tasks/checks for mobile/TV launch, import, and playback. Record TV AVD/hardware gaps separately from local build work. The user has authorized ordinary PR/CI/merge/delete delivery; do not repeat that approval question.

**Expected output:** A linked implementation plan with its verification approach and a precise next executable task. Add the actual plan path to A05 when created, update P1, and replace this handoff with the next task.

**Suggested new-chat prompt:** “Read progress.md and the linked architecture specification. Continue from the next-session handoff, preserve the recorded decisions, and update progress.md with the work and verification.”

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

## 11. Maintenance rules

- Update the date, overall/current phase, and next task first so the top of this file stays useful.
- Keep decisions and deliverables linked to their detailed documents; avoid copying full specifications here.
- When a decision changes, update its row and the relevant source document, then record the change in the session log. Preserve why an earlier decision was replaced when that affects implementation.
- Add concise work-item IDs under the active phase when a detailed plan exists; do not create a second conflicting task list.
- Record actual delivered files, check results, unresolved failures, and unblock actions. If work is interrupted, state the last completed step and what remains.
- Before a handoff, verify that referenced local files exist and that current statuses agree with the workspace. Mark unknowns explicitly.
- Keep the session log concise: one meaningful row per work session or milestone, with more detailed evidence linked elsewhere.
