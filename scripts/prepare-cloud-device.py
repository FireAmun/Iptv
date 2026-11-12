"""Prepare the disposable Actions AVD before the emulator runner launches it."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys


CAPTURE_LOG = Path("app/build/reports/device-previews/bootstrap-system.txt")


def configure_density(config: Path) -> None:
    """Scale the Pixel 2 density with its 720x1280 launch skin, preserving dp."""
    original = config.read_bytes()
    entries = list(re.finditer(rb"(?m)^[ \t]*hw\.lcd\.density[ \t]*=([^\r\n]*)", original))
    if len(entries) != 1 or entries[0][1].strip() != b"420":
        raise ValueError("Expected exactly one hw.lcd.density=420 in the modern phone AVD")
    entry = entries[0]
    offset = entry.start(1) + entry[1].index(b"420")
    config.write_bytes(original[:offset] + b"280" + original[offset + 3:])


def capture_bootstrap() -> None:
    # Start the host server once before the waiter; never restart guest adbd.
    subprocess.run(["adb", "start-server"], stdin=subprocess.DEVNULL, check=True, timeout=10)
    CAPTURE_LOG.parent.mkdir(parents=True, exist_ok=True)
    with CAPTURE_LOG.open("wb") as log:
        # The action's default port is 5554. Redirect every descriptor so the
        # bounded collector cannot hold the action's command pipes open.
        subprocess.Popen(
            ["timeout", "-k", "2s", "120s", "bash", "-c",
             "adb -s emulator-5554 wait-for-device && exec adb -s emulator-5554 logcat -b all -v threadtime"],
            stdin=subprocess.DEVNULL,
            stdout=log,
            stderr=subprocess.STDOUT,
            close_fds=True,
            start_new_session=True,
        )


def main(argv=None) -> int:
    if (not sys.platform.startswith("linux") or os.environ.get("GITHUB_ACTIONS") != "true"
            or os.environ.get("RUNNER_OS") != "Linux"):
        print("Cloud device preparation requires a disposable Linux GitHub Actions runner.", file=sys.stderr)
        return 2

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=("phone-api28", "phone-api36", "tv-api36"))
    profile = parser.parse_args(argv).profile
    try:
        if profile == "phone-api36":
            avd_home = os.environ.get("ANDROID_AVD_HOME")
            avd_dir = Path(avd_home) if avd_home else Path.home() / ".android/avd"
            configure_density(avd_dir / "test.avd/config.ini")
            print("Cloud phone display density: 420 -> 280 dpi.")
        capture_bootstrap()
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print(f"Cloud device preparation failed: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
