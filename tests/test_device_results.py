"""Reject incomplete native test evidence, including Gradle's zero-test success."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts/check_device_results.py"
PACKAGE = "com.fireamun.iptv.prototype."


class DeviceResults(unittest.TestCase):
    def run_check(self, scope="player", groups=None, mutate=None, copies=1):
        groups = groups if groups is not None else {
            "PlayerTracksTest": 12, "PlayerPictureInPictureTest": 6, "PlaybackCheckpointTest": 7,
        }
        suite = ET.Element("testsuite", tests=str(sum(groups.values())), failures="0", errors="0", skipped="0")
        for name, count in groups.items():
            for index in range(count):
                ET.SubElement(suite, "testcase", classname=PACKAGE + name, name=f"case{index}")
        if mutate:
            replacement = mutate(suite)
            if replacement is not None and replacement.tag == "testsuites":
                suite = replacement
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            for index in range(copies):
                ET.ElementTree(suite).write(folder / f"TEST-device-{index}.xml", encoding="utf-8")
            return subprocess.run([sys.executable, str(CHECKER), "--scope", scope,
                                   "--results-dir", str(folder)], capture_output=True, text=True, timeout=10)

    def test_complete_player_and_full_results_pass(self):
        self.assertEqual(0, self.run_check().returncode)
        self.assertEqual(0, self.run_check(scope="full", groups={"ExistingTest": 109}).returncode)

    def test_agp_testsuites_wrapper_checks_each_nested_summary(self):
        def wrap(suite):
            parent = ET.Element("testsuites", **suite.attrib)
            parent.append(suite)
            return parent
        self.assertEqual(0, self.run_check(mutate=wrap).returncode)
        def bad_child(suite):
            parent = wrap(suite)
            suite.set("tests", "0")
            return parent
        self.assertNotEqual(0, self.run_check(mutate=bad_child).returncode)

    def test_zero_and_truncated_success_are_rejected(self):
        for groups in ({}, {"PlayerTracksTest": 12}):
            with self.subTest(groups=groups):
                self.assertNotEqual(0, self.run_check(groups=groups).returncode)

    def test_exact_count_with_wrong_class_or_duplicate_is_rejected(self):
        self.assertNotEqual(0, self.run_check(groups={"OtherTest": 25}).returncode)
        def duplicate(suite):
            suite[1].set("name", suite[0].get("name"))
        self.assertNotEqual(0, self.run_check(mutate=duplicate).returncode)

    def test_failure_error_and_skip_cannot_hide_behind_zero_summary(self):
        for kind in ("failure", "error", "skipped"):
            with self.subTest(kind=kind):
                self.assertNotEqual(0, self.run_check(mutate=lambda suite: ET.SubElement(suite[0], kind)).returncode)

    def test_summary_must_match_actual_cases(self):
        for key, value in (("tests", "0"), ("failures", "1"), ("errors", "1"), ("skipped", "1")):
            with self.subTest(key=key):
                self.assertNotEqual(0, self.run_check(mutate=lambda suite: suite.set(key, value)).returncode)

    def test_missing_or_multiple_reports_and_unknown_scope_are_rejected(self):
        for copies in (0, 2):
            self.assertNotEqual(0, self.run_check(copies=copies).returncode)
        self.assertNotEqual(0, self.run_check(scope="unknown").returncode)

    def test_each_single_class_and_forms_scope_is_exact(self):
        for scope, groups in (("tracks", {"PlayerTracksTest": 12}),
                              ("pip", {"PlayerPictureInPictureTest": 6}),
                              ("forms", {"CategoryPickerFlowTest": 2, "UrlVisibilityTest": 4, "XtreamFlowTest": 7})):
            with self.subTest(scope=scope):
                self.assertEqual(0, self.run_check(scope=scope, groups=groups).returncode)


if __name__ == "__main__":
    unittest.main()
