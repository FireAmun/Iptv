# First Android Build Implementation Plan

> **For agentic workers:** Use executing-plans to implement these tasks in order.

**Goal:** Produce an installable mobile/TV debug APK with English/Arabic launch screens and persistent language selection.

**Architecture:** One prototype application with mobile and TV entry activities and separate `ui:mobile` / `ui:tv` libraries. Use Android resources and AppCompat per-app locales; only configure modules needed now. Source import and playback are the next milestone.

**Tech Stack:** Gradle 9.6.0, AGP 9.4.0 with built-in Kotlin, Kotlin Compose compiler 2.2.10, Compose BOM 2026.02.01, TV Material 1.1.0, Activity Compose 1.12.4, AppCompat 1.7.1, Java 21 runtime / JVM 17 bytecode.

**Spec:** [Architecture specification](../specs/2026-10-24-iptv-architecture-design.md), sections 2, 3, 8, 9.

## Global constraints

- Arabic/English, phone/tablet and Android TV/Google TV from the first build.
- One temporary application ID `com.fireamun.iptv.prototype`; branding remains open.
- Prototype minSdk 28, compile/target 36. This floor is a feasibility baseline, not a release compatibility promise.
- No provider credentials, private source data, telemetry SDK, billing, or backend in this milestone.
- Disable backup and device transfer of app data. No Internet permission until networking is implemented.
- Use the authorized branch/PR/latest-head-CI/merge/delete workflow.

### Task 1: Reproducible build and minimal entry points

**Files:** Create root settings/build/properties, `gradle/libs.versions.toml`, complete checksum-pinned wrapper, module build scripts, app manifest/resources/activities and `AppLanguage.kt`, UI library manifests/screens/resources.

**Interfaces:** `MobileWelcome(onChangeLanguage: () -> Unit)` and `TvWelcome(onChangeLanguage: () -> Unit)` composables; activities own locale switching using AppCompat. Each screen offers one focusable language switch and states that users supply their own content.

- [ ] Configure only `:app`, `:ui:mobile`, `:ui:tv`; Google/Maven Central repositories and fixed dependency versions.
- [ ] Generate wrapper from verified local Gradle: `gradle -p .tools/wrapper-bootstrap wrapper --gradle-version 9.6.0 --distribution-type bin`; copy generated scripts/JAR/properties to root, pin official distribution SHA-256.
- [ ] Build entry screens with native Android string resources and `supportsRtl`; use Material 3 on mobile and TV Material on TV, scrollable layouts with safe margins and visible focus.
- [ ] Register mobile LAUNCHER and TV LEANBACK_LAUNCHER separately, optional leanback and touchscreen features, vector prototype icon and TV banner.
- [ ] Run `. ./scripts/android-env.ps1`, select installed JDK 21 for parity with CI, then `./gradlew.bat --no-daemon --console=plain testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`.

No artificial unit tests for static scaffolding; the unit-test task may report NO-SOURCE. Behavioral checks belong to Task 2.

### Task 2: Language persistence and TV remote smoke tests

**Files:** Create `app/src/androidTest/kotlin/com/fireamun/iptv/prototype/WelcomeTest.kt`; add AndroidX runner/JUnit/Compose testing dependencies to app build script.

**Interfaces:** Launch `MobileActivity` / `TvActivity`; language switch exposes semantics tag `language_switch` and TV requests initial focus there.

- [ ] Write instrumentation tests that launch each activity, activate the language switch (TV through D-pad center), verify Arabic copy and RTL, recreate the activity and verify selection survives. Reset locale after each test.
- [ ] Run against the initial static screens before locale callback wiring; expect Arabic assertions to fail, then wire `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(...))` and rerun.
- [ ] Validate/recreate only the project TV AVD; preserve unrelated AVDs. Record actual device/API results; compilation alone is not runtime evidence. Session adjustment: after the owner's firewall/restart report, stop local emulator/ADB and run phone API 28 / TV API 36 tests on disposable GitHub runners instead. Do not restart local device tools automatically.

### Task 3: Evidence and GitHub delivery

**Files:** Update README, module READMEs, environment guide, `progress.md`; add build/run instructions in `docs/development/first-build.md`.

- [ ] Run repository unit/link checks, actionlint, PowerShell parsing, whitespace checks and complete Android build/lint/test tasks.
- [ ] Obtain independent code review, resolve material findings, commit/push branch, open PR.
- [ ] Verify repository, Android and final CI checks at the latest exact head. Merge bound to that SHA, confirm merged state, sync main with fast-forward only, delete merged branch.
- [ ] Handoff: next is local M3U selection/import and Media3 playback with synthetic parser fixtures and authorized runtime media.

## Self-review

This plan implements the authorized first-build slice of the larger architecture. Catalogue, playback, pairing, billing and managed monitoring remain later work. Hardware/source limitations and provisional identity/minSdk stay explicit. UI callbacks and test tags agree across tasks; no domain modules or unused production dependencies are added.
