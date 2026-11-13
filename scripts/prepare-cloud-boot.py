"""Prepare one Android 16 cloud guest boot before APK installation or tests."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time


EVIDENCE = Path("app/build/reports/device-previews/cloud-boot-preparation.jsonl")
PHASE_SECONDS = 180
COMMAND_SECONDS = 10
PROFILES = ("phone-api28", "phone-api36", "tv-api36")
BOOT_ID = re.compile(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
COMPONENT = re.compile(r"[\w.]+/[\w.$]+")
FAILURE_SECONDS = 30
FAILURE_COMMAND_SECONDS = 5
FAILURE_FILE_BYTES = 256 * 1024


class Pending(Exception):
    """A bounded boot observation is not available yet."""


class PreparationFailed(Exception):
    """A terminal preparation failure with a fixed, non-private reason."""


def record(phase, **values):
    # Never persist command output, private settings, package data or boot IDs.
    entry = {"phase": phase, **values}
    with EVIDENCE.open("a", encoding="utf-8") as output:
        output.write(json.dumps(entry, sort_keys=True) + "\n")
    print("Cloud boot preparation: " + json.dumps(entry, sort_keys=True), flush=True)


def adb(deadline, *args):
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise PreparationFailed("readiness-deadline")
    try:
        result = subprocess.run(
            ["adb", "-s", "emulator-5554", *args],
            stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL, text=True, check=False,
            timeout=min(COMMAND_SECONDS, remaining),
        )
    except subprocess.TimeoutExpired:
        raise Pending from None
    if result.returncode:
        raise Pending
    return result.stdout.strip()


def component(value):
    if not COMPONENT.fullmatch(value):
        return None
    package, activity = value.split("/")
    return package + "/" + (package + activity if activity.startswith(".") else activity)


def launcher_ready(window, home):
    resolved = component(home)
    if not resolved:
        return False
    focus = re.search(r"(?m)^\s*mCurrentFocus=Window\{(\S+) u0 ([^}\s]+)\}", window)
    app = re.search(r"(?m)^\s*mFocusedApp=ActivityRecord\{\S+ u0 (\S+)", window)
    if not focus or not app or component(focus[2]) != resolved or component(app[1]) != resolved:
        return False
    # Bind visibility to this exact focused native window, not another surface.
    block = re.search(
        r"(?ms)^  Window #\d+ Window\{" + re.escape(focus[1])
        + r" u0 " + re.escape(focus[2]) + r"\}:\n(.*?)(?=^  Window #|\Z)", window,
    )
    return bool(block and "mHasSurface=true isReadyForDisplay()=true" in block[1]
                and re.search(r"(?m)^\s*Surface: shown=true\b", block[1])
                and re.search(r"\bisOnScreen=true\b", block[1]))


def anr_count(deadline):
    events = adb(deadline, "logcat", "-b", "events", "-d", "-v", "brief", "am_anr:I", "*:S")
    return len(re.findall(r"(?m)^I/am_anr\s*\(\s*\d+\):", events))


def no_app(deadline):
    packages = adb(deadline, "shell", "pm", "list", "packages", "com.fireamun.iptv.prototype")
    if any(not re.fullmatch(r"package:[\w.]+", line) for line in packages.splitlines()):
        raise Pending
    if re.search(r"(?m)^package:com\.fireamun\.iptv\.prototype(?:\.test)?$", packages):
        raise PreparationFailed("app-already-installed")


def capture_failure():
    """Read bounded cloud OS evidence without changing the failed gate result."""
    start = time.monotonic()
    deadline = start + FAILURE_SECONDS
    root = EVIDENCE.parent / "cloud-boot-failure"
    try:
        root.mkdir(parents=True, exist_ok=True)
    except OSError:
        return {"commands": 0, "errors": 1, "truncated": 0}
    commands = (
        ("events-anr", ["logcat", "-b", "events", "-d", "-v", "epoch", "am_anr:I", "*:S"], True),
        ("logcat-tail", ["logcat", "-b", "all", "-d", "-t", "2000", "-v", "threadtime"], True),
        ("lastanr", ["shell", "dumpsys", "activity", "lastanr"], False),
        ("window", ["shell", "dumpsys", "window"], False),
        ("cpuinfo", ["shell", "dumpsys", "cpuinfo"], False),
        ("memory", ["shell", "cat", "/proc/meminfo", "/proc/stat", "/proc/loadavg", "/proc/uptime"], False),
    )
    attempted = errors = truncated = 0
    for name, args, keep_tail in commands:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        attempted += 1
        output = b""
        try:
            result = subprocess.run(
                ["adb", "-s", "emulator-5554", *args],
                stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=False, check=False, timeout=min(FAILURE_COMMAND_SECONDS, remaining),
            )
            output = result.stdout or b""
            errors += bool(result.returncode)
        except subprocess.TimeoutExpired as error:
            output = error.stdout or b""
            errors += 1
        except (OSError, subprocess.SubprocessError):
            errors += 1
        if isinstance(output, str):
            output = output.encode("utf-8", errors="replace")
        truncated += len(output) > FAILURE_FILE_BYTES
        bounded = output[-FAILURE_FILE_BYTES:] if keep_tail else output[:FAILURE_FILE_BYTES]
        try:
            (root / (name + ".txt")).write_bytes(bounded)
        except OSError:
            errors += 1
    return {"commands": attempted, "errors": errors, "truncated": truncated,
            "elapsed_seconds": round(time.monotonic() - start, 3)}


def wait_ready(phase, previous_boot=None):
    start = time.monotonic()
    deadline = start + PHASE_SECONDS
    unlocked = False
    samples = 0
    while time.monotonic() < deadline:
        samples += 1
        try:
            current = adb(deadline, "shell", "cat", "/proc/sys/kernel/random/boot_id")
            if not BOOT_ID.fullmatch(current) or current == previous_boot:
                raise Pending
            sdk = adb(deadline, "shell", "getprop", "ro.build.version.sdk")
            if not sdk:
                raise Pending
            if sdk != "36":
                raise PreparationFailed("unexpected-api")
            no_app(deadline)
            complete = adb(deadline, "shell", "getprop", "sys.boot_completed") == "1"
            if not complete:
                raise Pending
            cache = adb(deadline, "shell", "dumpsys", "activity", "service",
                        "com.android.systemui/.SystemUIService", "BootCompleteCacheImpl")
            responsive = bool(re.search(r"(?m)^\s*BootCompleteCache state:\s*\n\s*boot complete: true\s*$", cache))
            count = anr_count(deadline)
            if previous_boot and count:
                raise PreparationFailed("second-boot-anr")
            if not responsive:
                raise Pending
            if previous_boot:
                before_unlock = adb(deadline, "shell", "dumpsys", "window")
                # A system-server boot error need not emit an am_anr event.
                # Reject native error windows before sending any unlock input.
                if re.search(r"Application Not Responding|Application Error|System\s*Error|Factory Error|ty=SYSTEM_ERROR", before_unlock, re.IGNORECASE):
                    raise PreparationFailed("second-boot-error-window")
                # Standard one-shot unlock, after SystemUI answers and before
                # checking launcher focus. Never send input to an ANR dialog.
                if not unlocked:
                    unlocked = True
                    try:
                        adb(deadline, "shell", "input", "keyevent", "82")
                    except Pending:
                        raise PreparationFailed("unlock-command-failed") from None
                    record("second-unlocked", boot_changed=True)
                home = adb(deadline, "shell", "cmd", "package", "resolve-activity", "--components",
                           "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
                window = adb(deadline, "shell", "dumpsys", "window")
                if not launcher_ready(window, home):
                    raise Pending
                count = anr_count(deadline)
                if count:
                    raise PreparationFailed("second-boot-anr")
                no_app(deadline)
            if adb(deadline, "shell", "cat", "/proc/sys/kernel/random/boot_id") != current:
                raise PreparationFailed("boot-changed-during-observation")
            record(phase + "-ready", boot_changed=bool(previous_boot), anr_count=count,
                   samples=samples, elapsed_seconds=round(time.monotonic() - start, 3),
                   systemui_responsive=True, launcher_ready=bool(previous_boot))
            return current
        except Pending:
            remaining = deadline - time.monotonic()
            if remaining > 0:
                time.sleep(min(2, remaining))
    raise PreparationFailed(phase + "-readiness-deadline")


def main():
    if (not sys.platform.startswith("linux") or os.environ.get("GITHUB_ACTIONS") != "true"
            or os.environ.get("RUNNER_OS") != "Linux"):
        print("Cloud boot preparation requires a disposable Linux GitHub Actions runner.", file=sys.stderr)
        return 2
    profile = os.environ.get("IPTV_DEVICE_PROFILE")
    if profile not in PROFILES:
        print("Cloud boot preparation requires a known device profile.", file=sys.stderr)
        return 2
    if profile != "phone-api36":
        return 0
    try:
        EVIDENCE.parent.mkdir(parents=True, exist_ok=True)
        record("first-start")
        initial = wait_ready("first")
        record("reboot-requested", reboot_count=1)
        try:
            adb(time.monotonic() + COMMAND_SECONDS, "reboot")
        except Pending:
            # A timeout may mean the reboot was accepted. Never issue it twice.
            raise PreparationFailed("reboot-command-failed") from None
        wait_ready("second", initial)
        record("prepared", boot_changed=True, reboot_count=1, anr_count=0)
    except (PreparationFailed, OSError) as error:
        reason = str(error) if isinstance(error, PreparationFailed) else "evidence-or-process-error"
        try:
            record("failed", reason=reason)
        except OSError:
            pass
        diagnostics = capture_failure()
        try:
            record("failure-diagnostics", **diagnostics)
        except OSError:
            pass
        print("Cloud boot preparation failed: " + reason, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
