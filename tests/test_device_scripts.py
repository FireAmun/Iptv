"""Exercise CI failure gates with fake commands; never contact an emulator."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASH = "C:/Program Files/Git/bin/bash.exe" if os.name == "nt" else shutil.which("bash")


class DeviceScriptGates(unittest.TestCase):
    def run_script(self, script, mode="success", gradle=0, probe=0, event=None, scope=None, expect_filter=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "bin").mkdir()
            (root / "scripts").mkdir()
            commands = {
                "adb": """#!/usr/bin/env bash
if [[ "$1" == install || "$1" == pull ]]; then exit 0; fi
if [[ "$3" == force-stop ]]; then exit 0; fi
case "$7" in
  seed) pid=100 ;;
  verify) if [[ "$STUB_MODE" == same_pid ]]; then pid=100; else pid=101; fi ;;
  clear) pid=102 ;;
  *) exit 9 ;;
esac
if [[ "$STUB_MODE" != missing_marker && "$STUB_MODE" != "missing_old_$7" ]]; then echo "INSTRUMENTATION_RESULT: stream=IPTV_PERSISTENCE_PROBE_OK pid=$pid"; fi
if [[ "$STUB_MODE" != missing_progress_marker && "$STUB_MODE" != "missing_progress_$7" ]]; then echo "IPTV_PROGRESS_PROBE_OK stage=$7"; fi
if [[ "$STUB_MODE" == timeout_after_marker && "$7" == seed ]]; then exit 124; fi
""",
                "timeout": '#!/usr/bin/env bash\nshift\nexec "$@"\n',
            }
            for name, text in commands.items():
                p = root / "bin" / name
                p.write_text(text, encoding="utf-8", newline="\n")
                p.chmod(0o755)
            (root / "gradlew").write_text('''if [[ "$EXPECT_FILTER" == yes && "$*" != *android.testInstrumentationRunnerArguments.class=* ]]; then exit 19; fi
if [[ "$EXPECT_FILTER" == no && "$*" == *android.testInstrumentationRunnerArguments.class=* ]]; then exit 20; fi
''' + f"exit {gradle}\n", encoding="ascii")
            (root / "scripts" / "persistence-process-test.sh").write_text(f"exit {probe}\n", encoding="ascii")
            env = os.environ.copy()
            env["PATH"] = str(root / "bin") + os.pathsep + env["PATH"]
            env["STUB_MODE"] = mode
            env.pop("IPTV_TEST_SCOPE", None)
            env.pop("GITHUB_EVENT_NAME", None)
            env["EXPECT_FILTER"] = expect_filter or ""
            if event is not None:
                env["GITHUB_EVENT_NAME"] = event
            if scope is not None:
                env["IPTV_TEST_SCOPE"] = scope
            return subprocess.run([BASH, str(ROOT / "scripts" / script).replace("\\", "/")], cwd=root,
                                  env=env, text=True, capture_output=True, timeout=15).returncode

    def test_cold_process_success_requires_different_pids(self):
        self.assertEqual(0, self.run_script("persistence-process-test.sh"))

    def test_timeout_after_success_marker_still_fails(self):
        self.assertNotEqual(0, self.run_script("persistence-process-test.sh", "timeout_after_marker"))

    def test_reused_pid_is_rejected(self):
        self.assertNotEqual(0, self.run_script("persistence-process-test.sh", "same_pid"))

    def test_missing_marker_is_rejected(self):
        self.assertNotEqual(0, self.run_script("persistence-process-test.sh", "missing_marker"))

    def test_each_progress_stage_is_required(self):
        for mode in ("missing_progress_seed", "missing_progress_verify", "missing_progress_clear"):
            with self.subTest(mode=mode):
                self.assertNotEqual(0, self.run_script("persistence-process-test.sh", mode))

    def test_each_original_marker_is_required(self):
        for mode in ("missing_old_seed", "missing_old_verify", "missing_old_clear"):
            with self.subTest(mode=mode):
                self.assertNotEqual(0, self.run_script("persistence-process-test.sh", mode))

    def test_gradle_failure_is_preserved(self):
        self.assertEqual(7, self.run_script("device-tests.sh", gradle=7, probe=9))

    def test_process_probe_failure_is_preserved(self):
        self.assertEqual(9, self.run_script("device-tests.sh", probe=9))

    def test_dispatch_scope_filters_the_diagnostic_class(self):
        self.assertEqual(0, self.run_script("device-tests.sh", event="workflow_dispatch", scope="pip", expect_filter="yes"))

    def test_pull_request_cannot_reduce_the_suite(self):
        self.assertEqual(2, self.run_script("device-tests.sh", event="pull_request", scope="pip"))

    def test_invalid_dispatch_scope_is_rejected(self):
        self.assertEqual(2, self.run_script("device-tests.sh", event="workflow_dispatch", scope="pip; invalid"))

    def test_normal_scope_has_no_class_filter(self):
        self.assertEqual(0, self.run_script("device-tests.sh", event="push", scope="full", expect_filter="no"))

    def test_focused_run_preserves_gradle_failure(self):
        self.assertEqual(7, self.run_script("device-tests.sh", event="workflow_dispatch", scope="pip", gradle=7))


if __name__ == "__main__":
    unittest.main()
