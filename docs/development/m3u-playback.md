# M3U import and playback prototype

This P2 slice imports a user-selected file or M3U URL, displays safe entry labels/groups and plays a selected HTTP/HTTPS entry through Media3. Phone/tablet and TV use separate controls; native Media3 controls support touch and D-pad. It supplies no channels or subscriptions.

The library exists for the current process/session. Activity/language recreation retains it through the ViewModel; process death requires importing again. Failed/cancelled imports preserve the previous library. Database/provider credential persistence, EPG, Xtream, parental controls, phone pairing and billing remain later work. Background playback is not implemented: stopping the activity detaches/releases the player; foregrounding prepares it again. Persistent VOD resume is deferred.

## Accepted input and provisional limits

- UTF-8 extended M3U with optional BOM, LF/CRLF, quoted attributes, labels/groups and HTTP/HTTPS entry URLs. Duplicate entries remain separate.
- HLS playback manifests are media inputs, not M3U catalogue imports. Import a catalogue pointing to an HLS URL instead.
- 8 MiB decompressed bytes; 16,384 UTF-16 code units per line; 512 per title/group; 10,000 entries; five-second parser budget checked during reads and before commit. These are prototype protection limits, not measured device-performance promises.
- The URL loader has a 20-second call timeout and eight-second connection/read timeouts. Default TLS trust is preserved; cross-protocol redirects are disabled.
- Confirm each HTTP import/playback operation. HTTPS playback rejects plaintext or non-network subresources; an HTTPS master cannot silently load HTTP segments/keys.
- URLs are masked during entry, cleared on submission and never used as fallback labels or error text. There is no provider logging, analytics, credential persistence or network logging interceptor. Media3 logging is disabled because nested exceptions can contain sensitive addresses.

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
adb shell am instrument -w -e manualFixture true com.fireamun.iptv.prototype.test/com.fireamun.iptv.prototype.ManualFixtureRunner
```

While the runner remains active, use another shell to launch `.MobileActivity` or `.TvActivity` and enter `http://127.0.0.1:8765/playlist.m3u`. Exercise the real URL field, HTTP confirmation, list, player and Back controls. A test M3U file pushed to Downloads can use `http://127.0.0.1:8765/clip.mp4` to exercise the real system picker. The `/missing` path returns HTTP 404 for failure/retry checks. End the fixture host with:

```powershell
adb shell run-as com.fireamun.iptv.prototype touch cache/manual-qa-stop
```

It also expires after ten minutes. The fixture provider and runner exist in the instrumentation APK only. Gradle's connected-test runner uninstalls tested APKs afterward; reinstall before independent manual use.

## Verification

Local API 34 instrumentation passed all eight tests after regression fixes: both language journeys, URL import/playback on both UI modes, granted-content-URI file import and cancellation during actual provider opening, and approved/blocked transport checks. Playback tests include STARTED → CREATED → RESUMED and TV Back/focus restoration. Nineteen JVM tests cover parser/transport/state boundaries, including quote-aware groups, final-EOF time budget, incompatible URL validation and cancellation of interruption-insensitive file reads. See [progress](../../progress.md) and the delivery PR for cloud results.

Hands-on API 34 phone checks used the actual Downloads document picker and a two-entry synthetic M3U. Observed video frames, paused with native controls, advanced playback position, denied/accepted HTTP consent, retried a deliberately missing stream, and returned to the retained library. Status-bar contrast was corrected after visual inspection. Only synthetic data was used; no provider credentials or real subscriptions were tested.

Physical TV boxes, private providers, large-library profiling, endurance playback, codec gaps and persistent resume remain unverified. A TV without a document picker can use URL import.
