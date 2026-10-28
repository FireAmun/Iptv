# Try Amar Iptv on a personal phone or TV

28 October 2026. The local Cinema UI preview is based on v0.8.0/code 10 (merged PR #12) with the approved presentation changes applied. The owner accepted this preview and authorized GitHub delivery on 28 October; use progress.md for the current delivery/CI state. Phone selected by the owner: Samsung S25 Ultra. TV model/system is still undecided.

## Easiest S25 Ultra installation: copy the APK

1. On this workstation, open `D:\Side_p\iptv\.tools\releases\Amar-Iptv-UI-preview-2026-10-28.apk` in File Explorer.
2. Connect the phone by USB, choose file transfer, and copy that APK to the phone's Downloads folder. No Android Studio or developer options are needed for this method.
3. Open **My Files → Downloads → Amar-Iptv-UI-preview-2026-10-28.apk** on the phone. Allow My Files to install unknown apps if prompted, then install.
4. If Samsung Auto Blocker prevents the local APK installation, temporarily turn it off in **Settings → Security and privacy → Auto Blocker**, install this build, then restore the setting and My Files' install permission. Samsung's menu names can vary by One UI version; see the [official Samsung guide](https://www.samsung.com/uk/support/mobile-devices/protect-your-galaxy-device-with-the-new-auto-blocker-feature/).
5. Open **Amar Iptv** and add your source once in Settings. Each installation has its own protected local cache; it does not copy or sync the emulator's saved account.

The dated preview APK is 45,151,755 bytes (about 45 MB). SHA-256 at handoff: `CFC5E00E729AA3CEA0CFFA47638D70E02E8C61F06A1D78B529E4E947755BACB6`. It is a debug build, not a Play release. Keep using the workstation's local build for updates so the signing identity stays consistent. A CI debug build can have a different signing key.

## Optional: install/update from the VS Code terminal

Enable Developer options and USB debugging on the phone, connect by USB and accept that computer's debugging prompt on the phone. No Android Studio is required. Android documents the [USB device setup](https://developer.android.com/studio/run/device) and [ADB install options](https://developer.android.com/tools/adb).

```powershell
Set-Location D:\Side_p\iptv
$iptvAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $iptvAdb devices -l
& $iptvAdb -d install -r .\app\build\outputs\apk\debug\app-debug.apk
& $iptvAdb -d shell am start -n com.fireamun.iptv.prototype/.MobileActivity
```

`-d` selects the connected physical Android device rather than the emulator. Use this shortcut only with one physical Android device attached; use `-s SERIAL` for an explicitly selected device otherwise. `install -r` updates without intentionally clearing the existing app data. No rebuild is needed to try this existing APK.

## Testing checklist on the phone

- Add one source in Settings, browse Live/Movies/Series, use top Search and toggle a favourite. Settings is the last bottom destination.
- Open the category grid button beside the existing chips, search for a category, select it, then reopen and cancel; the chip selection should remain correct.
- Inspect login, URL, playlist manager, rename/removal and light/Arabic screens against the approved Canva direction.
- Close and reopen the app: source and favourites should restore from the saved cache without another provider download. Refresh stays manual in Settings.
- Try one authorized live stream and one movie/episode, pause, use the existing 30-second seek controls and return to browsing.
- Switch English/Arabic and dark/light; check the keyboard, rotation and larger text size on the actual phone.
- Persistent movie/episode playback positions and 300k support are planned, not present in v0.8.0. Do not report their absence as a regression in this build.

Use personal devices for hands-on use, not the destructive disposable-device CI scripts or instrumentation. Keep the emulator/My IPTV cache intact.

## TV installation and testing

The same APK includes both MobileActivity and the TV launcher TvActivity. The current manifest requires Android API 28 or newer; Android TV/Google TV hardware compatibility still needs testing on the chosen model. Samsung Tizen and LG webOS TVs cannot install this Android APK directly. Identify the TV/box system before choosing the installation method.

For a supported Android TV/Google TV device, install the same local APK using its supported installer/file-transfer method or ADB. For Android 13+ TV devices exposing Wireless debugging, [Android's ADB guide](https://developer.android.com/tools/adb) describes pairing on the same Wi-Fi network. Pairing and connection use the separately displayed ports; do not assume a universal fixed port. Other TVs may expose only USB or older network debugging.

Once a TV is connected to ADB, replace `TV_SERIAL` with its entry from `adb devices -l`:

```powershell
& $iptvAdb -s TV_SERIAL install -r .\app\build\outputs\apk\debug\app-debug.apk
& $iptvAdb -s TV_SERIAL shell am start -n com.fireamun.iptv.prototype/.TvActivity
```

With the real remote, test every destination, category movement, focus visibility, selection, Back from playback/episodes, login fields, source switching and light/Arabic mode. A successful phone run or cloud emulator run does not prove that TV's codec or remote compatibility. The current UI audit does not require another local emulator, host firewall changes, driver changes or a host reboot.
