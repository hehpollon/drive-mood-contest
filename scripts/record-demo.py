#!/usr/bin/env python3
"""Record the real Pleos APK demo flow, retaining raw video and timestamped scenes.

Requires installed pleosDebug + androidTest APKs. Does not change vehicle signals,
clear app data, or change the device's display configuration.
"""
import argparse
import json
import os
import re
from pathlib import Path
import shlex
import subprocess
import time

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', default='emulator-5554')
parser.add_argument('--output', default=str(ROOT / '.tools/video-recordings' / time.strftime('%Y%m%d-%H%M%S')))
args = parser.parse_args()
out = Path(args.output).resolve()
out.mkdir(parents=True, exist_ok=True)
adb = str(Path(os.environ.get('ANDROID_HOME', ROOT / '.tools/android-sdk')) / 'platform-tools/adb')
package = 'io.github.hehpollon.drivemood'


def run(*command):
    return subprocess.check_output([adb, '-s', args.serial, *command], timeout=30).decode().strip()


def markers(text):
    result = []
    for block in text.split('INSTRUMENTATION_STATUS_CODE: 0'):
        fields = {}
        for line in block.splitlines():
            if line.startswith('INSTRUMENTATION_STATUS: '):
                key, _, value = line.removeprefix('INSTRUMENTATION_STATUS: ').partition('=')
                fields[key] = value
        if 'scene' in fields:
            result.append(fields)
    return result


if not args.serial.startswith('emulator-') or run('shell', 'getprop', 'ro.kernel.qemu') != '1':
    raise RuntimeError('Recording automation is restricted to an Android emulator.')
existing = subprocess.run([adb, '-s', args.serial, 'shell', 'pidof', 'screenrecord'], capture_output=True, text=True)
if existing.stdout.strip():
    raise RuntimeError('Another screen recording is running; leave it intact.')
if 'TripService' in run('shell', 'dumpsys', 'activity', 'services', package):
    raise RuntimeError('A trip service is running; finish the existing trip in the app before recording.')
user = run('shell', 'am', 'get-current-user')
remote = '/data/local/tmp/drive-mood-demo-' + time.strftime('%Y%m%d-%H%M%S') + '.mp4'
remote_log = remote + '.log'
process = None
recorder_pid = None
recorder_process = None
version = re.search(r'\bversionName=(\S+)', run('shell', 'dumpsys', 'package', package))
metadata = {'serial': args.serial, 'package': package, 'app_version': version.group(1) if version else None,
            'remote': remote, 'scenes': [], 'data_source': 'Built-in app demo; actual Pleos emulator APK rendering'}
try:
    with (out / 'instrumentation.txt').open('w') as log:
        process = subprocess.Popen([adb, '-s', args.serial, 'shell', 'am', 'instrument', '--user', 'current', '-w', '-e', 'action', 'video',
                                    package + '.test/' + package + '.verification.RuntimeChecks'], stdout=log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 225
        announced = set()
        while process.poll() is None:
            if time.monotonic() > deadline:
                raise TimeoutError('Demo recording exceeded 225 seconds.')
            current = (out / 'instrumentation.txt').read_text()
            scenes = markers(current)
            metadata['scenes'] = scenes
            for scene in scenes:
                if scene['scene'] not in announced:
                    print(scene['scene'], scene.get('rhythm'), flush=True)
                    announced.add(scene['scene'])
            if any(scene['scene'] == 'ready' for scene in scenes) and recorder_pid is None:
                metadata['capture_start_ms'] = round(float(run('shell', 'cat', '/proc/uptime').split()[0]) * 1000)
                shell_command = 'echo $$; exec screenrecord --size 2560x1440 --bit-rate 12M --time-limit 180 ' + shlex.quote(remote)
                shell_command += ' >' + shlex.quote(remote_log) + ' 2>&1'
                recorder_process = subprocess.Popen([adb, '-s', args.serial, 'shell', shell_command],
                                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                recorder_pid = int(recorder_process.stdout.readline().strip())
                metadata['recorder_pid'] = recorder_pid
                time.sleep(1)
                run('shell', 'run-as', package, '--user', user, 'touch', 'cache/demo-video-start')
            (out / 'scenes.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
            time.sleep(.25)
    metadata['scenes'] = markers((out / 'instrumentation.txt').read_text())
    if 'INSTRUMENTATION_RESULT: result=PASS' not in (out / 'instrumentation.txt').read_text():
        raise RuntimeError('Demo flow failed; inspect instrumentation.txt before using footage.')
    metadata['result'] = 'PASS'
finally:
    if recorder_pid is not None:
        subprocess.run([adb, '-s', args.serial, 'shell', 'kill', '-2', str(recorder_pid)], capture_output=True)
        recorder_process.wait(timeout=15)
        subprocess.run([adb, '-s', args.serial, 'pull', remote, str(out / 'drive-mood-raw.mp4')], check=True)
        subprocess.run([adb, '-s', args.serial, 'pull', remote_log, str(out / 'screenrecord.txt')], check=True)
        run('shell', 'rm', '-f', remote, remote_log)
    if process is not None and process.poll() is None:
        process.terminate()
        process.wait(timeout=10)
        subprocess.run([adb, '-s', args.serial, 'shell', 'am', 'force-stop', '--user', 'current', package], check=True)
    (out / 'scenes.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
print('Recording saved:', out, flush=True)
