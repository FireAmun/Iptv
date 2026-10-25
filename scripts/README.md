# Development scripts

`android-env.ps1` activates the downloaded project-local JDK, Gradle, and Android command-line tools in the current PowerShell process. It uses the existing Android SDK without changing machine-wide environment variables.

After tool downloads/extraction have completed, run from the project root:

```powershell
. ./scripts/android-env.ps1
java -version
gradle --version
android --no-metrics --version
adb devices -l
```

Project-local binary tools live under ignored `.tools/`. Check [the environment inventory](../docs/development/environment.md) for installed versions and verification status. Activating tools does not build or install the app.

To open the existing phone in its own window without Android Studio, run `./scripts/start-phone.ps1` in PowerShell. It uses `ANDROID_HOME` or the default Windows Android SDK, requires `Iptv_Phone_API_34`, and declines to start another device while any QEMU emulator is running. Close the phone window when finished. Emulator logs stay under ignored `.tools/`.

CI invokes `device-tests.sh` inside its disposable emulator runner. It preserves the connected-test exit code, runs `persistence-process-test.sh` after a successful suite and collects only synthetic test screenshots before shutdown. The process probe reinstalls app/test APKs, seeds a synthetic saved source, force-stops the target, requires restoration under a different PID and clears it. These are device-testing scripts, not launchers: do not run them against the owner's interactive emulator without an agreed testing window. Device reports/previews and CI logs record results; stubbed Python checks test failure propagation without ADB.

The environment exposes Android CLI from project-local command-line tools 23 and the Java SDK/AVD Manager from tools 22 installed in the existing SDK's standard `cmdline-tools/22.0` directory. The tools-23 `sdkmanager` wrapper is superseded on PATH by the Java manager because the new CLI stalled while resolving the TV image. Use `sdkmanager --list_installed` for SDK inventory or `android --no-metrics --sdk="$env:ANDROID_HOME" sdk list` for the new CLI.
