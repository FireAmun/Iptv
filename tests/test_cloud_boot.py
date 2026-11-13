"""Exercise cloud guest preparation without invoking an SDK or real clock."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "scripts/prepare-cloud-boot.py"
OLD_ID = "11111111-1111-1111-1111-111111111111"
NEW_ID = "22222222-2222-2222-2222-222222222222"
HOME = "com.google.android.apps.nexuslauncher/.NexusLauncherActivity"
FULL_HOME = "com.google.android.apps.nexuslauncher/com.google.android.apps.nexuslauncher.NexusLauncherActivity"
CACHE = "BootCompleteCacheImpl:\nBootCompleteCache state:\n  boot complete: true\n"
WINDOW = (f"  mCurrentFocus=Window{{abc u0 {FULL_HOME}}}\n"
          f"  mFocusedApp=ActivityRecord{{def u0 {HOME} t6}}\n"
          f"  Window #6 Window{{abc u0 {FULL_HOME}}}:\n"
          "    mHasSurface=true isReadyForDisplay()=true\n"
          "      Surface: shown=true    isOnScreen=true\n")


class Clock:
    def __init__(self):
        self.now = 0.0

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class CloudBootPreparation(unittest.TestCase):
    def setUp(self):
        self.assertTrue(SCRIPT.is_file(), "cloud boot helper is missing")
        spec = importlib.util.spec_from_file_location("prepare_cloud_boot", SCRIPT)
        self.helper = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.helper)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.log = Path(directory.name) / "cloud-boot-preparation.jsonl"
        self.clock = Clock()
        self.calls = []
        self.reboots = 0
        self.unlocks = 0
        self.first_anr = False
        self.after_unlock_anr = False
        self.boot_ids = []
        self.empty_id = False
        self.cache = CACHE
        self.window = WINDOW
        self.installed = False
        self.timeout_dump = False
        self.diagnostic_calls = []
        self.diagnostic_output = b"fresh cloud failure payload\n"
        self.diagnostic_timeout = False
        self.diagnostic_error = False

    def adb(self, command, **kwargs):
        self.assertEqual(["adb", "-s", "emulator-5554"], command[:3])
        self.assertGreater(kwargs["timeout"], 0)
        self.assertLessEqual(kwargs["timeout"], 10)
        if kwargs.get("text") is False:
            self.assertLessEqual(kwargs["timeout"], 5)
            self.assertEqual(subprocess.STDOUT, kwargs["stderr"])
            self.diagnostic_calls.append(command[3:])
            if self.diagnostic_error:
                raise OSError("synthetic diagnostic read failure")
            if self.diagnostic_timeout:
                self.clock.now += kwargs["timeout"]
                raise subprocess.TimeoutExpired(command, kwargs["timeout"], output=b"partial synthetic payload\n")
            return subprocess.CompletedProcess(command, 0, stdout=self.diagnostic_output)
        self.calls.append(command[3:])
        args = command[3:]
        if args == ["reboot"]:
            self.reboots += 1
            output = ""
        elif args == ["shell", "input", "keyevent", "82"]:
            self.unlocks += 1
            output = ""
        elif args == ["shell", "cat", "/proc/sys/kernel/random/boot_id"]:
            output = self.boot_ids.pop(0) if self.boot_ids else "" if self.empty_id else OLD_ID
        elif args == ["shell", "getprop", "sys.boot_completed"]:
            output = "1\n"
        elif args == ["shell", "getprop", "ro.build.version.sdk"]:
            output = "36\n"
        elif args == ["shell", "pm", "list", "packages", "com.fireamun.iptv.prototype"]:
            output = "package:com.fireamun.iptv.prototype\n" if self.installed else ""
        elif args == ["shell", "dumpsys", "activity", "service", "com.android.systemui/.SystemUIService", "BootCompleteCacheImpl"]:
            if self.timeout_dump:
                self.clock.now += kwargs["timeout"]
                raise subprocess.TimeoutExpired(command, kwargs["timeout"])
            output = self.cache
        elif args == ["shell", "cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"]:
            # Android16 --brief prints this metadata before its component.
            output = "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n" + HOME
        elif args == ["shell", "cmd", "package", "resolve-activity", "--components", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"]:
            output = HOME
        elif args == ["shell", "dumpsys", "window"]:
            output = self.window
        elif args == ["logcat", "-b", "events", "-d", "-v", "brief", "am_anr:I", "*:S"]:
            output = "I/am_anr(668): [0,1071,com.android.systemui,0,service]\n" if self.first_anr or (self.unlocks and self.after_unlock_anr) else ""
        else:
            self.fail(f"unexpected device operation: {args}")
        return subprocess.CompletedProcess(command, 0, stdout=output)

    def invoke(self, profile="phone-api36", *, actions="true", runner="Linux", platform="linux"):
        env = {"GITHUB_ACTIONS": actions, "RUNNER_OS": runner, "IPTV_DEVICE_PROFILE": profile}
        out, err = io.StringIO(), io.StringIO()
        with (patch.dict(os.environ, env), patch.object(self.helper.sys, "platform", platform),
              patch.object(self.helper, "EVIDENCE", self.log),
              patch.object(self.helper.subprocess, "run", side_effect=self.adb),
              patch.object(self.helper.time, "monotonic", self.clock.monotonic),
              patch.object(self.helper.time, "sleep", self.clock.sleep),
              contextlib.redirect_stdout(out), contextlib.redirect_stderr(err)):
            result = self.helper.main()
        return result, out.getvalue(), err.getvalue()

    def test_host_guards_and_profile_fail_before_any_device_or_file_work(self):
        for args in ({"actions": "false"}, {"runner": "Windows"}, {"platform": "win32"}, {"profile": ""}, {"profile": "other"}):
            with self.subTest(args=args):
                self.assertEqual(2, self.invoke(**args)[0])
                self.assertFalse(self.calls)
                self.assertFalse(self.log.exists())

    def test_other_known_profiles_skip_without_any_device_operation(self):
        for profile in ("phone-api28", "tv-api36"):
            self.assertEqual(0, self.invoke(profile)[0])
        self.assertFalse(self.calls)
        self.assertFalse(self.log.exists())

    def test_clean_boot_never_reboots_and_unlocks_once(self):
        result, out, _ = self.invoke()
        self.assertEqual(0, result)
        self.assertEqual(0, self.reboots)
        self.assertEqual(1, self.unlocks)
        events = [json.loads(line) for line in self.log.read_text().splitlines()]
        self.assertEqual(["cold-start", "cold-unlocked", "cold-ready", "prepared"], [e["phase"] for e in events])
        self.assertTrue(all(e["reboot_count"] == 0 and e["anr_count"] == 0 for e in events))
        self.assertTrue(events[-2]["systemui_responsive"])
        self.assertTrue(events[-2]["launcher_ready"])
        self.assertNotIn(OLD_ID, self.log.read_text() + out)
        self.assertNotIn(HOME, self.log.read_text() + out)

    def test_missing_or_false_systemui_marker_never_reboots(self):
        for marker in ("", CACHE.replace("true", "false"), "unrelated boot complete: true"):
            self.cache = marker
            self.clock.now = 0
            self.assertEqual(2, self.invoke()[0])
            self.assertEqual(0, self.reboots)
            self.assertLessEqual(self.clock.now, 180)

    def test_empty_initial_boot_id_never_reboots(self):
        self.empty_id = True
        self.assertEqual(2, self.invoke()[0])
        self.assertEqual(0, self.reboots)

    def test_initial_anr_fails_before_unlock_without_retry(self):
        self.first_anr = True
        self.assertEqual(2, self.invoke()[0])
        self.assertEqual(0, self.reboots)
        self.assertEqual(0, self.unlocks)

    def test_boot_identity_change_across_pending_poll_samples_is_terminal(self):
        self.boot_ids = [OLD_ID, NEW_ID, NEW_ID]
        original = self.adb
        def pending_first_sample(command, **kwargs):
            result = original(command, **kwargs)
            if command[3:] == ["shell", "getprop", "sys.boot_completed"] and self.clock.now == 0:
                return subprocess.CompletedProcess(command, 0, stdout="0\n")
            return result
        with patch.object(self, "adb", side_effect=pending_first_sample):
            self.assertEqual(2, self.invoke()[0])
        self.assertEqual(0, self.reboots)
        self.assertEqual(0, self.unlocks)
        self.assertIn("boot-changed-during-observation", self.log.read_text())

    def test_boot_identity_change_during_sample_is_terminal(self):
        self.boot_ids = [OLD_ID, NEW_ID]
        self.assertEqual(2, self.invoke()[0])
        self.assertEqual(0, self.reboots)
        self.assertIn("boot-changed-during-observation", self.log.read_text())

    def test_native_error_window_without_anr_event_fails_before_unlock(self):
        for title in ("Application Not Responding: system", "Application Error: system", "System Error", "Factory Error"):
            with self.subTest(title=title):
                self.reboots = self.unlocks = 0
                self.clock.now = 0
                self.first_anr = False
                self.window = f"  mCurrentFocus=Window{{err u0 {title}}}\n"
                self.assertEqual(2, self.invoke()[0])
                self.assertEqual(0, self.reboots)
                self.assertEqual(0, self.unlocks)

    def test_missing_focus_hidden_surface_and_wrong_home_fail_after_one_unlock(self):
        for window in ("", WINDOW.replace("mHasSurface=true", "mHasSurface=false"),
                       WINDOW.replace("isReadyForDisplay()=true", "isReadyForDisplay()=false"),
                       WINDOW.replace("Surface: shown=true", "Surface: shown=false"),
                       WINDOW.replace("isOnScreen=true", "isOnScreen=false"),
                       WINDOW.replace("mCurrentFocus=Window{abc u0 " + FULL_HOME,
                                      "mCurrentFocus=Window{other u0 android/com.android.internal.app.ResolverActivity")):
            with self.subTest(window=window):
                self.reboots = self.unlocks = 0
                self.clock.now = 0
                self.window = window
                self.assertEqual(2, self.invoke()[0])
                self.assertEqual(0, self.reboots)
                self.assertEqual(1, self.unlocks)

    def test_preinstalled_app_is_rejected_without_reboot(self):
        self.installed = True
        self.assertEqual(2, self.invoke()[0])
        self.assertEqual(0, self.reboots)

    def test_app_installed_after_unlock_is_rejected(self):
        original = self.adb
        def installed_after_unlock(command, **kwargs):
            if self.unlocks:
                self.installed = True
            return original(command, **kwargs)
        with patch.object(self, "adb", side_effect=installed_after_unlock):
            self.assertEqual(2, self.invoke()[0])
        self.assertEqual(0, self.reboots)
        self.assertEqual(1, self.unlocks)
        self.assertIn("app-already-installed", self.log.read_text())

    def test_final_anr_check_follows_final_package_observation(self):
        original = self.adb
        def late_anr(command, **kwargs):
            result = original(command, **kwargs)
            if self.unlocks and command[3:6] == ["shell", "pm", "list"]:
                self.first_anr = True
            return result
        with patch.object(self, "adb", side_effect=late_anr):
            self.assertEqual(2, self.invoke()[0])
        self.assertEqual(0, self.reboots)
        self.assertEqual(1, self.unlocks)
        self.assertIn("cold-boot-anr", self.log.read_text())

    def test_wrong_api_is_rejected_before_unlock(self):
        original = self.adb
        def wrong_api(command, **kwargs):
            result = original(command, **kwargs)
            if command[3:] == ["shell", "getprop", "ro.build.version.sdk"]:
                return subprocess.CompletedProcess(command, 0, stdout="35\n")
            return result
        with patch.object(self, "adb", side_effect=wrong_api):
            self.assertEqual(2, self.invoke()[0])
        self.assertEqual(0, self.reboots)
        self.assertEqual(0, self.unlocks)
        self.assertIn("unexpected-api", self.log.read_text())

    def test_command_timeouts_do_not_escape_the_phase_deadline_or_leak_output(self):
        self.timeout_dump = True
        result, out, err = self.invoke()
        self.assertEqual(2, result)
        self.assertEqual(0, self.reboots)
        self.assertLessEqual(self.clock.now, 180)
        self.assertNotIn("adb", out + err)

    def test_uncertain_unlock_is_never_reissued(self):
        original = self.adb
        def timeout_unlock(command, **kwargs):
            result = original(command, **kwargs)
            if command[3:] == ["shell", "input", "keyevent", "82"]:
                raise subprocess.TimeoutExpired(command, kwargs["timeout"])
            return result
        with patch.object(self, "adb", side_effect=timeout_unlock):
            self.assertEqual(2, self.invoke()[0])
        self.assertEqual(0, self.reboots)
        self.assertEqual(1, self.unlocks)

    def test_anr_appearing_after_unlock_is_rejected(self):
        self.after_unlock_anr = True
        self.assertEqual(2, self.invoke()[0])
        self.assertEqual(0, self.reboots)
        self.assertEqual(1, self.unlocks)

    def test_package_query_diagnostic_is_not_accepted_as_app_absence(self):
        original = self.adb
        def broken_package_query(command, **kwargs):
            if command[3:6] == ["shell", "pm", "list"]:
                return subprocess.CompletedProcess(command, 0, stdout="Error: package service unavailable\n")
            return original(command, **kwargs)
        with patch.object(self, "adb", side_effect=broken_package_query):
            self.assertEqual(2, self.invoke()[0])
        self.assertEqual(0, self.reboots)

    def test_launcher_visibility_accepts_wallpaper_metadata_between_native_fields(self):
        # Full AOSP API36 prints wallpaper attributes between these properties.
        self.window = WINDOW.replace(
            "Surface: shown=true    isOnScreen=true",
            "Surface: shown=true    mWallpaperX=0.0 mWallpaperY=0.5\n"
            "    mWallpaperXStep=0.333 mWallpaperYStep=1.0\n"
            "    isOnScreen=true\n    isVisible=true",
        )
        self.assertEqual(0, self.invoke()[0])
        self.assertEqual(0, self.reboots)
        self.assertEqual(1, self.unlocks)
        self.assertFalse(self.helper.launcher_ready(self.window.replace("isOnScreen=true", "isOnScreen=false"), HOME))
        self.assertFalse(self.helper.launcher_ready(self.window.replace("Surface: shown=true", "Surface: shown=false"), HOME))
        self.assertFalse(self.helper.launcher_ready(self.window, "another.launcher/.Home"))

    def test_terminal_anr_preserves_event_payload_with_bounded_readonly_diagnostics(self):
        self.first_anr = True
        self.diagnostic_output = b"1700000000.123 I/am_anr(668): [0,1001,com.android.systemui,0,fresh failure]\n"
        result, out, err = self.invoke()
        self.assertEqual(2, result)
        self.assertEqual(0, self.reboots)
        self.assertEqual(0, self.unlocks)
        self.assertEqual(6, len(self.diagnostic_calls))
        root = self.log.parent / "cloud-boot-failure"
        self.assertIn(b"com.android.systemui", (root / "events-anr.txt").read_bytes())
        self.assertEqual({"events-anr.txt", "logcat-tail.txt", "lastanr.txt", "window.txt", "cpuinfo.txt", "memory.txt"},
                         {p.name for p in root.iterdir()})
        self.assertIn(["logcat", "-b", "all", "-d", "-t", "2000", "-v", "threadtime"], self.diagnostic_calls)
        self.assertTrue(all(c[0] == "logcat" or c[:2] == ["shell", "dumpsys"]
                            or c[:2] == ["shell", "cat"] for c in self.diagnostic_calls))
        self.assertNotIn("com.android.systemui", out + err)
        records = [json.loads(line) for line in self.log.read_text().splitlines()]
        self.assertEqual("cold-boot-anr", next(e["reason"] for e in records if e["phase"] == "failed"))

    def test_failure_artifacts_are_byte_bounded_and_keep_recent_logcat_tail(self):
        self.first_anr = True
        self.diagnostic_output = b"old" + b"x" * (300 * 1024) + b"recent-event"
        self.assertEqual(2, self.invoke()[0])
        root = self.log.parent / "cloud-boot-failure"
        self.assertTrue(root.is_dir())
        for p in root.iterdir():
            self.assertLessEqual(p.stat().st_size, 256 * 1024)
        self.assertTrue((root / "logcat-tail.txt").read_bytes().endswith(b"recent-event"))
        self.assertTrue((root / "events-anr.txt").read_bytes().endswith(b"recent-event"))

    def test_diagnostic_timeouts_are_bounded_and_cannot_override_original_failure(self):
        self.first_anr = True
        self.diagnostic_timeout = True
        result, _, err = self.invoke()
        self.assertEqual(2, result)
        self.assertIn("cold-boot-anr", err)
        self.assertEqual(30, self.clock.now)
        self.assertEqual(6, len(self.diagnostic_calls))
        self.assertEqual(0, self.reboots)
        self.assertEqual(0, self.unlocks)
        self.assertIn(b"partial synthetic payload", (self.log.parent / "cloud-boot-failure/events-anr.txt").read_bytes())

    def test_diagnostic_process_errors_preserve_original_failure(self):
        self.first_anr = True
        self.diagnostic_error = True
        result, out, err = self.invoke()
        self.assertEqual(2, result)
        self.assertIn("cold-boot-anr", err)
        self.assertNotIn("synthetic diagnostic read failure", out + err)
        self.assertEqual(0, self.reboots)
        self.assertEqual(0, self.unlocks)


if __name__ == "__main__":
    unittest.main()
