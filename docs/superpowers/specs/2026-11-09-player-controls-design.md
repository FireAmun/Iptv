# Player tracks and phone picture-in-picture

Owner approved audio/subtitle selection, real quality options and phone PiP on9 November, after the proposed next player milestone. Existing Cinema player and one Media3 instance remain the foundation. The owner reports phone tests OK; scope of that manual pass is not a formal300k/codec/endurance matrix.

## Behavior

- Shared phone/TV player has Audio, Subtitles and Quality controls in the current black/gold design, with accessible EN/AR labels and TV focusable rows. Choices come only from current Media3 supported tracks. Audio/quality Auto clears overrides of only that type; subtitle Auto restores defaults and Off disables text. Show a single supplied quality honestly; no synthetic HD/4K or transcoding. Selection changes the current player without restarting playback or changing saved positions. Stale player/groups cannot receive old dialog actions. Preferences last for the current request/lifecycle restart only; no database/schema or source changes.
- Phone PiP provides a minimize action when supported and allowed. Manual entry preserves play/pause intent. Leaving actively playing video uses auto-entry on API31+ and user-leave entry on28-30. Browser, paused Home, failed/ended media and TV never auto-enter. Native play/pause controls use a media session bound to the same player commands. No background playback service or second stream.
- One PlaybackSurface remains at its existing composition key/call site during PiP transitions. Hide native controller, title/status/back/retry/options and overlays during entering/active PiP. Return to fullscreen changes presentation only. Source rectangle and valid video ratio improve transitions; permission/feature denial is handled without crashing or leaking URLs.
- Visible PiP pauses Activity but remains STARTED. Retain existing onStop release, never release onPause and never skip release merely because a PiP flag remains true. Preserve user playback intent and track parameters across real STOP/START. PiP dismissal closes the ViewModel request and directly closes its concrete player owner even after Media3 release, preserving accepted-checkpoint -> disposed FIFO ordering when lifecycle collection stops. API29+ pre-stop may report the final checkpoint before the Activity closing hook. Fullscreen expansion must not close playback.

## Interfaces and ownership

core:playback adds PlaybackPresentation(canEnter:Boolean,wantsToPlay:Boolean,isPlaying:Boolean,aspectRatio:Float) and PlaybackCommands(play,pause,close). PlaybackSurface retains existing positional arguments/default behavior and appends optional television, inPictureInPicture, onMinimize, onPresentation, onCommands and onVideoBounds callbacks. LifecyclePlayerSession publishes the player internally for track controls and exposes safe callbacks; no Media3 object crosses into the app ViewModel/data layer.

LibraryActivity has default-off protected hooks for phone PiP state/support/request/presentation/commands/bounds plus a protected closePlaybackFromPictureInPicture that marks request closing before invoking concrete close. MobileActivity owns native PiP params/state/media-session and dismissal ordering; TvActivity unchanged. MobilePlayback hides chrome via a flag without moving its player call site.

## Verification and constraints

Existing JVM progress/ordering regressions plus real native policy/format and loopback HLS instrumentation: two H264 qualities, two generated AAC language tracks and WebVTT cues; assert actual Media3 selections/disabled flags, single-quality/no-caption behavior, changing groups, EN/AR and touch/D-pad cancellation/focus. Test actual native PiP entry/return/close/Home/paused/denial, same-player identity, advancing playback and progress-save ordering. Existing Continue Watching regressions remain required. Use cloud phoneAPI28+TVAPI36 and a modern disposable phoneAPI36 job if needed for actual auto-entry; unsupported image behavior must be stated, not silently skipped.

No local AVD launch/recreation, AEHD/GVM/HAXM, personal-device fixtures or host changes. Local Java21 JVM/lint/APKs and repository checks remain allowed. Preserve private owner research outside Git. Normal reviewed exact-head PR/full-cloud/main delivery and one idle completion watcher apply.

## References

[Track selection](https://developer.android.com/media/media3/exoplayer/track-selection), [PiP setup](https://developer.android.com/develop/ui/compose/system/pip-setup), [PiP timing](https://developer.android.com/develop/ui/compose/system/pip-enter), [PiP lifecycle](https://developer.android.com/develop/ui/views/picture-in-picture). Current official documentation checked9 November; pinned Media3 remains1.11.1.
