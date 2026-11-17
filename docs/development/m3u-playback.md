# M3U import and playback prototype

This P2 slice imports a user-selected file or M3U URL, displays safe entry labels/groups and plays a selected HTTP/HTTPS entry through Media3. Phone/tablet and TV use separate controls; native Media3 controls support touch and D-pad. It supplies no channels or subscriptions.

The original P2 implementation was session-only. Version 0.5.0 [saves one active source/root catalogue](saved-library.md), including file imports, and restores it after process death without an automatic network request. Failed/cancelled downloads preserve the previous library. [Xtream server login](xtream-login.md) is another implemented source route; EPG, parental controls, phone pairing and billing remain later work. Background playback is not implemented: stopping the activity detaches/releases the player; foregrounding prepares it again. Persistent VOD resume is deferred.

## Accepted input and provisional limits

- UTF-8 extended M3U with optional BOM, LF/CRLF, quoted attributes, labels/groups and HTTP/HTTPS entry URLs. Duplicate entries remain separate.
- HLS playback manifests are media inputs, not M3U catalogue imports. Import a catalogue pointing to an HLS URL instead.
- Version 0.10.0 raises the tested count guard to 300,000 entries while retaining 64 MiB decompressed input, with a 120-second parser budget. Line/field bounds remain 16,384/512 UTF-16 units. The earlier 10,000-entry/8 MiB/five-second prototype caps rejected ordinary large provider catalogues. Limits are resource guards, not a paid feature restriction or physical-device performance promise. See the [capacity measurements and limitations](catalogue-capacity.md).
- The URL loader has a 120-second call timeout, 15-second connect and 30-second read timeout. Default TLS trust is preserved; cross-protocol redirects are disabled.
- A recognized, unfiltered Xtream `get.php` account link returning 404/405 at the original endpoint retries the same server/prefix through the Player API. The link must contain unique username/password/type parameters (`m3u` or `m3u_plus`), optional `ts`/`m3u8` output and no other parameters, fragment or embedded HTTP credentials. Ordinary links, filtered exports, authentication rejection and redirected failures do not trigger that retry. Original URL storage/refresh and cached series navigation survive restart without changing source encoding.
- HTTP import/playback proceeds without repeated confirmation in v0.8.0, per the owner's later decision. HTTPS playback rejects plaintext or non-network subresources; an HTTPS master cannot silently load HTTP segments/keys.
- M3U URLs start hidden. Use **Show / Hide** beside the URL label to inspect or edit a pasted link on phone or TV; TV remotes move Up from the field to this control and Down to return. The keyboard keeps password input mode to avoid suggestions while revealing. Closing or submitting clears the draft, and reopening starts hidden. Actual server passwords remain masked. Source URLs are never fallback labels, error text or telemetry fields; saved connections use [protected local storage](saved-library.md). Media3 logging stays disabled because nested exceptions can contain sensitive addresses.

Use [the build instructions](first-build.md). Relevant checks:

```powershell
./gradlew.bat --no-daemon --console=plain :core:model:test :core:data:test testDebugUnitTest lintDebug assembleDebug :app:assembleDebugAndroidTest
./gradlew.bat --no-daemon --console=plain :app:connectedDebugAndroidTest
```

## Hands-on synthetic media

The owner re-authorized local manual app testing on 24 October 2026. Use one emulator at a time and do not change host firewall rules, virtualization drivers or reboot the laptop. The earlier unexpected host shutdown remains unexplained.

The test APK contains a separate instrumentation mode that serves only the [self-generated clip](../../app/src/androidTest/assets/README.md) and a tiny catalogue on **emulator loopback**. It starts no Windows HTTP listener and introduces no production debug shortcut. Normal CI mode runs AndroidJUnitRunner's tests.

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e manualFixture true com.fireamun.amariptv.test/com.fireamun.iptv.prototype.ManualFixtureRunner
```

While the runner remains active, use another shell to launch `.MobileActivity` or `.TvActivity` and enter `http://127.0.0.1:8765/playlist.m3u`. Exercise the real URL field, HTTP confirmation, list, player and Back controls. A test M3U file pushed to Downloads can use `http://127.0.0.1:8765/clip.mp4` to exercise the real system picker. The `/missing` path returns HTTP 404 for failure/retry checks. End the fixture host with:

```powershell
adb shell run-as com.fireamun.amariptv touch cache/manual-qa-stop
```

It also expires after ten minutes. The fixture provider and runner exist in the instrumentation APK only. Gradle's connected-test runner uninstalls tested APKs afterward; reinstall before independent manual use.

## Verification

Local API 34 phone and API 36 TV instrumentation each passed all eight tests after regression fixes: both language journeys, URL import/playback on both UI modes, granted-content-URI file import and cancellation during actual provider opening, and approved/blocked transport checks. Playback tests include STARTED → CREATED → RESUMED and TV Back/focus restoration. Nineteen JVM tests cover parser/transport/state boundaries, including quote-aware groups, final-EOF time budget, incompatible URL validation and cancellation of interruption-insensitive file reads. See [progress](../../progress.md) and [PR #3](https://github.com/FireAmun/Iptv/pull/3) for cloud results and delivery state.

Hands-on API 34 phone checks used the actual Downloads document picker and a two-entry synthetic M3U. Observed video frames, paused with native controls, advanced playback position, denied/accepted HTTP consent, retried a deliberately missing stream, and returned to the retained library. Status-bar contrast was corrected after visual inspection. Only synthetic data was used; no provider credentials or real subscriptions were tested.

On the actual API 36 TV emulator, D-pad activation switched English → Arabic → English; the Arabic layout and focus appearance were visually inspected. Manual TV file-picker and playback checks were not completed. The URL/playback journey is covered by device instrumentation; hands-on TV playback is not claimed.

Physical TV boxes, large-library interaction/peak-memory profiling, endurance playback, codec gaps and persistent resume remain unverified. A TV without a document picker can use URL import. Provider-import investigation is recorded in the current tracker without private source details.
