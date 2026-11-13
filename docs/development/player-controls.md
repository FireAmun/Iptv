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

The complete player scope has25 cases (12 tracks,6 PiP,7 checkpoint). The script sends only a plain diagnosticScope enum; the test runner validates that enum and selects a fixed class list before AndroidJUnitRunner. This avoids both pinned AGP9.4's comma splitting and native shell separator interpretation. Normal full runs emit no filter. Before process probes, [the XML gate](../../scripts/check_device_results.py) requires complete counts, unique cases, successful summaries and exact diagnostic classes; zero, partial, failed or skipped results cannot inherit Gradle's success.

Actions-only capture in [device-diagnostics.sh](../../scripts/device-diagnostics.sh) retains bounded system logs, window/input/last-ANR and resource snapshots around the suite; failed runs also request a bounded bugreport. Collection preserves the original test result, stops its owned log stream and never dismisses an ANR, changes settings or requests guest root. These are synthetic disposable devices, never personal sources/devices. Diagnostic collection establishes evidence; it is not a repair for a system ANR or a replacement for complete passing test results.

Device jobs compile app/test APKs before starting the guest, then reuse that output tree for connected tests. This separates heavy compilation from the modern image's busy first boot on the two-CPU runner. The same images, resources, assertions and full-suite gates remain; only subsequent native results can establish whether this sequencing resolves the observed boot ANR.

The modern phone uses a 720x1280 framebuffer at280dpi, preserving the previous Pixel2 logical layout (~411x731dp) while reducing the number of pixels rendered in software. The [Linux Actions prelaunch helper](../../scripts/prepare-cloud-device.py) changes only the expected disposable AVD density and records bounded bootstrap logs before the runner unlocks the guest. API28 phone and TV display configurations, all images and full test counts stay the same. Confirm actual guest dimensions/density and test outcomes in the cloud before treating this experiment as a repair.

Android16 additionally uses a [cloud-only prepared boot](../../scripts/prepare-cloud-boot.py): wait for initial SystemUI data setup, reboot that disposable guest exactly once, then require a changed boot ID, responsive SystemUI, healthy launcher and no second-boot ANR before APK installation. First-boot evidence is retained; tests run once on the prepared OS. Preparation fails closed rather than dismissing errors or retrying tests. It does not reboot the laptop, launch a local AVD or use AEHD/GVM/HAXM, and leaves OS ANR thresholds unchanged. This initialization experiment still requires actual cloud verification.

The initial prepared-boot run rejected a second-boot ANR before installing the app. It did not establish a repair. Preparation failures now retain read-only event/logcat, last-ANR, window and resource snapshots under `cloud-boot-failure/`, with a shared30-second budget, at most5 seconds per command and256KiB per file. Diagnostic failure cannot turn a failed preparation into success or replace its original reason; no extra unlock/reboot or test retry is allowed.

Source forms keep one scroll container attached while the native keyboard resizes the dialog. This avoids removing an active bring-into-view node when crossing the previous height threshold. Titles, fields and actions scroll together, including at normal heights; native touch tests scroll actions into view and still require displayed controls before activation. Data callbacks and draft limits are unchanged. Complete native regression results remain required.

The modern cloud profile now evaluates the full AOSP Android16 (`default`, x86_64) phone image. Only its image target changes; API36, display, resources, prepared-boot safety gate,109 cases and native PiP/process proofs remain mandatory. This app currently requires no Google Play services library. The Google APIs image repeatedly failed OS startup before APK installation, including after its preparation reboot; available memory and compositor CPU comparisons did not establish a unique cause. The image change is a controlled compatibility experiment, not proof that Google services caused the timeout. This is a full phone image, not a reduced automated-test image. Google/OEM physical-device validation and future Play Billing testing remain separate release requirements. See [Android's system image guidance](https://developer.android.com/studio/run/managing-avds#system-images).
