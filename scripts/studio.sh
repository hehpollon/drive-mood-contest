#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
exec "$DRIVE_MOOD_ROOT/.tools/android-studio/bin/studio.sh" "$DRIVE_MOOD_ROOT" "$@"
