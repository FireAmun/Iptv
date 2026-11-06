# First Android build

The original app-shell milestone delivered mobile/tablet and TV launchers, English/Arabic resources, persistent language selection, and TV remote focus. The current prototype also includes [M3U import and playback](m3u-playback.md) and the approved [Cinema dark/light appearance](../design/app/README.md); it supplies no channels. See the tracker for PR integration status.

## Build and install

Requires JDK 21, SDK platform 36 and Build Tools 36.0.0. On the prepared Windows workspace:

```powershell
. ./scripts/android-env.ps1
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
./gradlew.bat --no-daemon --console=plain testDebugUnitTest lintDebug assembleDebug :app:assembleDebugAndroidTest
```

Other machines should set `JAVA_HOME` and `ANDROID_HOME` to their own installations, or set `sdk.dir` in ignored `local.properties`. Use `bash ./gradlew` on Linux/macOS. Do not commit local SDK paths.

APK: `app/build/outputs/apk/debug/app-debug.apk`. CI publishes `iptv-debug` for seven days. It is debug-signed, uses temporary ID `com.fireamun.iptv.prototype`, and is not a Play release. Prototype minSdk 28 is a test baseline, not a final support policy. Compile/target SDK is 36 and bytecode target is JVM 17.

## Current host restriction — 6 November 2026

Use the existing GitHub Actions Ubuntu/KVM phone API28 and TV API36 matrix for instrumentation and persistence testing. Windows AEHD/GVM and HAXM are prohibited on this laptop. The ten old AVDs and their backup were removed at the owner's request; do not restore or recreate them automatically. Cloud Linux KVM is a different hypervisor path and remains authorized.

The owner's post-restart verification recorded HypervisorPresent=True, GVM Stopped/Disabled and WHPX installed and usable. This verifies acceleration capability, not long-running guest stability or the cause of earlier resets. It does not authorize creating a new local AVD. Follow [AGENTS.md](../../AGENTS.md) before any future device action.

Local repository/JVM tests, lint and APK/test-APK builds remain authorized; the build command above does not launch a device. Do not run connectedDebugAndroidTest, a Gradle managed-device task, Android Studio's Run/Device Manager launch, an old emulator command or a fixture/persistence script against a personal device.

## Manual personal-phone use

Only for an owner-authorized device, use a data-preserving update and operate the app manually:

```powershell
$deviceSerial = '<owner-authorized-device-serial>'
adb -s $deviceSerial install -r app/build/outputs/apk/debug/app-debug.apk
```

Do not uninstall, clear app data, install test fixtures or run instrumentation/persistence scripts on a personal phone or TV. Installation/signing and same-package requirements are described in [the personal-device guide](personal-device-testing.md).

## Standalone emulator launcher

scripts/start-phone.ps1 is intentionally disabled under the current host policy. Old desktop/VS Code shortcuts may still call it, but it now fails before accessing the SDK or starting a guest. Do not follow older instructions to recreate Iptv_Phone_API_34 or any removed review device.

If the owner later explicitly authorizes one fresh disposable local AVD, implement a fail-closed launcher that checks the current SDK binary's usable WHPX result, confirms the Microsoft hypervisor and stopped/disabled legacy drivers, refuses parallel guests, and cold-boots the named new device without old snapshots. Never fall back to AEHD/GVM/HAXM or change firmware/firewall/drivers/reboot automatically.

## Historical environment evidence

The original October local tests were run before the November restriction. The owner has since reported repeated unclean resets with fatal firmware WHEA records; emulator launch/use is correlated in the investigation history, but the exact cause remains unconfirmed. Software graphics such as SwiftShader does not determine CPU virtualization and did not establish host safety. Preserve earlier logs and do not count an interrupted suite as passed.

The final capacity TV suite was interrupted after65 of91 cases; no final JUnit success or following persistence-probe result exists. Complete latest-head cloud phone/TV reports and all process markers remain mandatory before merge. SDK build tools, project source, signing/debug keys and investigation evidence are retained.

See [the CI workflow](../../.github/workflows/ci.yml), [environment notes](environment.md) and [progress](../../progress.md) for current evidence. The historical SDK Manager copy-integrity issue remains separate from host reset investigation.

## Dependency basis

Versions are fixed in [the catalogue](../../gradle/libs.versions.toml). Use AGP's built-in Kotlin rather than also applying `kotlin-android`. Compatibility was selected from the official [AGP 9.4 notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes), [built-in Kotlin guide](https://developer.android.com/build/migrate-to-built-in-kotlin), [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping), and [TV releases](https://developer.android.com/jetpack/androidx/releases/tv), checked 24 October 2026. Per-app locale storage follows [Android's AppCompat locale guidance](https://developer.android.com/guide/topics/resources/app-languages).
