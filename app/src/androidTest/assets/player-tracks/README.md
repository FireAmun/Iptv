# Owned audio/subtitle fixture

Synthetic FFmpeg test pattern and two sine tones (English/Arabic labels), plus authored WebVTT. No provider media, URLs or credentials. Extracted from the retained recovery source at `ddd9ce1b735194e24bf0753bd0fdedef20804d1d`. The original `master.m3u8` keeps one320x180 H264 video rendition, AAC mono audio and one English caption track. The opt-in `quality-master.m3u8` adds the owned640x360 rendition for quality tests; original audio/subtitle fixtures stay unchanged. The existing `synthetic-test.mp4` exercises missing subtitles and single-quality playback.

Thirty-two seconds; eight-second video segments. The caption covers the full clip so selection and rendered-pixel/Off checks do not race cue boundaries. Served only through loopback MockWebServer. No fixture is bundled in the production APK.
