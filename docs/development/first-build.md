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

Install only when device testing is intended:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.fireamun.iptv.prototype/.MobileActivity
adb shell am start -n com.fireamun.iptv.prototype/.TvActivity
./gradlew.bat --no-daemon --console=plain :app:connectedDebugAndroidTest
```

Run with one emulator/device connected. Connected tests exercise English → Arabic → English, RTL/LTR, recreation, TV focus, file/URL import and playback lifecycle. The original shell had no business-rule JVM tests; the current slice adds model, parser, transport and ViewModel tests. Use the additional core JVM commands in [the playback guide](m3u-playback.md).

## Local device testing authorization

During the app-shell milestone the owner reported a prior laptop restart and cancelled a Windows Firewall network-access prompt. No elevation, driver install, firewall-rule change or reboot command was issued. ADB's TCP listener was observed on `127.0.0.1:5037`. Windows recorded an unexpected shutdown; its cause remains unresolved. The tools were stopped then. On 24 October 2026 the owner explicitly re-authorized local hands-on app testing alongside CI: use one emulator at a time with no host firewall/driver/reboot changes. See [the playback guide](m3u-playback.md).

The TV AVD was recreated with the installed TV API 36 image and reached ADB online before being stopped. This is not a completed UI or playback test. SDK Manager's versioned local copy also contained an unreadable JAR; the intact downloaded tools were used for AVD recreation. Local SDK/tool integrity and reboot investigation remain separate environment follow-up work.

CI runs the device tests on disposable Linux runners: API 28 phone and API 36 Android TV. The first TV attempt exited before boot because its default userdata partition needed more disk than the runner had available. CI now sets a 2 GB data partition and removes unused preinstalled .NET/NDK directories on the disposable TV runner. No local firewall permission or Windows virtualization changes are needed for that path. See [the workflow](../../.github/workflows/ci.yml) and [progress](../../progress.md) for actual results.

## Dependency basis

Versions are fixed in [the catalogue](../../gradle/libs.versions.toml). Use AGP's built-in Kotlin rather than also applying `kotlin-android`. Compatibility was selected from the official [AGP 9.4 notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes), [built-in Kotlin guide](https://developer.android.com/build/migrate-to-built-in-kotlin), [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping), and [TV releases](https://developer.android.com/jetpack/androidx/releases/tv), checked 24 October 2026. Per-app locale storage follows [Android's AppCompat locale guidance](https://developer.android.com/guide/topics/resources/app-languages).
