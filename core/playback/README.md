# Playback

Android module: `:core:playback`. The current slice provides lifecycle-bound Media3 HLS/DASH/direct playback, native controls, safe status/errors and transport policy. It releases the player directly on stop; media service/session integration, track preferences and persistent resume remain later work.

Own Media3 playback, a single active player per device, playback coordination, media-session integration, track selection, and resume. Consume source resolution through injected interfaces. Touch, TV remote, and paired-phone actions use the same playback coordinator.
