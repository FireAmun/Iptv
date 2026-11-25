# Phone picture-in-picture

Version 1 requirement D25 (17 November 2026): a PiP button in the phone player, and automatic PiP when the user goes Home while video plays. Ported on 21 November 2026 from the closed, unmerged PR #21 branch `codex/player-controls-pip` onto current main. Only the PiP parts were taken; the track menus and CI work from that branch had already landed separately.

## Behaviour

- **Phones only.** `MobileActivity` declares `supportsPictureInPicture`; `TvActivity` never offers or enters PiP.
- **Minimize button.** Shown in the player's control row (English "Minimize", Arabic "تصغير") when the device has the PiP feature, the PiP app-op is allowed and the current stream has a supported video track that is ready or buffering. Works while playing or paused.
- **Home while playing.** Android 12+ uses system auto-enter (`setAutoEnterEnabled`); Android 9–11 enter from `onUserLeaveHint`. Paused, ended or failed playback does not auto-enter.
- **Inside PiP.** The same `PlayerView` and Media3 player stay attached; only the video shows. Title, status, retry, track menus, the native controller and resume dialogs are hidden. System play/pause reaches that player through a `MediaSession`.
- **Back to full screen** keeps the play/pause state and position, and the controls return.
- **Closing PiP** stops playback, closes the request and saves the final position, even when the system releases the player before the Activity's own stop callback.
- **Screen off while in PiP** releases the player like any stop but keeps the request; after unlocking, the same PiP window plays again.
- **Pause intent** now survives a real STOP/START of the same request (for example a paused Home), like the track choices already did.
- **Blocked or missing PiP** (feature absent or app-op denied) hides the button and never crashes.
- The manifest handles size and orientation changes in `MobileActivity` itself, so PiP transitions and rotation do not recreate the Activity. Language and appearance changes still recreate it as before.
- No background audio service, extra player or extra stream is added. Nothing new is sent off the device.

## Code

`app/.../PhonePictureInPicture.kt` (entry, params, aspect ratio, source bounds, media session, close on dismissal), `MobileActivity.kt` (lifecycle and PiP callbacks), `LibraryActivity.kt` (hooks, hidden chrome, close ownership), `core/playback/PlaybackPresentation.kt`, `LifecyclePlayerSession.kt` (presentation, commands, hidden controls, pause intent), `PlayerSurface.kt`, `PlayerTrackControls.kt` (Minimize button).

## Tests

`PlayerPictureInPictureTest` adds seven native cases (completeness gate 110 → 117): manual PiP keeps one advancing player and full screen restores the chrome; paused PiP with native media-session play/pause keeps intent; Home while playing enters PiP and the system Close saves progress without reopening playback; paused Home does not enter PiP and keeps pause and position; screen off and on in PiP keeps the request and plays again; a denied app-op hides Minimize; the TV Activity never offers PiP. Images without the PiP feature assert the hidden button instead of skipping.

Physical-phone checks (gesture navigation, OEM PiP menus) remain owner-device follow-ups.
