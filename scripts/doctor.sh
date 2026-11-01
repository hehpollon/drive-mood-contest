#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"

printf '프로젝트: %s\n' "$DRIVE_MOOD_ROOT"
printf 'Java: %s\n' "$JAVA_HOME"
"$JAVA_HOME/bin/java" -version
printf '\nAndroid SDK: %s\n' "$ANDROID_HOME"
adb version
printf '\n설치된 Android 플랫폼:\n'
for platform in "$ANDROID_HOME"/platforms/android-*; do
    [[ -d "$platform" ]] && printf '%s\n' "${platform##*/}"
done
printf '\n설치된 AVD:\n'
emulator -list-avds
printf '\n에뮬레이터 가속:\n'
if ! emulator -accel-check; then
    if [[ ! -e /dev/kvm ]]; then
        printf '\n현재 실행 환경에서 /dev/kvm을 찾을 수 없습니다. 에이전트 샌드박스라면 실제 호스트에서 다시 검사하세요. 호스트에서도 없다면 BIOS/UEFI 가상화 설정과 KVM 모듈을 확인하세요.\n' >&2
    elif [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
        printf '\n현재 프로세스에 KVM 접근 권한이 없습니다. kvm 그룹 가입 여부를 확인하고, 그룹을 추가했다면 새 로그인 세션에서 실행하세요.\n' >&2
    else
        printf '\nKVM 장치는 보이지만 에뮬레이터 가속 검사가 실패했습니다. 위 진단 결과를 확인하세요.\n' >&2
    fi
    exit 1
fi
