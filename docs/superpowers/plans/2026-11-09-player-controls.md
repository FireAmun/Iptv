# Player controls implementation plan

> **For agentic workers:** Use subagent-driven-development with root-owned builds/Git and independent source/runtime review. User approved this milestone; do not repeat permission questions.

**Goal:** Select supplied audio/subtitles/quality and use phone PiP without changing playback/progress ownership.
**Architecture:** Existing Media3 session and Cinema surface; typed current-player controls plus a phone-only native PiP host. No data/schema/network/privacy changes.
**Tech stack:** Pinned Kotlin/Compose/Media3 1.11.1, Android PiP/native MediaSession, existing loopback instrumentation and generated owned fixtures.
**Spec:** [Approved scope/design](../specs/2026-11-09-player-controls-design.md).

## Global constraints

Single player/current request, current parsed/saved source contracts unchanged, no fabricated quality, supported current tracks only, no provider logs/telemetry additions. English/Arabic and touch/TV focus. Local AVD/AEHD/GVM/HAXM prohibited; cloud Linux devices only. Private research preserved and never staged. Work in place on codex/player-controls-pip under the RAM-conscious workspace ruling.

### Task1: Real track selection

Files: new core/playback PlayerTrackControls.kt and track-choice helper; playback strings/build dependency; app PlayerTracksTest.kt plus synthetic HLS assets.
Consumes current Player? internally; produces @Composable PlayerTrackControls(player:Player?,television:Boolean,modifier:Modifier=Modifier,onMinimize:(()->Unit)?=null). Audio/text/video Auto clears overrides of only its type and enables it; text Off disables it. Validate group still current before override. Media3 listeners rebuild current options/selection; release dismisses dialog. Scrollable Cinema dialog, radio semantics, initial focus once per window/dialog, return focus to originating button.

- [ ] Write real fixture track tests for selecting second audio/text/video and clearing Auto/Off; verify absent controls fail on baseline in cloud without weakening normal matrix.
- [ ] Implement helper/controls/resources, then integrate into same PlaybackSurface and run JVM/compile/lint.
- [ ] Assert actual selected formats/override/disabled state and rendered subtitles; no external streams. Independently review.

### Task2: Phone PiP and lifecycle

Files: core PlaybackPresentation.kt/LifecyclePlayerSession.kt/PlayerSurface.kt; app MobileActivity.kt/PhonePictureInPicture.kt/LibraryActivity.kt/AndroidManifest.xml; MobileLibrary.kt; PiP policy tests/PlayerPictureInPictureTest.kt.
Produces core PlaybackPresentation(canEnter,wantsToPlay,isPlaying,aspectRatio) and PlaybackCommands(play,pause,close). Append compatible Surface flags/callbacks after modifier. LibraryActivity default-off protected hooks: pictureInPictureActive, pictureInPictureSupported, requestPictureInPicture(), updatePlayerPresentation(PlaybackPresentation), updatePlayerCommands(PlaybackCommands?), updatePlayerBounds(Rect); closePlaybackFromPictureInPicture() calls library.closePlayback then concrete commands.close.

- [ ] Test policy and paused STOP/START intent before implementation; preserve onStop release and same player through visible PiP.
- [ ] Add phone-only manifest/config, manual/native auto/legacy entry, guarded params/valid ratio/source bounds, native session play/pause and no unsupported entry. No TV PiP.
- [ ] Hide all chrome without moving/recreating player, preserve fullscreen/pause intent, distinguish expansion from STOP/dismissal; close request/owner in required checkpoint order.
- [ ] Cloud real video tests verify same instance/advancing position/return/paused/close/final progress/denial/Home, modern path as well as API28. Independently review.

### Task3: Full verification and delivery

- [ ] Generate owned tiny multi-track HLS fixtures with existing ignored ffmpeg; commit only necessary synthetic test assets and fixture source recipe, no dependency installs.
- [ ] Run local Java21 :core:model:test :core:data:test testDebugUnitTest lintDebug assembleDebug :app:assembleDebugAndroidTest, repository15 tests/docs/actionlint/PowerShell syntax/whitespace. Record actual counts.
- [ ] Inspect synthetic screenshots and cloud runtime evidence; retain any failed RED/diagnostic runs honestly. No owner device automation.
- [ ] Update version/docs/progress, obtain whole-change review, stage only public files (exclude private tracker section/research), push/update one PR.
- [ ] One background watcher to complete latest-head CI; verify actual XML and every source/progress marker, exact-head merge, FF main, delete branches and independently verify main. Supply Windows-signed APK/current handoff. Do not claim codec/hardware/endurance coverage from synthetic tests.
