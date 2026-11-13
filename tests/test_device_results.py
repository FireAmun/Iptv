"""Validate complete native results using synthetic XML, without a device."""
from pathlib import Path
import tempfile
import unittest

from scripts.check_device_results import validate_results


class DeviceResults(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.xml = self.root / "TEST-device.xml"

    def write(self, cases='<testcase classname="Flow" name="first"/><testcase classname="Flow" name="second"/>', **counts):
        attributes = {"tests": "2", "failures": "0", "errors": "0", "skipped": "0", **counts}
        attrs = " ".join(f'{key}="{value}"' for key, value in attributes.items())
        self.xml.write_text(f'<testsuites {attrs}><testsuite {attrs}>{cases}</testsuite></testsuites>')

    def test_accepts_complete_unique_successful_results(self):
        self.write()
        self.assertEqual(2, validate_results(self.root, 2))

    def test_missing_empty_partial_and_extra_results_fail(self):
        with self.assertRaises(ValueError):
            validate_results(self.root, 2)
        for count in (0, 1, 3):
            self.write("".join(f'<testcase classname="Flow" name="case{i}"/>' for i in range(count)), tests=str(count))
            with self.subTest(count=count), self.assertRaises(ValueError):
                validate_results(self.root, 2)

    def test_case_failures_errors_and_skips_fail(self):
        for tag in ("failure", "error", "skipped"):
            self.write(f'<testcase classname="Flow" name="first"><{tag}/></testcase><testcase classname="Flow" name="second"/>')
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                validate_results(self.root, 2)

    def test_summary_failure_and_count_mismatch_fail(self):
        for counts in ({"failures": "1"}, {"errors": "1"}, {"skipped": "1"}, {"tests": "3"}, {"tests": "bad"}):
            self.write(**counts)
            with self.subTest(counts=counts), self.assertRaises(ValueError):
                validate_results(self.root, 2)

    def test_duplicate_or_missing_case_identity_fails(self):
        for cases in ('<testcase classname="Flow" name="same"/><testcase classname="Flow" name="same"/>',
                      '<testcase classname="Flow"/><testcase classname="Flow" name="second"/>'):
            self.write(cases)
            with self.subTest(cases=cases), self.assertRaises(ValueError):
                validate_results(self.root, 2)

    def test_malformed_and_multiple_reports_fail(self):
        self.xml.write_text("<broken")
        with self.assertRaises(ValueError):
            validate_results(self.root, 2)
        self.write()
        (self.root / "TEST-another.xml").write_bytes(self.xml.read_bytes())
        with self.assertRaises(ValueError):
            validate_results(self.root, 2)


if __name__ == "__main__":
    unittest.main()
