# Development environment inventory

Checked: 24 October 2026 (Asia/Riyadh).

This is an observed local inventory after the owner authorized downloading missing tools. Tool execution is verified below; there is not yet an Android application build.

| Item | Observed state |
| --- | --- |
| Workspace | Git initialized at `D:/Side_p/iptv`; remote `https://github.com/FireAmun/Iptv.git`; initial empty `main` baseline pushed. Project files are prepared on `chore/bootstrap-ci` for PR delivery. |
| JDK | Original Oracle JDK 21.0.1 remains installed. The local activation script selects Studio's bundled OpenJDK/JBR 25.0.3; Gradle version execution passed with both JDKs. This is not yet a JVM target/toolchain decision for application compilation. |
| Android Studio | Rabbit 1 / 2026.2.1.8 archive downloaded from Google, SHA-256 verified, extracted at `.tools/ide/android-studio`; executable/product metadata and bundled Java checked. Existing machine-wide Studio preserved. IDE UI not launched. |
| Android SDK | Reused the current user's local Android SDK directory; API 36 revision 2 confirmed. Activation script sets SDK variables only in the current PowerShell process. |
| Build tools | 36.0.0 installed in the existing SDK. Older installed versions preserved. |
| Gradle | 9.6.0 downloaded, SHA-256 verified, extracted at `.tools/gradle/gradle-9.6.0`; `--version` passed. This project has no wrapper yet. |
| Command-line tools | Tools 23 / Android CLI 1.0.16625370 at `.tools/android-cli`; checksum verified. Java SDK Manager 22 additionally downloaded/SHA-256 verified and placed at SDK `cmdline-tools/22.0` for correct AVD SDK-root discovery. The activation script selects that manager on PATH. |
| Platform-tools / emulator | ADB/platform-tools 37.0.1 and emulator 37.2.12 installed; executable versions checked. Emulator `-accel-check` exited 0 and detected usable AEHD 2.0. No Windows feature or driver installation was required. |
| TV system image | Android TV API 36 x86_64 revision 4 installed and present in SDK Manager's installed-package list. |
| Devices | No connected physical devices. Existing phone AVD definitions remain. A TV AVD creation attempt produced device-profile/config warnings; do not treat the generated definition as verified until its configuration and boot are checked. |
| Git / GitHub CLI | Available and authenticated for the specified repository; PR delivery is documented separately. |
| App verification | No Gradle project, build, tests, APK, or playback run yet. |

## Using the tools

```powershell
. ./scripts/android-env.ps1
java -version
gradle --version
android --no-metrics --version
sdkmanager "--sdk_root=$env:ANDROID_HOME" --list_installed
emulator -accel-check
```

The SDK Manager 22 needed explicit SDK-root selection when run outside its normal SDK layout. Keeping its installed copy under `SDK/cmdline-tools/22.0` lets AVD Manager discover the correct installed images. Native-access warnings may occur with Java 25; those commands exited successfully. Binary downloads, checksums, and installation logs are kept under ignored `.tools/` and are not pushed to GitHub.

Kotlin, Compose, Media3, Room, and the app's other libraries will be pinned/resolved by Gradle when the application build is configured. No standalone framework installation or custom backend is needed for the directory-only bootstrap.

During first-build setup, select and verify the complete Android Gradle plugin/Kotlin/Compose combination with Gradle 9.6.0, select minSdk explicitly, and retain a temporary prototype application ID until branding is decided. CI currently uses Java 21; verify the actual Android build on that runtime before declaring toolchain compatibility. Keep local SDK paths in ignored machine configuration.
