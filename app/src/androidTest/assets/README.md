# Playback test fixture

`synthetic-test.mp4` is a self-generated 30-second 320×180 H.264 Baseline/AAC clip. Its video comes from FFmpeg's `testsrc2` generator and audio from a 440 Hz `sine` generator; it contains no third-party programme footage, channels or provider data. Generated on 24 October 2026 using FFmpeg 7.1 from the project-local imageio-ffmpeg 0.6.0 tool wheel. The duration allows hands-on pause/seek checks without racing a six-second ending.

Reproduce with FFmpeg:

```sh
ffmpeg -f lavfi -i testsrc2=size=320x180:rate=24 -f lavfi -i sine=frequency=440:sample_rate=44100 -t 30 -c:v libx264 -profile:v baseline -pix_fmt yuv420p -c:a aac -movflags +faststart -threads 1 synthetic-test.mp4
```

This asset is in the instrumentation APK only. Tests serve it on loopback using MockWebServer; production does not supply content or a test-source shortcut.
