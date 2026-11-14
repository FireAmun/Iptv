# Audio and subtitle selection

Owner-authorized slice, 14 November 2026. Starts from verified main `b9130236bbddfa2b8cd3172cf0cbbd0e3f028c30`.

## Design and limits

Add two Cinema menus under the existing Media3 surface on phone and TV. Audio offers Auto and supported source tracks. Subtitles offers Auto, Off and supported source tracks. Show selected state and an explicit empty message; never invent tracks. Use existing English/Arabic resources, light/dark tokens and visible TV focus with Back restoring the opener.

Selection changes only the requested track type on the current player: no media replacement, seek, play/pause change or video override. Reject actions from a retired player or obsolete track groups. Keep choices through STOP/START of the same in-memory request; a new request starts with native defaults. No saved global language preference.

Keep the existing player owner, resume/checkpoint behavior, library and ViewModels. No quality controls, PiP, media-session work, driver/emulator setup, dependency upgrades or CI infrastructure changes. Reuse only the owned single-resolution HLS media from the retained recovery source.

## Implementation and verification

- [x] Add focused native tests first: real audio selection/Auto, rendered captions/Off/Auto, absent tracks and Arabic labels, stale same-player actions and real STOP/START.
- [x] Add the two menus in `core/playback/PlayerTrackControls.kt`, wire the active player through `PlayerSurface.kt` and `LifecyclePlayerSession.kt`; pass the TV flag from `LibraryActivity.kt`.
- [x] Compile native tests and run JVM tests, lint, APK builds and repository checks locally. No local device process or prohibited driver.
- [ ] Obtain independent review; open one PR. Retain all 91 baseline cases and require complete phone/API28 and TV/API36 native results plus persistence probes.
- [ ] Merge only the reviewed exact green head, sync main, remove the merged branch and verify the main run separately.

Architecture and product context: [architecture](../superpowers/specs/2026-10-24-iptv-architecture-design.md), [brief](../../codex/docs/iptv-product-brief.md). Delivery evidence lives in [progress](../../progress.md).
