#!/usr/bin/env python3
"""Run the synthetic capacity probe on an explicitly disposable review emulator."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys

EXPECTED_PHASES = {
    'parse': ['fixture_generate', 'parse', 'parse_assert'],
    'import': ['fixture_generate', 'store_open', 'empty_assert', 'parse', 'save', 'saved_session', 'user_state_seed', 'database_metrics'],
    'refresh': ['fixture_generate', 'store_open', 'before_refresh', 'parse', 'refresh_save', 'refresh_assert', 'database_metrics'],
    'open': ['store_open', 'open_session', 'first_page', 'deep_page', 'categories', 'latin_selective_search',
             'arabic_selective_search', 'latin_broad_search', 'arabic_broad_search', 'favourites_and_history', 'database_metrics'],
    'clear': ['clear'],
    'cancel': ['fixture_generate', 'cancel_parse', 'cancel_assert'],
    'xtream': ['xtream_fixture_generate', 'xtream_load', 'xtream_assert', 'store_open', 'empty_assert',
               'xtream_save', 'xtream_saved_session', 'xtream_user_state_seed', 'xtream_refresh_save',
               'xtream_refresh_assert', 'database_metrics'],
    'ui_seed': ['fixture_generate', 'parse', 'ui_seed_clear', 'ui_seed_save', 'ui_seed_assert'],
}

def require(condition, message):
    if not condition:
        raise SystemExit(message)

def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--mode', required=True, choices=tuple(EXPECTED_PHASES))
parser.add_argument('--size', type=int, required=True, choices=(100_000, 200_000, 300_000))
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--serial', default='emulator-5554')
parser.add_argument('--timeout', type=int, default=600)
args = parser.parse_args()
if not 1 <= args.timeout <= 1200:
    parser.error('timeout must be between 1 and 1200 seconds')
args.output.parent.mkdir(parents=True, exist_ok=True)
result_path = args.output.with_suffix('.json')
require(result_path != args.output, 'Raw output must not have a .json suffix')
result_path.write_text(json.dumps({'status': 'running', 'mode': args.mode, 'size': args.size}) + '\n', encoding='utf-8')
adb = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'
base = [str(adb), '-s', args.serial]
devices = subprocess.check_output([str(adb), 'devices'], text=True)
online = re.findall(r'^(\S+)\s+device$', devices, re.M)
if online != [args.serial] or not args.serial.startswith('emulator-'):
    raise SystemExit('Exactly one disposable emulator must be connected')
avd = subprocess.check_output(base + ['emu', 'avd', 'name'], text=True).splitlines()[0].strip()
if avd not in ('Iptv_UiReview_TV_API_36', 'Iptv_UiReview_API_34'):
    raise SystemExit('Refusing capacity probe on a non-disposable AVD')
if subprocess.check_output(base + ['shell', 'getprop', 'sys.boot_completed'], text=True).strip() != '1':
    raise SystemExit('Review emulator is not ready')
root = Path(__file__).resolve().parent.parent
apk_paths = {'app': root / 'app/build/outputs/apk/debug/app-debug.apk',
             'test': root / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'}
apk_hashes = {}
for kind, apk in apk_paths.items():
    local_digest = sha256(apk)
    package = 'com.fireamun.iptv.prototype' + ('.test' if kind == 'test' else '')
    installed = subprocess.check_output(base + ['shell', 'pm', 'path', package], text=True).strip().splitlines()
    require(len(installed) == 1 and installed[0].startswith('package:'), 'Expected one installed APK per package')
    installed_path = installed[0].removeprefix('package:')
    require(bool(re.fullmatch(r'/data/app/[A-Za-z0-9_=/+.~\-]+\.apk', installed_path)), 'Unexpected installed APK path')
    installed_digest = subprocess.check_output(base + ['shell', 'sha256sum', installed_path], text=True).split()[0]
    require(local_digest == installed_digest, 'Installed APK does not match the measured build')
    apk_hashes[kind] = local_digest
source_files = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '--', 'app', 'core', 'ui', 'gradle'], cwd=root, text=True).splitlines()
source_hashes = {p: sha256(root / p) for p in source_files if (root / p).is_file()}
source_manifest = args.output.with_suffix('.sources.json')
source_manifest.write_text(json.dumps(source_hashes, indent=2) + '\n', encoding='utf-8')
provenance = {'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
              'sourceManifestSha256': sha256(source_manifest), 'apkSha256': apk_hashes}
result_path.write_text(json.dumps({'status': 'running', 'mode': args.mode, 'size': args.size,
                                   'provenance': provenance}, indent=2) + '\n', encoding='utf-8')
subprocess.run(base + ['shell', 'am', 'force-stop', 'com.fireamun.iptv.prototype'], check=True)
command = base + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'capacityProbe', args.mode,
                  '-e', 'capacitySize', str(args.size),
                  'com.fireamun.iptv.prototype.test/com.fireamun.iptv.prototype.ManualFixtureRunner']
try:
    with args.output.open('w', encoding='utf-8') as out:
        run = subprocess.run(command, stdout=out, stderr=subprocess.STDOUT, timeout=args.timeout)
except subprocess.TimeoutExpired:
    subprocess.run(base + ['shell', 'am', 'force-stop', 'com.fireamun.iptv.prototype'], check=True)
    raise SystemExit('Capacity probe timed out; raw output retained')
raw = args.output.read_text(encoding='utf-8')
events = [json.loads(m.group(1)) for m in re.finditer(r'IPTV_CAPACITY (\{[^\n]+\})', raw)]
require(bool(events), 'No capacity probe evidence')
require(all(e['mode'] == args.mode and e['size'] == args.size for e in events), 'Probe arguments mismatch')
require(len({e['pid'] for e in events}) == 1 and events[0]['pid'] > 0, 'Probe changed process unexpectedly')
require(run.returncode == 0 and re.search(r'^INSTRUMENTATION_CODE: -1\s*$', raw, re.M), 'Instrumentation did not finish successfully')
require(events[-1]['event'] == 'complete' and events[-1]['success'] and not any(e['event'] == 'failed' for e in events), 'Incomplete/failed probe')
phases = [e for e in events if e['event'] == 'phase_end']
require([e['phase'] for e in phases] == EXPECTED_PHASES[args.mode], 'Wrong/incomplete measurement phases')
for e in phases:
    require(e['success'] and e['samples'] > 0 and math.isfinite(e['elapsedMs']) and e['elapsedMs'] >= 0, 'Invalid phase result')
    require(0 < e['peakJavaHeapBytes'] <= e['maxJavaHeapBytes'] and e['peakNativeHeapBytes'] >= 0, 'Invalid heap sample')
require(len([e for e in events if e['event'] == 'start']) == 1, 'Missing/duplicate manifest')
result = {'status': 'passed', 'avd': avd, 'mode': args.mode, 'size': args.size, 'pid': events[-1]['pid'],
          'provenance': provenance, 'events': events, 'phases': phases}
result_path.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'status': 'passed', 'mode': args.mode, 'size': args.size, 'pid': result['pid'], 'phases': phases}))
