# Development environment inventory

Historical inventory: 24 October 2026 (Asia/Riyadh). Current host policy updated 6 November 2026.

## Current host policy

AEHD/GVM and HAXM are prohibited. The owner removed all ten old local AVDs and their backup; do not restore or recreate them automatically. Existing project/build tools/keys and investigation evidence are preserved. Local builds/JVM/lint remain authorized; use the existing Ubuntu/KVM CI matrix for device instrumentation and persistence.

The supplied post-restart verification records HypervisorPresent=True, GVM Stopped/Disabled and current emulator accel-check reporting WHPX installed and usable. This establishes capability only. No new guest or long-running stability test was performed after migration, and the cause of repeated WHEA-associated unclean resets remains unconfirmed. Any future local disposable AVD requires explicit owner authorization and a fresh fail-closed WHPX/legacy-driver check.

The historical entries below are not current authorization to use old devices or drivers. scripts/start-phone.ps1 is disabled. Follow [AGENTS.md](../../AGENTS.md) and the [current build/device guide](first-build.md).

## Historical October inventory

This is the observed local inventory after tool setup and the M3U/playback slice. See [the build guide](first-build.md) for commands and device-test restrictions; [progress](../../progress.md) records current verification.

| Item | Observed state |
| --- | --- |
| Workspace | Git at `D:/Side_p/iptv`; remote `https://github.com/FireAmun/Iptv.git`; bootstrap PR #1 merged. Ordinary work follows branch/PR/CI/merge/delete. |
| JDK | Original Oracle JDK 21.0.1 remains installed and was used for the first successful app/lint/test-APK build. CI uses Java 21. Activation defaults to Studio JBR 25.0.3; explicitly select JDK 21 afterward as shown in the build guide. JVM bytecode target is 17. |
| Android Studio | Rabbit 1 / 2026.2.1.8 archive downloaded from Google, SHA-256 verified, extracted at `.tools/ide/android-studio`; executable/product metadata and bundled Java checked. Existing machine-wide Studio preserved. IDE UI not launched. |
| Android SDK | Reused the current user's local Android SDK directory; API 36 revision 2 confirmed. Activation script sets SDK variables only in the current PowerShell process. |
| Build tools | 36.0.0 installed in the existing SDK. Older installed versions preserved. |
| Gradle | 9.6.0 downloaded and SHA-256 verified. Complete project wrapper now committed with distribution checksum; use it for builds. AGP 9.4.0 and application dependencies are fixed in the version catalogue. |
| Command-line tools | Tools 23 / Android CLI 1.0.16625370 at `.tools/android-cli`; checksum verified. Tools 22 archive verified and extracted under `.tools/android-sdk-manager`. Its SDK `cmdline-tools/22.0` copy later failed class loading; a copied JAR was not a readable ZIP. Original extracted JAR contained the class. AVD recreation used the intact original libraries with explicit SDK tools root. Repair/verify the SDK copy before relying on that manager again. |
| Platform-tools / emulator | ADB/platform-tools 37.0.1 and emulator 37.2.12 installed; executable versions checked. Emulator `-accel-check` exited 0 and detected usable AEHD 2.0. No Windows feature or driver installation was required. |
| TV system image | Android TV API 36 x86_64 revision 4 installed and present in SDK Manager's installed-package list. |
| Devices | No physical devices. Existing phone AVDs preserved; project `Iptv_Phone_API_34` uses the installed API 34 Google APIs x86_64 image. Local API 34 tests and hands-on file/playback checks passed. Project TV AVD uses API 36 / `tv_1080p`. Owner re-authorized hands-on testing after earlier suspension: one emulator at a time, no firewall/driver/reboot changes. Earlier host shutdown remains unexplained. |
| Git / GitHub CLI | Available and authenticated for the specified repository; PR delivery is documented separately. |
| App verification | Version 0.2.0 debug/test APKs compile; lint, 19 JVM tests and 8 local tests each on API 34 phone and API 36 TV pass on Java 21. M3U import and synthetic Media3 playback observed manually. See tracker/PR for cloud phone/TV results. |

## Using the tools

```powershell
. ./scripts/android-env.ps1
java -version
gradle --version
android --no-metrics --version
```

The October authorization below is historical and is superseded by the 6 November host restriction. Use cloud device CI now; do not launch a guest, restore an old AVD, change host firewall/drivers or reboot automatically. The SDK Manager copy also needs integrity repair before use; the inventory above separates original archives from the later copy failure.

SDK Manager 22 needs a correct SDK root/layout for image discovery. The later class-loading failure in the versioned copy is documented above; do not treat earlier executable checks as proof that copy still works. Native-access warnings occurred with Java 25. Binary downloads, checksums, and installation logs are kept under ignored `.tools/` and are not pushed to GitHub.

Compose/TV and entry-point libraries resolve through Gradle. Media3 1.11.1, OkHttp 5.3.2 and Coroutines 1.10.2 are included. Room and other architecture packages will be added when their capabilities are implemented; no standalone framework installation or hosted backend is required.

Prototype baseline: minSdk 28, compile/target 36, temporary ID `com.fireamun.iptv.prototype`. Release identity and supported hardware remain open. Keep local SDK paths in ignored machine configuration.
