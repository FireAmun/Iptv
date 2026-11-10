#!/usr/bin/env bash
# Capture only: sourced by the disposable Actions device job, which owns EXIT.
# Never clear logs, dismiss an ANR, change settings, or request adb root.

_iptv_diagnostics_allowed() {
  [[ "${GITHUB_ACTIONS:-}" == true ]] || return 1
  case "${GITHUB_EVENT_NAME:-}" in
    push|pull_request|workflow_dispatch) return 0 ;;
    *) return 1 ;;
  esac
}

_iptv_diagnostic_capture() {
  local name="$1" bound="$2" status=0
  shift 2
  timeout -k 2s "$bound" "$@" > "$_IPTV_DIAGNOSTICS_DIR/$name.txt" 2>&1 || status=$?
  printf '%s exit=%s\n' "$name" "$status" >> "$_IPTV_DIAGNOSTICS_DIR/collection-status.txt" || true
  _IPTV_DIAGNOSTIC_RESULT="$status"
  return 0
}

_iptv_diagnostic_snapshot() {
  local phase="$1"
  _iptv_diagnostic_capture "$phase-lastanr" 10s adb shell dumpsys activity lastanr
  _iptv_diagnostic_capture "$phase-window" 10s adb shell dumpsys window
  _iptv_diagnostic_capture "$phase-input" 10s adb shell dumpsys input
  _iptv_diagnostic_capture "$phase-cpuinfo" 10s adb shell dumpsys cpuinfo
  _iptv_diagnostic_capture "$phase-memory" 10s adb shell '
    date -u
    getprop ro.build.fingerprint
    getprop sys.boot_completed
    cat /proc/uptime /proc/stat /proc/loadavg /proc/meminfo
    for name in cpu memory io; do
      echo "pressure/$name"
      cat "/proc/pressure/$name"
    done
  '
}

start_device_diagnostics() {
  if ! _iptv_diagnostics_allowed; then
    echo 'Device diagnostics require a disposable GitHub Actions push/PR/dispatch job.' >&2
    return 2
  fi
  if ! command -v timeout >/dev/null; then
    echo 'Device diagnostics require bounded command execution (timeout).' >&2
    return 2
  fi
  _IPTV_DIAGNOSTICS_DIR=app/build/reports/device-previews/system-diagnostics
  _IPTV_DIAGNOSTICS_STARTED=0
  if ! mkdir -p "$_IPTV_DIAGNOSTICS_DIR"; then
    echo 'Cannot create device diagnostic artifacts; continuing original test result.' >&2
    return 0
  fi
  _IPTV_DIAGNOSTICS_STARTED=1
  # Streaming retained buffers before Gradle also prevents later ring-buffer loss.
  timeout -k 2s 40m adb logcat -b all -v threadtime > "$_IPTV_DIAGNOSTICS_DIR/logcat-all.txt" 2>&1 &
  _IPTV_DIAGNOSTICS_LOGCAT_PID=$!
  _iptv_diagnostic_capture host 10s bash -c 'date -u; getconf _NPROCESSORS_ONLN; cat /proc/meminfo /proc/loadavg'
  _iptv_diagnostic_snapshot before-tests
  return 0
}

finish_device_diagnostics() {
  local original_result="$1" stream_status=0
  if [[ "${_IPTV_DIAGNOSTICS_STARTED:-0}" != 1 ]]; then
    return "$original_result"
  fi
  if _iptv_diagnostics_allowed; then
    _iptv_diagnostic_snapshot after-tests
    # Root-readable traces only; never restart adb or escalate guest privileges.
    _iptv_diagnostic_capture anr-readable 10s adb shell test -r /data/anr
    if [[ "$_IPTV_DIAGNOSTIC_RESULT" == 0 ]]; then
      _iptv_diagnostic_capture anr-pull 10s adb pull /data/anr "$_IPTV_DIAGNOSTICS_DIR/anr"
    fi
    if [[ "$original_result" != 0 ]]; then
      # TERM at58s and forced termination2s later cap this expensive operation60s.
      _iptv_diagnostic_capture bugreport 58s adb bugreport "$_IPTV_DIAGNOSTICS_DIR/bugreport.zip"
    fi
  fi
  kill "$_IPTV_DIAGNOSTICS_LOGCAT_PID" 2>/dev/null || true
  wait "$_IPTV_DIAGNOSTICS_LOGCAT_PID" 2>/dev/null || stream_status=$?
  printf 'logcat-stream exit=%s\n' "$stream_status" >> "$_IPTV_DIAGNOSTICS_DIR/collection-status.txt" || true
  _IPTV_DIAGNOSTICS_STARTED=0
  return "$original_result"
}
