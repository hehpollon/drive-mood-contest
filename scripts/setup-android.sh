#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME" "$DRIVE_MOOD_ROOT/.tools/logs"

if [[ ! -x "$JAVA_HOME/bin/java" || ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
    printf '%s\n' '먼저 python3 scripts/install-toolchain.py --with-studio 를 실행하세요.' >&2
    exit 1
fi

# All settings and accepted SDK licenses stay within this checkout's toolchain.
python3 - "$ANDROID_HOME" "$DRIVE_MOOD_ROOT/.tools/logs" <<'PY'
from pathlib import Path
import subprocess
import sys

sdk_root, logs = map(Path, sys.argv[1:])
sdkmanager = str(sdk_root / 'cmdline-tools/latest/bin/sdkmanager')
base = [sdkmanager, f'--sdk_root={sdk_root}']

def run(args, name, answers=None):
    path = logs / name
    with path.open('w') as log:
        result = subprocess.run(base + args, input=answers, text=True, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        print(path.read_text()[-10000:], file=sys.stderr)
        raise SystemExit(result.returncode)

print('Accepting Android SDK development licenses...', flush=True)
run(['--licenses'], 'sdk-licenses.log', 'y\n' * 100)
print('Installing Android API 36, build tools, platform tools, and emulator...', flush=True)
run(['--install', 'platforms;android-36', 'build-tools;35.0.0', 'platform-tools', 'emulator'], 'sdk-install.log', 'y\n' * 100)
print('Android SDK packages installed.', flush=True)
PY
