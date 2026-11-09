# Owned multitrack playback fixture

Generated solely from FFmpeg `testsrc2` and 440/660 Hz sine sources. No programme
footage, provider URLs, credentials or third-party media. Instrumentation APK only.

`master.m3u8` offers two video-only H264 Baseline renditions (320×180 and 640×360,
5 fps, 32 seconds), an alternative-audio group containing English/Arabic mono AAC
tones, and a subtitle group containing a nondefault English WebVTT rendition.
The subtitle cue runs from zero to 120 seconds so selection at any test position
renders the same owned text. All media/playlist links are relative. Tests serve
them on loopback through MockWebServer with content types and byte range support.

Rebuild with `python scripts/fixtures/generate_player_tracks.py` from the repository
root. It uses the existing ignored FFmpeg 7.1 Windows binary, one encoder/filter
thread per invocation, and performs no downloads. On another host pass an existing
binary with `--ffmpeg /absolute/path/to/ffmpeg`. Generated media totals about 570 KiB.

`PlayerTracksTest` opens real phone/TV activities and inspects their actual attached
Media3 player. Caption checks render the live native SubtitleView to a transparent
bitmap; they verify visible cue pixels and their removal after Off. The existing
`synthetic-test.mp4` supplies the single-quality/no-caption regression case.
