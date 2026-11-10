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
  player) classes='com.fireamun.iptv.prototype.PlayerTracksTest,com.fireamun.iptv.prototype.PlayerPictureInPictureTest,com.fireamun.iptv.prototype.PlaybackCheckpointTest' ;;
  forms) classes='com.fireamun.iptv.prototype.CategoryPickerFlowTest,com.fireamun.iptv.prototype.UrlVisibilityTest,com.fireamun.iptv.prototype.XtreamFlowTest' ;;
  *) echo 'Unknown diagnostic test scope.' >&2; exit 2 ;;
esac
printf 'IPTV_TEST_SCOPE=%s\n' "$scope"
test_args=()
if [[ -n "$classes" ]]; then
  test_args+=("-Pandroid.testInstrumentationRunnerArguments.class=$classes")
fi
result=0
bash ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.diagnosticTimeoutSeconds=90 "${test_args[@]}" || result=$?
if [[ "$result" -eq 0 ]]; then
  bash ./scripts/persistence-process-test.sh || result=$?
fi
mkdir -p app/build/reports/device-previews
adb pull /data/local/tmp/iptv-previews app/build/reports/device-previews/ || true
exit "$result"
