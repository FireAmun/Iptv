"""Exercise cloud preparation using temporary files and mocked processes."""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "scripts/prepare-cloud-device.py"
BASE_CONFIG = b"hw.lcd.width=1080\r\nhw.lcd.density=420\r\nhw.lcd.height=1920\r\n"


class CloudDevicePreparation(unittest.TestCase):
    def setUp(self):
        self.assertTrue(SCRIPT.is_file(), "cloud preparation helper is missing")
        spec = importlib.util.spec_from_file_location("prepare_cloud_device", SCRIPT)
        self.helper = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.helper)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.config = self.root / "test.avd/config.ini"
        self.config.parent.mkdir()
        self.config.write_bytes(BASE_CONFIG)
        self.log = self.root / "reports/bootstrap-system.txt"

    def invoke(self, profile, *, actions="true", runner="Linux", platform="linux", start_error=None):
        env = {"GITHUB_ACTIONS": actions, "RUNNER_OS": runner, "ANDROID_AVD_HOME": str(self.root)}
        with (
            patch.dict(os.environ, env),
            patch.object(self.helper.sys, "platform", platform),
            patch.object(self.helper, "CAPTURE_LOG", self.log),
            patch.object(self.helper.subprocess, "run", side_effect=start_error) as start,
            patch.object(self.helper.subprocess, "Popen") as stream,
            contextlib.redirect_stdout(io.StringIO()),
            contextlib.redirect_stderr(io.StringIO()),
        ):
            def record_output(*args, **kwargs):
                self.assertEqual(1, start.call_count, "ADB server must start before capture")
                kwargs["stdout"].write(b"fake guest log\n")

            stream.side_effect = record_output
            result = self.helper.main([profile])
        return result, start, stream

    def test_non_cloud_or_non_linux_rejected_before_file_or_sdk_work(self):
        for actions, runner, platform in (
            ("false", "Linux", "linux"),
            ("true", "Windows", "linux"),
            ("true", "Linux", "win32"),
        ):
            with self.subTest(actions=actions, runner=runner, platform=platform):
                result, start, stream = self.invoke(
                    "phone-api36", actions=actions, runner=runner, platform=platform,
                )
                self.assertEqual(2, result)
                self.assertEqual(BASE_CONFIG, self.config.read_bytes())
                self.assertFalse(self.log.parent.exists())
                start.assert_not_called()
                stream.assert_not_called()

    def test_density_updates_only_the_exact_existing_value(self):
        self.helper.configure_density(self.config)
        self.assertEqual(
            b"hw.lcd.width=1080\r\nhw.lcd.density=280\r\nhw.lcd.height=1920\r\n",
            self.config.read_bytes(),
        )

    def test_missing_duplicate_or_unexpected_density_rejected_without_writing(self):
        for config in (b"hw.lcd.width=1080\n", BASE_CONFIG + b"hw.lcd.density=420\n",
                       b"hw.lcd.density=320\n", b"hw.lcd.density=280\n"):
            with self.subTest(config=config):
                self.config.write_bytes(config)
                with self.assertRaises(ValueError):
                    self.helper.configure_density(self.config)
                self.assertEqual(config, self.config.read_bytes())

    def test_only_modern_phone_changes_density_but_every_profile_captures(self):
        for profile in ("phone-api28", "phone-api36", "tv-api36"):
            with self.subTest(profile=profile):
                self.config.write_bytes(BASE_CONFIG)
                result, start, stream = self.invoke(profile)
                self.assertEqual(0, result)
                self.assertEqual(
                    BASE_CONFIG.replace(b"420", b"280") if profile == "phone-api36" else BASE_CONFIG,
                    self.config.read_bytes(),
                )
                start.assert_called_once()
                stream.assert_called_once()
                self.assertEqual(b"fake guest log\n", self.log.read_bytes())

    def test_capture_waits_for_the_named_guest_under_a_hard_deadline(self):
        result, start, stream = self.invoke("tv-api36")
        self.assertEqual(0, result)
        start.assert_called_once_with(["adb", "start-server"], stdin=subprocess.DEVNULL, check=True, timeout=10)
        self.assertEqual(
            ["timeout", "-k", "2s", "120s", "bash", "-c",
             "adb -s emulator-5554 wait-for-device && exec adb -s emulator-5554 logcat -b all -v threadtime"],
            stream.call_args.args[0],
        )
        launch = stream.call_args.kwargs
        self.assertEqual(subprocess.DEVNULL, launch["stdin"])
        self.assertEqual(subprocess.STDOUT, launch["stderr"])
        self.assertTrue(launch["close_fds"])
        self.assertTrue(launch["start_new_session"])
        self.assertTrue(launch["stdout"].closed)

    def test_server_timeout_is_reported_without_starting_collector(self):
        result, start, stream = self.invoke("tv-api36", start_error=subprocess.TimeoutExpired("adb", 10))
        self.assertEqual(2, result)
        start.assert_called_once()
        stream.assert_not_called()


if __name__ == "__main__":
    unittest.main()
