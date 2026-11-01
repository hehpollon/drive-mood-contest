#!/usr/bin/env bash
# Source this file from Bash: source scripts/env.sh
DRIVE_MOOD_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-$DRIVE_MOOD_ROOT/.tools/jdk}"
export ANDROID_HOME="${ANDROID_HOME:-$DRIVE_MOOD_ROOT/.tools/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$DRIVE_MOOD_ROOT/.tools/gradle-user-home}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$DRIVE_MOOD_ROOT/.tools/android-user-home}"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$ANDROID_USER_HOME/avd}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
