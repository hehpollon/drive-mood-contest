#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$DRIVE_MOOD_ROOT"
exec ./gradlew "$@"
