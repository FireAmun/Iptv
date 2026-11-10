"""Run cloud diagnostic collection against fake commands; never use a device."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASH = "C:/Program Files/Git/bin/bash.exe" if os.name == "nt" else shutil.which("bash")


class DeviceDiagnostics(unittest.TestCase):
    def run_diagnostics(self, result=0, mode="success", actions="true", event="push"):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "bin").mkdir()
            commands = {
                "adb": r'''#!/usr/bin/env bash
printf '%s\n' "$*" >> adb-calls.txt
if [[ "$MODE" == errors ]]; then echo 'collection denied'; exit 17; fi
if [[ "$1" == logcat ]]; then
  echo 'boot and system log'
  echo "$$" > stream-pid.txt
  trap 'echo stopped > stream-stopped.txt; exit 0' TERM
  while :; do sleep 0.05; done
fi
if [[ "$1" == pull ]]; then mkdir -p "$3"; echo 'trace' > "$3/anr_test"; fi
if [[ "$1" == bugreport ]]; then echo 'report' > "$2"; fi
if [[ "$1" == shell && "$2" == test && "$MODE" == unrooted ]]; then exit 1; fi
echo "evidence $*"
''',
                "timeout": r'''#!/usr/bin/env bash
printf '%q ' "$@" >> timeout-calls.txt
printf '\n' >> timeout-calls.txt
[[ "$1" == -k ]] || exit 96
[[ "$2" == 2s ]] || exit 97
limit="$3"
shift 3
if [[ "$MODE" == timeouts && "$1 $2" != 'adb logcat' ]]; then exit 124; fi
exec "$@"
''',
            }
            for name, content in commands.items():
                path = root / "bin" / name
                path.write_text(content, encoding="ascii", newline="\n")
                path.chmod(0o755)
            source = str(ROOT / "scripts/device-diagnostics.sh").replace("\\", "/")
            driver = root / "driver.sh"
            driver.write_text(f'''set -u
export PATH="$PWD/bin:$PATH"
[[ "$(command -v adb)" == "$PWD/bin/adb" ]] || exit 95
[[ "$(command -v timeout)" == "$PWD/bin/timeout" ]] || exit 95
source '{source}' || exit 99
start_device_diagnostics || exit $?
trap 'finish_device_diagnostics "$?"' EXIT
# Give the fake stream a chance to start before an immediate fixture exit.
for ((i=0; i<100; i++)); do
  [[ -f stream-pid.txt || "$MODE" == errors ]] && break
  sleep 0.02
done
exit {result}
''', encoding="ascii", newline="\n")
            env = os.environ.copy()
            env.update(PATH=str(root / "bin") + os.pathsep + env["PATH"], MODE=mode,
                       GITHUB_ACTIONS=actions, GITHUB_EVENT_NAME=event)
            run = subprocess.run([BASH, str(driver).replace("\\", "/")], cwd=root, env=env,
                                 capture_output=True, text=True, timeout=15)
            output = root / "app/build/reports/device-previews/system-diagnostics"
            files = {str(p.relative_to(output)).replace("\\", "/"): p.read_text(encoding="utf-8")
                     for p in output.rglob("*") if p.is_file()} if output.exists() else {}
            return run, files, (root / "adb-calls.txt").read_text() if (root / "adb-calls.txt").exists() else "", (
                (root / "timeout-calls.txt").read_text() if (root / "timeout-calls.txt").exists() else ""), (
                root / "stream-stopped.txt").exists()

    def test_guard_rejects_local_or_unknown_event_before_adb(self):
        for actions, event in (("false", "push"), ("", "push"), ("true", ""), ("true", "schedule")):
            with self.subTest(actions=actions, event=event):
                run, files, calls, _, _ = self.run_diagnostics(actions=actions, event=event)
                self.assertEqual(2, run.returncode, run.stderr)
                self.assertEqual("", calls)
                self.assertEqual({}, files)

    def test_success_captures_both_boundaries_and_stops_stream_without_bugreport(self):
        run, files, calls, _, stopped = self.run_diagnostics(event="pull_request")
        self.assertEqual(0, run.returncode, run.stderr)
        for phase in ("before-tests", "after-tests"):
            for name in ("lastanr", "window", "input", "cpuinfo", "memory"):
                self.assertIn(f"{phase}-{name}.txt", files)
        self.assertIn("boot and system log", files["logcat-all.txt"])
        self.assertIn("logcat -b all -v threadtime", calls)
        self.assertIn("/proc/pressure/$name", calls)
        self.assertIn("cpu memory io", calls)
        self.assertIn("/proc/meminfo", calls)
        self.assertIn("anr/anr_test", files)
        self.assertNotIn("bugreport", calls)
        self.assertTrue(stopped, "owned logcat stream outlived EXIT cleanup")

    def test_failure_preserves_original_exit_and_captures_bounded_bugreport(self):
        run, files, calls, bounds, stopped = self.run_diagnostics(result=7, event="workflow_dispatch")
        self.assertEqual(7, run.returncode, run.stderr)
        self.assertIn("bugreport.zip", files)
        self.assertIn("-k 2s 58s adb bugreport", bounds)
        self.assertTrue(stopped)
        self.assertNotIn("root", calls)

    def test_collection_errors_do_not_mask_failed_or_successful_test_result(self):
        for result in (0, 9):
            with self.subTest(result=result):
                run, files, _, _, _ = self.run_diagnostics(result=result, mode="errors")
                self.assertEqual(result, run.returncode, run.stderr)
                self.assertIn("exit=17", files["collection-status.txt"])

    def test_timeouts_are_recorded_without_masking_original_failure(self):
        run, files, _, bounds, stopped = self.run_diagnostics(result=6, mode="timeouts")
        self.assertEqual(6, run.returncode, run.stderr)
        self.assertIn("exit=124", files["collection-status.txt"])
        self.assertTrue(stopped)
        for line in bounds.splitlines():
            words = line.split()
            self.assertEqual(["-k", "2s"], words[:2])
            limit = words[2]
            if "adb logcat" in line:
                self.assertEqual("40m", limit)
            else:
                self.assertTrue(limit.endswith("s"))
                self.assertLessEqual(int(limit[:-1]), 58)

    def test_unreadable_traces_are_not_pulled_and_no_privilege_change_occurs(self):
        run, files, calls, _, stopped = self.run_diagnostics(mode="unrooted")
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertNotIn("pull /data/anr", calls)
        self.assertNotIn("root", calls)
        self.assertIn("exit=1", files["collection-status.txt"])
        self.assertTrue(stopped)


if __name__ == "__main__":
    unittest.main()
