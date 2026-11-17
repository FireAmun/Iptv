# Try Amar Iptv on a personal phone or TV

Updated6 November2026. Candidate0.10.0/code12 includes the tested300k count guard and Continue Watching; check [progress](../../progress.md) for actual CI/delivery state. Phone selected by the owner: Samsung S25 Ultra; TV model/system remains undecided.

The6 November host restriction supersedes older emulator recipes. All ten old local AVDs were removed; do not restore/recreate them. Use cloud Ubuntu/KVM for instrumentation and persistence. Personal phones/TVs are for owner-authorized manual use and install-r only, with no fixture clearing or test scripts.

## Easiest S25 Ultra installation: copy the APK

1. On this workstation, open `D:\Side_p\iptv\.tools\releases\Amar-Iptv-0.10.0-300k-2026-11-06.apk` in File Explorer.
2. Connect the phone by USB, choose file transfer, and copy that APK to the phone's Downloads folder. No Android Studio or developer options are needed for this method.
3. Open **My Files → Downloads → Amar-Iptv-0.10.0-300k-2026-11-06.apk** on the phone. Allow My Files to install unknown apps if prompted, then install.
4. If Samsung Auto Blocker prevents the local APK installation, temporarily turn it off in **Settings → Security and privacy → Auto Blocker**, install this build, then restore the setting and My Files' install permission. Samsung's menu names can vary by One UI version; see the [official Samsung guide](https://www.samsung.com/uk/support/mobile-devices/protect-your-galaxy-device-with-the-new-auto-blocker-feature/).
5. Open **Amar Iptv** and add your source once in Settings. Each installation has its own protected local cache; it does not copy or sync the emulator's saved account.

Use the current Windows-signed local debug APK; its SHA256 is recorded with the release evidence in progress.md. This is not a Play release. Keep using the same signing identity for updates; a CI debug build can have a different signing key. Do not uninstall an existing personal installation merely to work around a signing mismatch.

## Optional: install/update from the VS Code terminal

Enable Developer options and USB debugging on the phone, connect by USB and accept that computer's debugging prompt on the phone. No Android Studio is required. Android documents the [USB device setup](https://developer.android.com/studio/run/device) and [ADB install options](https://developer.android.com/tools/adb).

```powershell
Set-Location D:\Side_p\iptv
$iptvAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $iptvAdb devices -l
& $iptvAdb -d install -r .\app\build\outputs\apk\debug\app-debug.apk
& $iptvAdb -d shell am start -n com.fireamun.amariptv/com.fireamun.iptv.prototype.MobileActivity
```

From version 0.11.0 the application ID is `com.fireamun.amariptv`, so the new build installs next to an older `com.fireamun.iptv.prototype` installation instead of updating it. The old installation keeps its own sources and progress; add your source again in the new app. Remove the old app manually only when you no longer need its data.

`-d` selects the connected physical Android device rather than the emulator. Use this shortcut only with one physical Android device attached; use `-s SERIAL` for an explicitly selected device otherwise. `install -r` updates without intentionally clearing the existing app data. No rebuild is needed to try this existing APK.

## Testing checklist on the phone

- Add one source in Settings, browse Live/Movies/Series, use top Search and toggle a favourite. Settings is the last bottom destination.
- Open the category grid button beside the existing chips, search for a category, select it, then reopen and cancel; the chip selection should remain correct.
- Inspect login, URL, playlist manager, rename/removal and light/Arabic screens against the approved Canva direction.
- Close and reopen the app: source and favourites should restore from the saved cache without another provider download. Refresh stays manual in Settings.
- Try one authorized live stream and one movie/episode, pause, use the existing 30-second seek controls and return to browsing.
- Switch English/Arabic and dark/light; check the keyboard, rotation and larger text size on the actual phone.
- Verify saved movie/episode positions, Continue/Start over/Cancel and the Continue Watching row after reopening. The300k guard is synthetically validated; practical physical-device capacity remains to be measured.

Use personal devices for hands-on use, not the destructive disposable-device CI scripts or instrumentation. Preserve personal app data. The old emulator AVDs were removed at the owner's request and must not be restored automatically.

## TV installation and testing

The same APK includes both MobileActivity and the TV launcher TvActivity. The current manifest requires Android API 28 or newer; Android TV/Google TV hardware compatibility still needs testing on the chosen model. Samsung Tizen and LG webOS TVs cannot install this Android APK directly. Identify the TV/box system before choosing the installation method.

For a supported Android TV/Google TV device, install the same local APK using its supported installer/file-transfer method or ADB. For Android 13+ TV devices exposing Wireless debugging, [Android's ADB guide](https://developer.android.com/tools/adb) describes pairing on the same Wi-Fi network. Pairing and connection use the separately displayed ports; do not assume a universal fixed port. Other TVs may expose only USB or older network debugging.

Once a TV is connected to ADB, replace `TV_SERIAL` with its entry from `adb devices -l`:

```powershell
& $iptvAdb -s TV_SERIAL install -r .\app\build\outputs\apk\debug\app-debug.apk
& $iptvAdb -s TV_SERIAL shell am start -n com.fireamun.amariptv/com.fireamun.iptv.prototype.TvActivity
```

With the real remote, test every destination, category movement, focus visibility, selection, Back from playback/episodes, login fields, source switching and light/Arabic mode. A successful phone run or cloud emulator run does not prove that TV's codec or remote compatibility. The current UI audit does not require another local emulator, host firewall changes, driver changes or a host reboot.
