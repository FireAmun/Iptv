# Player tracks and phone PiP

Candidate0.11.0 / versionCode13. Implementation, test-APK and source reviews are prepared; final live delivery state is [PR #21](https://github.com/FireAmun/Iptv/pull/21) and [progress](../../progress.md), not this document alone.

## Tracks

Audio, Subtitles and Quality are shared phone/TV controls beneath the video. They use the current Media3 player and supported current stream tracks. Pick a row to apply immediately; Close/Back cancels without changing selection. Auto clears only that type's override; subtitle Off disables text. English/Arabic, explicit radio state, scrollable choices and TV focus restoration are included.

Quality choices display actual supplied dimensions/known bitrates. Auto allows Media3 adaptation. A single-quality stream says so; missing captions are not invented. Unsupported codecs/tracks cannot be selected. This feature does not transcode, discover alternate provider URLs or promise4K. Audio/subtitle language labels use Media3's localized names with bounded safe fallbacks. Group changes/released players dismiss old menus; queued old actions are rejected against the latest group set.

Selections and pause intent are retained across a real STOP/START for the current request. They are not saved as account/global language preferences or reused for a different stream/retry. Existing30-second native seeking and saved movie/episode progress remain in place.

## Phone picture-in-picture

On a phone/image with PiP available and allowed, Minimize opens native PiP while playing or paused. Going Home while video intends to play uses the legacy leave callback on API28–30 and system auto-entry on API31+. Paused, ended/failed, browser and TV paths do not auto-enter. A blocked feature/AppOp hides the action and is handled without a crash. No background audio service or extra player/stream is added.

The same video view/player remains mounted; title/back/status/retry/track chrome and native controller are hidden in PiP. System play/pause acts on that same player through a native media session. Returning fullscreen preserves pause intent and current position. Closing PiP releases playback, closes its request and flushes accepted progress before retirement, even if lifecycle collection has stopped. API29+ can deliver a final pre-stop checkpoint before the Activity close hook; FIFO checkpoint-before-disposal remains the invariant.

The manifest change is phone-only. PiP layout/configuration handling does not consume language or appearance configuration changes. Source bounds and a platform-valid video aspect ratio support transitions. Manual entry hides chrome before entry; API35+ transition callbacks hide it early for system entry. API31–34 automatic-transition animation has not been independently tested; its actual mode callback still hides controls. Physical OEM/gesture/permission behavior remains an owner-device follow-up.

## Validation

Local Java21 JVM/lint/app/test-APK checks and independent source review precede delivery. Owned loopback HLS fixtures supply two H264 qualities (180/360), en/ar AAC tones and English WebVTT; original owned MP4 proves a single-quality/no-caption source. See the [fixture recipe](../../scripts/fixtures/generate_player_tracks.py) and [fixture description](../../app/src/androidTest/assets/player-tracks/README.md).

The normal cloud matrix covers phoneAPI28, modern phoneAPI36 and TVAPI36. Native suites assert actual Media3 override/selected/decoded formats, rendered subtitle pixels and removal, type isolation, pause/position/owner identity, stale same-player and released-player actions, new Arabic labels and physical control bounds outside system bars. PiP tests use actual system modes, media-session commands, Home/return/Close, pause intent, native app-op denial/restoration and final saved progress. Unsupported images assert no action instead of skipping; actual phone entry markers and matching XML must establish both legacy/modern coverage.

All native fixtures run only on disposable cloud Ubuntu/KVM devices. Local AVD/AEHD/GVM/HAXM and personal-device fixture restrictions remain in AGENTS. Baseline RED failures are intentional test-oracle evidence and are not merge candidates; only latest-head full GREEN results/probes permit merge. Exact counts/results belong in the tracker.

## References

[Media3 track selection](https://developer.android.com/media/media3/exoplayer/track-selection), [PiP setup/config](https://developer.android.com/develop/ui/compose/system/pip-setup), [PiP entry timing](https://developer.android.com/develop/ui/compose/system/pip-enter), [PiP lifecycle/media controls](https://developer.android.com/develop/ui/views/picture-in-picture). Existing pins retained; no new runtime media engine/service.

## Focused cloud diagnosis

CI workflow_dispatch supports full (default), player (track/PiP/checkpoint classes), pip, tracks or forms. These are allowlisted diagnostics for disposable cloud devices. Pull requests and main pushes always use the complete109-case suite on all three profiles; the script rejects any non-full scope outside workflow_dispatch. Every successful diagnostic scope still runs source/progress process probes. Diagnostic runs do not satisfy merge gates: fetch the pull_request run at the exact head and require full XML/probes and separate main verification. The35-minute device job bound allows modern-image setup and the expanded suite; individual assertions/watchdog limits are unchanged.

The complete player scope has25 cases (12 tracks,6 PiP,7 checkpoint). The script transports its fixed class lists with semicolons because pinned AGP9.4 drops comma-separated suffixes; the test runner validates and decodes the list before AndroidJUnitRunner. Normal full runs emit no filter.

Actions-only capture in [device-diagnostics.sh](../../scripts/device-diagnostics.sh) retains bounded system logs, window/input/last-ANR and resource snapshots around the suite; failed runs also request a bounded bugreport. Collection preserves the original test result, stops its owned log stream and never dismisses an ANR, changes settings or requests guest root. These are synthetic disposable devices, never personal sources/devices. Diagnostic collection establishes evidence; it is not a repair for a system ANR or a replacement for complete passing test results.
