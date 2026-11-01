#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
adb "$@" forward tcp:8088 tcp:8088
printf '%s\n' 'Pleos Virtual Console: http://127.0.0.1:8088/'
