#!/usr/bin/env bash
# Disposable CI emulator only: reinstall synthetic test APKs after connected-test cleanup.
set -euo pipefail
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
probe() {
  local output
  output="$(timeout 90s adb shell am instrument -w -e persistenceProbe "$1" com.fireamun.amariptv.test/com.fireamun.iptv.prototype.ManualFixtureRunner)" || return $?
  printf '%s\n' "$output"
  [[ "$output" == *IPTV_PERSISTENCE_PROBE_OK* ]] &&
    [[ "$output" == *"IPTV_PROGRESS_PROBE_OK stage=$1"* ]]
}
seed="$(probe seed)"
printf '%s\n' "$seed"
seed_pid="$(printf '%s\n' "$seed" | sed -n 's/.*IPTV_PERSISTENCE_PROBE_OK pid=\([0-9][0-9]*\).*/\1/p')"
adb shell am force-stop com.fireamun.amariptv
verified="$(probe verify)"
printf '%s\n' "$verified"
verified_pid="$(printf '%s\n' "$verified" | sed -n 's/.*IPTV_PERSISTENCE_PROBE_OK pid=\([0-9][0-9]*\).*/\1/p')"
if [[ -z "$seed_pid" || -z "$verified_pid" || "$seed_pid" == "$verified_pid" ]]; then
  echo 'Cold-process persistence must use different target process IDs.' >&2
  exit 1
fi
probe clear
printf 'Cold-process Room/Keystore persistence passed: %s -> %s\n' "$seed_pid" "$verified_pid"
