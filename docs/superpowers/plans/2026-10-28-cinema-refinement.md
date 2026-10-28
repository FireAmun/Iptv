# Cinema refinement implementation plan

> **For agentic workers:** Execute in this session using executing-plans and focused subagent-driven tasks. User instructions override automatic commits: leave all work uncommitted and unpushed until the owner reviews the local app.

**Goal:** Implement the approved Cinema review across the existing native phone/TV UI, with Settings last and a searchable scrolling category picker.

**Architecture:** Existing LibraryState, ViewModels, Paging/repositories and playback session stay unchanged. UI-local state owns sheet visibility and category-label filtering; actions call existing callbacks. Shared design tokens/vector icons, device-adapted forms and themed existing Media3 controls provide the visual refresh.

**Tech stack:** Existing Kotlin/Compose Material 3, TV Material, shared design system, Media3 PlayerView; no dependency upgrades.

**Spec:** [Approved whole-app audit](../../design/ui-audit-2026-10-27.md). Owner approved 28 October 2026. Exact local mockup sources are under `.tools/ui-review/`.

## Constraints and rulings

- Work in existing `design/ui-audit-review` branch; preserve its uncommitted docs and unrelated `docs/development/backend-options-2026-10-28.md`. No commits, pushes, PRs or merges in this pass.
- UI only. Do not modify LibraryViewModel, LibraryPagingSource, models, parsers, Room, repositories, network, LifecyclePlayerSession or playback resolution.
- Preserve masking/validation/clearing, every source action, stable test tags, no-prompt HTTP, Back/focus, stored appearance/language, cached source and favourites.
- Navigation display order: Live, Movies, Series, Favourites, Settings. Search remains top. Arabic mirrors placement while retaining logical order.
- Keep chip behavior; separate category grid button opens local search/list and calls existing `onCategory(String?)`. Returning/cancelling restores invoker focus; TV focus remains visible and distinct from selected state.
- Continue Watching and 300k remain separate functional tasks. No fake controls or capacity claim. No artificial splash delay or fabricated progress.
- One emulator at a time. Owner AVD must never receive test APKs/data clearing/private screenshots. Use a disposable AVD for synthetic instrumentation; no host firewall/driver changes or host reboot.

## Task 1 — Shared theme and mobile forms/Settings

Files: core/designsystem presentation helpers/tokens; ui/mobile CinemaTheme, MobileLibrary, MobileServerLogin, MobileSources and presentation resources. Preserve all public composable signatures/callbacks.

- [x] Use complete light/dark Material schemes and neutral sheet containers; keep contrast checks and fonts.
- [x] Add coherent neutral sheet, fixed-label field, vector icon and action components. Minimum 48dp targets, IME/short-window scroll with reachable actions, RTL with LTR URI values.
- [x] Group Settings Library/Appearance/Language with source summary/manage/refresh/remove/add actions, no source controls on browse pages. Preserve appearance/language tags/focus anchors.
- [x] Restyle login, URL, source manager, rename and confirmed removal, keeping their state and validation rules.
- [x] Retain player wrappers' behavior; visual player task below owns full-bleed placement.
- [x] Build/lint with controller coordinating one Gradle process; independent UI review.

## Task 2 — Browser, categories, cards and details

Files: app LibraryBrowser plus UI-only composable files and browse resources; app Android UI regression tests.

- [x] Add a meaningful real UI journey asserting Settings-last geometry, category picker opening/local filtering/selection/cancellation and retained chips. Run on a disposable device; confirm it fails before implementing the missing picker.
- [x] Lean bottom navigation and top Search, smaller readable headers and consistent vectors. Keep tags and TV focus behavior.
- [x] Add pinned `browse_categories` grid control, `category_picker`, `category_search`, `category_pick_N`, `category_picker_close`; lazy vertical rows, local `contains(ignoreCase=true)` filtering, selected checkmark, normal selection invokes existing callback and closes. Empty query matches all provided labels; All resets using null.
- [x] Refine compact channel/search/episode rows, 2:3 posters, metadata/favourites, details/empty/loading/error states using only existing supplied data.
- [x] Keep poster favourite target outside channel activation target; preserve all current keyboard/touch/D-pad journeys and pagination.
- [x] Build/test/lint; inspect synthetic dark/light, Arabic/English and category modal screenshots.

## Task 3 — TV forms and player/startup presentation

Files: ui/tv Composables/theme/resources, mobile/TV playback wrappers, core/playback PlayerSurface and XML UI controller styles only, shared startup UI/resources if truthful wording can use existing state.

- [x] TV forms and manager use the same palette/type/spacing grammar, bounded scrolling, explicit focus and existing cancel/submit/source behavior.
- [x] Adapt Settings to wide composition without hiding source or appearance controls; retain large-text/short-window reachability.
- [x] Full-bleed video with compact overlay header/status; preserve native PlayerView controller semantics and IDs, 30-second controls, Back/Retry/lifecycle. Restyle existing controls; never add new player behavior or unsupported track/PiP controls.
- [x] Preserve gold-to-black intro/reduced motion. Any loading copy must describe actual current restore state; do not display an unconditional provider fetch or capacity promise.
- [x] Independent review and local app/test-APK/lint.

## Task 4 — Integrated local verification and owner handoff

- [x] Local Java 21 build/test APK/lint; 64 JVM and 13 repository/script checks, Markdown links and whitespace.
- [x] Disposable local phone/TV-size synthetic UI journeys, actual category filter/Back/focus, forms and manager, theme/language, large text/landscape and playback. Never run these tests on the owner AVD or personal phone.
- [x] Capture and inspect actual implementation screenshots against approved boards, fix clipping/contrast/target issues.
- [x] Independent full UI/scope review. Verify protected behavior/data files byte-identical to baseline and preserve unrelated user files.
- [x] Produce installable Windows-signed APK. Dated preview in `.tools/releases/`; no owner-emulator update needed for the chosen S25 handoff. Owner source stays intact; no private screenshots/refresh.
- [x] Update progress.md with evidence, limitations and owner-review gate. Stop before commit/push; retain open functional backlog for resume/300k.


## Execution evidence

- Final build/lint/test-APK/JVM: `.tools/ui-refinement/final-verification-build.log`, 64 JVM cases, zero failures/errors/skips.
- API 34 disposable phone: first suite 25/25, latest category/browse 4/4 and player/startup 5/5. Native player pause/resume uses the existing visible control; no player behavior added.
- Manual wide tablet anchors and 1.3x landscape form scroll/cancel verified with synthetic sources. Manual dialog IDs required visible-label fallback across its separate Android window; no production form defect established.
- Independent reports: `.tools/ui-refinement/task-1-review.md` and `integrated-review.md`; all reported source findings addressed. Extra natural D-pad failure fixed and verified with the Dialog-local focus manager.
- API 36 disposable TV: final clean 29/29 pass; API 34 final source/login compatibility 6/6 pass. Reports: `tv-final-clean-ui-suite.log` and `phone-login-final-clean.log`. These local results preceded delivery authorization; actual latest-head CI is required before merging.
- Owner accepted the preview and authorized push on 28 October. Follow the normal reviewed PR/CI/merge cycle. Physical-device evidence remains separate; P3/P4 are not complete.

- Final margin touch/clear/reopen-empty and 1.3x landscape checks passed. Virtual input-frame stepping addresses test synchronization only; all auth/catalogue/playback outcomes remain normally synchronized and asserted. Temporary diagnostics removed. All local implementation/verification tasks are delivered. Owner explicitly accepted the preview and authorized push; these checkboxes do not replace latest-head CI or establish physical-device compatibility.
