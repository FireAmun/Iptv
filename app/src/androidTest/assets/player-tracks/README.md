# Owned audio/subtitle fixture

Synthetic FFmpeg test pattern and two sine tones (English/Arabic labels), plus authored WebVTT. No provider media, URLs or credentials. Extracted from the retained recovery source, with the second video rendition removed: this slice has one 320x180 H264 video rendition, AAC mono audio and one English caption track. The existing `synthetic-test.mp4` exercises missing subtitles.

Thirty-two seconds; eight-second video segments. The caption covers the full clip so selection and rendered-pixel/Off checks do not race cue boundaries. Served only through loopback MockWebServer. No fixture is bundled in the production APK.
