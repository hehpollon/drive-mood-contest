#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME" "$DRIVE_MOOD_ROOT/.tools/logs"

python3 - "$ANDROID_USER_HOME" <<'PY'
from pathlib import Path
import sys

config = Path(sys.argv[1]) / 'repositories.cfg'
properties = {}
if config.exists():
    for line in config.read_text().splitlines():
        if '=' in line and not line.startswith('#'):
            key, value = line.split('=', 1)
            properties[key.strip()] = value.strip()
url = 'https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml'
count = int(properties.get('count', '0'))
if url not in properties.values():
    properties[f'disp{count:02}'] = 'Pleos Connect System Image'
    properties[f'src{count:02}'] = url
    properties['count'] = str(count + 1)
config.write_text('\n'.join(f'{key}={value}' for key, value in properties.items()) + '\n')
PY

python3 - "$ANDROID_HOME" "$DRIVE_MOOD_ROOT/.tools/logs" <<'PY'
from pathlib import Path
import subprocess
import sys

sdk_root, logs = map(Path, sys.argv[1:])
base = [str(sdk_root / 'cmdline-tools/latest/bin/sdkmanager'), f'--sdk_root={sdk_root}']
for args, name in [(['--licenses'], 'pleos-licenses.log'), (['--install', 'system-images;android-34;pleos_car_emulator;x86_64'], 'pleos-image-install.log')]:
    print('Pleos emulator: ' + ' '.join(args), flush=True)
    with (logs / name).open('w') as log:
        result = subprocess.run(base + args, input='y\n' * 100, text=True, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        print((logs / name).read_text()[-8000:], file=sys.stderr)
        raise SystemExit(result.returncode)
PY

if [[ ! -f "$ANDROID_AVD_HOME/DriveMood_Pleos.ini" ]]; then
    printf 'no\n' | avdmanager create avd --name DriveMood_Pleos \
        --package 'system-images;android-34;pleos_car_emulator;x86_64' \
        --device Pleos_Connect_v2_0
fi

python3 - "$ANDROID_AVD_HOME/DriveMood_Pleos.avd/config.ini" \
    "$ANDROID_HOME/system-images/android-34/pleos_car_emulator/x86_64/devices.xml" <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

path = Path(sys.argv[1])
namespace = {'d': 'http://schemas.android.com/sdk/devices/5'}
device = ET.parse(sys.argv[2]).getroot().find('d:device', namespace)
profile = device.findtext('d:id', namespaces=namespace)
screen = device.find('d:hardware/d:screen', namespace)
properties = {}
for line in path.read_text().splitlines():
    if '=' in line:
        key, value = line.split('=', 1)
        properties[key.strip()] = value.strip()
if properties.get('hw.device.name') != profile:
    properties.pop('hw.device.hash2', None)
properties.update({
    'hw.device.name': profile,
    'hw.device.manufacturer': device.findtext('d:manufacturer', namespaces=namespace),
    'hw.lcd.width': screen.findtext('d:dimensions/d:x-dimension', namespaces=namespace),
    'hw.lcd.height': screen.findtext('d:dimensions/d:y-dimension', namespaces=namespace),
    'hw.lcd.density': str(int(float(screen.findtext('d:xdpi', namespaces=namespace)))),
    'hw.ramSize': '4096',
    'hw.cpu.ncore': '4',
    'hw.gpu.enabled': 'yes',
    'hw.gpu.mode': 'host',
    'fastboot.forceColdBoot': 'yes',
    'fastboot.forceFastBoot': 'no',
})
path.write_text('\n'.join(f'{key}={value}' for key, value in properties.items()) + '\n')
PY
printf '%s\n' 'Pleos AVD ready: DriveMood_Pleos'
emulator -accel-check || true
