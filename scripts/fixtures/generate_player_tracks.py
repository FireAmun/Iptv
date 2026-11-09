"""Rebuild owned instrumentation media; no downloads or external programme material.

Run from any directory with Python 3. Override --ffmpeg for an existing Linux binary.
The default is the project's ignored FFmpeg 7.1 Windows fixture tool. Each encoder
uses one thread. Output is deterministic for a given FFmpeg build.
"""

import argparse
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "app/src/androidTest/assets/player-tracks"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ffmpeg", type=Path, default=ROOT / ".tools/fixture-tools/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe")
    args = parser.parse_args()
    OUTPUT.mkdir(parents=True, exist_ok=True)

    def encode(name, source, options):
        subprocess.run([
            str(args.ffmpeg), "-hide_banner", "-loglevel", "error", "-y",
            "-filter_threads", "1", "-f", "lavfi", "-i", source, "-t", "32",
            *options, "-threads", "1", "-fflags", "+bitexact", "-flags", "+bitexact",
            "-f", "hls", "-hls_time", "8", "-hls_list_size", "0",
            "-hls_playlist_type", "vod", "-hls_flags", "independent_segments",
            "-hls_segment_filename", str(OUTPUT / f"{name}-%02d.ts"),
            str(OUTPUT / f"{name}.m3u8"),
        ], check=True)

    for name, size, bitrate in [("video-180", "320x180", "24k"), ("video-360", "640x360", "48k")]:
        encode(name, f"testsrc2=size={size}:rate=5", [
            "-an", "-c:v", "libx264", "-profile:v", "baseline", "-level:v", "3.0", "-pix_fmt", "yuv420p",
            "-preset", "veryfast", "-b:v", bitrate, "-maxrate", bitrate, "-bufsize", "96k",
            "-g", "40", "-keyint_min", "40", "-sc_threshold", "0",
        ])
    for language, frequency in [("en", 440), ("ar", 660)]:
        encode(f"audio-{language}", f"sine=frequency={frequency}:sample_rate=44100", [
            "-vn", "-c:a", "aac", "-b:a", "24k", "-ac", "1",
            "-metadata:s:a:0", f"language={language}",
        ])

    (OUTPUT / "master.m3u8").write_text('''#EXTM3U
#EXT-X-VERSION:3
#EXT-X-INDEPENDENT-SEGMENTS
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English tone",LANGUAGE="en",DEFAULT=YES,AUTOSELECT=YES,URI="audio-en.m3u8"
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Arabic tone",LANGUAGE="ar",DEFAULT=NO,AUTOSELECT=YES,URI="audio-ar.m3u8"
#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="text",NAME="English captions",LANGUAGE="en",DEFAULT=NO,AUTOSELECT=NO,FORCED=NO,URI="subtitles.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=72000,AVERAGE-BANDWIDTH=48000,RESOLUTION=320x180,FRAME-RATE=5.000,CODECS="avc1.42c01e,mp4a.40.2",AUDIO="audio",SUBTITLES="text"
video-180.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=96000,AVERAGE-BANDWIDTH=72000,RESOLUTION=640x360,FRAME-RATE=5.000,CODECS="avc1.42c01e,mp4a.40.2",AUDIO="audio",SUBTITLES="text"
video-360.m3u8
''', encoding="utf-8", newline="\n")
    (OUTPUT / "subtitles.m3u8").write_text('''#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:32
#EXT-X-MEDIA-SEQUENCE:0
#EXT-X-PLAYLIST-TYPE:VOD
#EXTINF:32.000,
subtitles.vtt
#EXT-X-ENDLIST
''', encoding="utf-8", newline="\n")
    (OUTPUT / "subtitles.vtt").write_text('''WEBVTT
X-TIMESTAMP-MAP=LOCAL:00:00:00.000,MPEGTS:126000

00:00:00.000 --> 00:02:00.000
Synthetic English subtitle
''', encoding="utf-8", newline="\n")
    print(f"Generated {sum(p.stat().st_size for p in OUTPUT.iterdir() if p.is_file())} bytes in {OUTPUT}")


if __name__ == "__main__":
    main()
