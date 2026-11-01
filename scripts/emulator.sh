#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
if [[ "${1:-}" == "--headless" ]]; then
    shift
    set -- -no-window -no-audio -gpu swiftshader "$@"
fi
exec emulator -avd DriveMood_Pleos -no-snapshot "$@"
