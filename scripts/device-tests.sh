#!/usr/bin/env bash
# Preserve the Gradle result while collecting synthetic previews before emulator shutdown.
set -u
scope="${IPTV_TEST_SCOPE:-full}"
if [[ "$scope" != full && "${GITHUB_EVENT_NAME:-}" != workflow_dispatch ]]; then
  echo 'Focused test scopes are diagnostic workflow_dispatch runs only.' >&2
  exit 2
fi
case "$scope" in
  full|pip|tracks|player|forms) ;;
  *) echo 'Unknown diagnostic test scope.' >&2; exit 2 ;;
esac
printf 'IPTV_TEST_SCOPE=%s\n' "$scope"
test_args=()
if [[ "$scope" != full ]]; then
  # Only an enum crosses AGP's comma transport and Android's shell. The fixed
  # class lists live in ManualFixtureRunner, before AndroidJUnitRunner starts.
  test_args+=("-Pandroid.testInstrumentationRunnerArguments.diagnosticScope=$scope")
fi
if [[ "${GITHUB_ACTIONS:-}" == true ]]; then
  # Verify the clean Android 16 guest before installing APKs or running tests.
  python3 ./scripts/prepare-cloud-boot.py || exit $?
  source ./scripts/device-diagnostics.sh || exit $?
  trap 'finish_device_diagnostics "$?"' EXIT
  start_device_diagnostics || exit $?
  # Cloud guests expose a hardware keyboard. Set the software-keyboard policy
  # before installing/launching the app, rather than resetting it during a form.
  adb -s emulator-5554 shell settings put secure show_ime_with_hard_keyboard 1 || exit $?
  iptv_ime_policy="$(adb -s emulator-5554 shell settings get secure show_ime_with_hard_keyboard)" || exit $?
  if [[ "$iptv_ime_policy" != 1 ]]; then
    echo 'Cloud software-keyboard policy was not applied.' >&2
    exit 2
  fi
  echo 'IPTV_CLOUD_IME_READY enabled=true'
fi
result=0
bash ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.diagnosticTimeoutSeconds=90 "${test_args[@]}" || result=$?
if [[ "$result" -eq 0 ]]; then
  python3 scripts/check_device_results.py --scope "$scope" --results-dir app/build/outputs/androidTest-results/connected/debug || result=$?
fi
if [[ "$result" -eq 0 ]]; then
  bash ./scripts/persistence-process-test.sh || result=$?
fi
mkdir -p app/build/reports/device-previews
adb pull /data/local/tmp/iptv-previews app/build/reports/device-previews/ || true
exit "$result"
