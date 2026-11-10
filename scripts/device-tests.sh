#!/usr/bin/env bash
# Preserve the Gradle result while collecting synthetic previews before emulator shutdown.
set -u
scope="${IPTV_TEST_SCOPE:-full}"
if [[ "$scope" != full && "${GITHUB_EVENT_NAME:-}" != workflow_dispatch ]]; then
  echo 'Focused test scopes are diagnostic workflow_dispatch runs only.' >&2
  exit 2
fi
classes=''
case "$scope" in
  full) ;;
  pip) classes='com.fireamun.iptv.prototype.PlayerPictureInPictureTest' ;;
  tracks) classes='com.fireamun.iptv.prototype.PlayerTracksTest' ;;
  player) classes='com.fireamun.iptv.prototype.PlayerTracksTest;com.fireamun.iptv.prototype.PlayerPictureInPictureTest;com.fireamun.iptv.prototype.PlaybackCheckpointTest' ;;
  forms) classes='com.fireamun.iptv.prototype.CategoryPickerFlowTest;com.fireamun.iptv.prototype.UrlVisibilityTest;com.fireamun.iptv.prototype.XtreamFlowTest' ;;
  *) echo 'Unknown diagnostic test scope.' >&2; exit 2 ;;
esac
printf 'IPTV_TEST_SCOPE=%s\n' "$scope"
test_args=()
if [[ -n "$classes" ]]; then
  # AGP 9.4 uses unescaped commas between runner arguments. Decode this list
  # inside ManualFixtureRunner, after that transport, before AndroidJUnitRunner.
  test_args+=("-Pandroid.testInstrumentationRunnerArguments.diagnosticClasses=$classes")
fi
if [[ "${GITHUB_ACTIONS:-}" == true ]]; then
  source ./scripts/device-diagnostics.sh || exit $?
  trap 'finish_device_diagnostics "$?"' EXIT
  start_device_diagnostics || exit $?
fi
result=0
bash ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.diagnosticTimeoutSeconds=90 "${test_args[@]}" || result=$?
if [[ "$result" -eq 0 ]]; then
  bash ./scripts/persistence-process-test.sh || result=$?
fi
mkdir -p app/build/reports/device-previews
adb pull /data/local/tmp/iptv-previews app/build/reports/device-previews/ || true
exit "$result"
