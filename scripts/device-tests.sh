#!/usr/bin/env bash
# Preserve the Gradle result while collecting synthetic previews before emulator shutdown.
set -u
result=0
bash ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.diagnosticTimeoutSeconds=90 || result=$?
if [[ "$result" -eq 0 ]]; then
  bash ./scripts/persistence-process-test.sh || result=$?
fi
mkdir -p app/build/reports/device-previews
adb pull /data/local/tmp/iptv-previews app/build/reports/device-previews/ || true
exit "$result"
