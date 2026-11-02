# Drive Mood 개발 환경

Linux x86_64에서 개발 도구를 프로젝트의 `.tools/` 안에 설치한다. 시스템 Java나 셸 설정 파일을 수정하지 않는다. `.tools/`, 빌드 산출물, `local.properties`는 Git에서 제외한다.

## 1. 최초 설치

필수 명령: Bash, Python 3.12 이상, curl, unzip. 도구 및 의존성 다운로드에는 인터넷 연결이 필요하다.

```bash
python3 scripts/install-toolchain.py --with-studio
./scripts/setup-android.sh
./scripts/setup-pleos-emulator.sh
```

설치 스크립트는 Android SDK와 Pleos 시스템 이미지 설치에 필요한 개발용 라이선스를 수락한다. 실행 전에 해당 라이선스 내용을 확인할 수 있다.
- [Android SDK 다운로드 및 라이선스](https://developer.android.com/studio)
- [Pleos 시스템 이미지 및 라이선스](https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml)

설치 대상:

| 도구 | 위치 / 버전 |
| --- | --- |
| Amazon Corretto Java 21 | `.tools/jdk/` |
| Android Studio Rabbit 1, 2026.2.1.8 | `.tools/android-studio/` |
| Android SDK | `.tools/android-sdk/` |
| Android 플랫폼 / 빌드 도구 | API 36 / 35.0.0 |
| Gradle | Wrapper 8.13, 다운로드 체크섬 검증 |
| Android Gradle Plugin / Kotlin | 8.11.1 / 2.2.21 |
| Compose BOM | 2025.10.00 |
| Pleos Vehicle / NaviHelper | 2.3.2 / 3.0.5 |
| Pleos 시스템 이미지 | 2.1.0, Android API 34, x86_64 |

Java, Android 도구, IDE, Wrapper JAR는 배포 서버에서 받은 SHA-256과 비교한다. SDK Manager로 설치하는 패키지는 SDK Manager가 검증한다. Java 21은 Gradle 실행에 사용하며 앱의 Java/Kotlin 바이트코드 타깃은 17이다.

Pleos 시스템 이미지는 압축 파일만 약 4.1 GB이며, 설치·실행에는 추가 디스크 공간이 필요하다.

## 2. Android Studio 열기

```bash
./scripts/studio.sh
```

프로젝트 루트 `pleosApp`을 연다. Gradle Sync가 끝나면 Build Variants에서 `demoDebug` 또는 `pleosDebug`를 선택한다.

- Gradle JDK: 프로젝트의 `.tools/jdk` (Java 21). `.gradle/config.properties`에 경로를 설정한다.
- Android SDK: 프로젝트의 `.tools/android-sdk`. `local.properties`에 경로를 설정한다.
- 앱 패키지명: `io.github.hehpollon.drivemood`
- 데모 패키지명: `io.github.hehpollon.drivemood.demo`

패키지명은 `app/build.gradle.kts`의 `defaultConfig.applicationId`에서 변경한다. Pleos 프로젝트 화면의 프로젝트 ID, 클라이언트 ID와는 별개다.

## 3. 빌드 및 설치

일반 Android 기기에서 확인할 데모:

```bash
./scripts/gradle.sh :app:assembleDemoDebug
```

산출물: `app/build/outputs/apk/demo/debug/app-demo-debug.apk`

Pleos Connect 에뮬레이터용:

```bash
./scripts/gradle.sh :app:assemblePleosDebug
```

산출물: `app/build/outputs/apk/pleos/debug/app-pleos-debug.apk`

에뮬레이터가 켜진 뒤 설치 및 실행:

```bash
source scripts/env.sh
adb devices
adb install -r app/build/outputs/apk/pleos/debug/app-pleos-debug.apk
adb shell am start -n io.github.hehpollon.drivemood/.MainActivity
```

일반 Android 기기에는 데모 APK를 설치하고 `io.github.hehpollon.drivemood.demo/io.github.hehpollon.drivemood.MainActivity`를 실행한다. 기기가 여러 대 연결되면 `adb -s <serial>`로 선택한다.

이 APK들은 개발용 debug 서명이다. 주요 기능은 구현되어 있으며 공모전 제출용 release 서명과 실차 검증은 별도 단계다.

## 4. Pleos 에뮬레이터와 차량 신호

```bash
./scripts/emulator.sh
```

AVD 이름은 `DriveMood_Pleos`이다. 설치된 시스템 이미지의 `devices.xml`에 정의된 **Pleos Connect v2.1.0** 프로필(`Pleos_Connect_v2_0`)을 사용한다. 화면은 **2560×1440, 160dpi**이며, 설치 스크립트가 해당 XML에서 화면 설정을 읽어 적용한다. 메모리 4 GB, CPU 4개, GPU host, Cold boot를 사용한다. 공식 안내에 따라 Quick boot를 사용하지 않는다. 실행 스크립트의 `-no-snapshot`도 Cold boot를 유지한다.

데스크톱 화면 세션 없이 실행하는 경우 다음 명령으로 창 없이 부팅한다. 이 모드의 그래픽 렌더링은 SwiftShader를 사용하며 CPU 가속에는 KVM을 사용한다.

```bash
./scripts/emulator.sh --headless
```

차량 신호를 바꾸는 Virtual Console:

```bash
./scripts/vehicle-console.sh
```

브라우저에서 `http://127.0.0.1:8088/`을 연다. Motion 항목의 조향각·브레이크 상태를 바꿔 앱의 값이 갱신되는지 확인한다. 여러 기기가 연결되면 `./scripts/vehicle-console.sh -s <serial>`을 사용한다.

이 주소는 에뮬레이터가 실행되는 PC의 로컬 주소다. 콘솔을 연 직후에는 차량 데이터 브리지가 초기화될 때까지 잠시 기다린다.

현재 Pleos 빌드는 다음을 수행한다.
- Vehicle SDK의 읽기 지원 범위 확인, 조향·브레이크 구독 및 1초 간격 재조회
- 기어·방향지시등, 실내·설정 온도, 앰비언트 밝기·색상, 좌석 점유 조회
- AAOS 속도와 CarUxRestrictions 조회, 실제 P 상태 확인 및 주행 중 조작 제한
- NaviHelper 목적지·경로·TBT 구독, 기본 내비 전면 요청, 도착 이벤트 처리
- 사용자가 기록을 시작하면 포그라운드 서비스로 화면 전환 후에도 수집 유지
- 기록 중이 아니면서 화면을 나가면 SDK 구독·연결 해제
- 음성 엔진이 제공되는 경우 한국어 안내와 일시적 오디오 포커스 사용

첫 기록 시작 때 차량 속도와 알림 권한을 요청한다. 이 Pleos 이미지에서는 `CAR_SPEED` 권한이 위치 관련 권한 창으로 표시된다. 거절해도 다른 사용 가능한 SDK 값은 계속 표시하지만 속도 기반 저속 제외는 적용할 수 없다. `연결 → 권한 확인`에서 다시 요청할 수 있다.

조향 값은 SDK에서 받은 도 단위 값이다. 브레이크는 밟음 여부(Boolean)이므로 급제동 강도로 해석하지 않는다. 운전 시간 API 값은 분 단위다. 실제 차량의 조향 축·범위는 별도로 보정한다.

현재 에뮬레이터의 `getTripCurrentDrivingTime`는 `ERROR_NOT_SUPPORTED_PROPERTY`를 반환한다. 이때 임의의 차량 운전 시간 대신 **앱 기록 시간**과 앱이 관찰한 주행 시간을 구분해 표시한다.

NaviHelper 서비스 연결과 실제 경로 안내는 구분한다. 목적지가 비어 있으면 기본 안내를 보여주고, 실제 내비 제공자가 경로·TBT·도착 이벤트를 반환할 때만 해당 값을 갱신한다. 경로 취소는 도착으로 저장하지 않는다. 현재·설정 온도는 각각의 지원 좌석 구역을 확인하여 조회한다. 미지원 정보는 `미제공`으로 표시한다.

`여정 시연`은 같은 리듬 엔진에 가상 신호와 경로를 넣는다. 시연 배지를 표시하고 저장 시 시연임을 유지하며 최근 7일 합계에서 제외한다. Pleos 빌드에서는 실제 차량이 주차 중일 때만 시연할 수 있다. 일반 Android용 `demoDebug`는 주차 상태의 가상 차량 연결을 제공한다.

호흡은 실제 P 상태·주행 제한 해제·앱 활성 상태에서만 가능하다. 화면 전환 또는 주차 해제 시 중단하며, 앱을 나가도 진행 중인 주행 기록은 유지한다. 실제 주행 기록을 시작하면 주행 요약 레이아웃을 준비한다. 주행 후 주차가 확인되면 `MainActivity`의 주차 화면으로 돌아온다. 앱 아이콘과 기록 알림의 진입점도 `MainActivity`이며, 주차 상태에서는 홈으로 연결한다. 진입 `MainActivity`는 DO를 선언하며 주차 상태가 확인되지 않으면 요약만 표시한다. 호흡·리포트·설정은 실제 P 상태에서만 제공한다. 30초 완료 자체로 리듬 점수를 바꾸지 않는다.

리포트는 최근 60개를 앱 내부에 보관한다. 기록 중에는 15초마다 중간 저장하고, 프로세스 종료 후에는 마지막 저장분을 중단 기록으로 복원한다. 앱 데이터 삭제·앱 제거 시 기록도 삭제된다. 자동 클라우드 백업·기기 이전에서 제외한다.

## 5. 실행 조건 확인

```bash
./scripts/doctor.sh
```

Linux에서 Android 에뮬레이터 가속에는 `/dev/kvm` 접근이 필요하다. 이 장치가 없으면 BIOS/UEFI의 CPU 가상화 설정 및 KVM 커널 모듈 상태를 확인해야 한다. 장치가 있지만 접근할 수 없으면 KVM 장치의 그룹 권한을 확인한다. BIOS 설정 변경에는 재부팅이 필요할 수 있다.

이 PC에서는 `han` 계정을 `kvm` 그룹에 추가했다. 다른 설치에서 필요한 경우 Ubuntu 터미널에서 다음 명령으로 그룹을 추가한다.

```bash
sudo usermod -aG kvm han
```

기존 프로세스에는 변경된 보조 그룹이 즉시 적용되지 않을 수 있다. 새 로그인 세션에서 실행하거나, 관리자 인증 후 다음처럼 KVM 그룹을 적용해 확인할 수 있다.

```bash
sudo -u han -g kvm -- ./scripts/doctor.sh
sudo -u han -g kvm -- ./scripts/emulator.sh --headless
```

에이전트 샌드박스에서는 `/dev/kvm`이 숨겨질 수 있으므로 하드웨어 진단은 실제 호스트 환경에서 진행한다.

Pleos는 GPU 요구사항도 안내하고 있다. [공식 에뮬레이터 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)을 확인한다.

하드웨어 조건 확인과 AVD 생성은 실제 Android 부팅·앱 실행 검증과 구분한다. APK 빌드는 에뮬레이터 가속 없이도 가능하다.

## 6. Pleos 계정 정보

Vehicle State와 NaviHelper의 Maven 의존성 다운로드에는 클라이언트 시크릿을 설정하지 않는다. SDK 기능의 사용 가능 상태는 Pleos Playground의 해당 프로젝트에서 확인한다.

계정별 클라이언트 시크릿은 저장소나 앱 소스에 넣지 않는다. `secrets.properties`, `.env`, 서명 키 파일은 Git 제외 대상으로 설정했다. 향후 인증이 필요한 SDK를 추가할 때 별도로 설정한다.

프로젝트 정보는 Pleos Playground → Workspace → My Project → 해당 프로젝트에서 확인한다. 공식 문서에서는 Emulator 2.1.0 이후 CRN 입력이 요구되지 않는다고 안내하므로 초기 설치 스크립트에는 CRN 변경 명령을 넣지 않았다.

## 7. 검증 명령

빌드, 순수 도메인 테스트, Android 정적 검사:

```bash
./scripts/gradle.sh :app:assembleDemoDebug :app:assemblePleosDebug \
  :app:testDemoDebugUnitTest :app:testPleosDebugUnitTest \
  :app:lintDemoDebug :app:lintPleosDebug
```

실제 설치 APK의 전체 여정 계측 검증 (Pleos 에뮬레이터를 P·속도 0으로 두고 실행):

```bash
./scripts/gradle.sh :app:assemblePleosDebugAndroidTest
source scripts/env.sh
adb install -r app/build/outputs/apk/pleos/debug/app-pleos-debug.apk
adb install -r app/build/outputs/apk/androidTest/pleos/debug/app-pleos-debug-androidTest.apk
adb shell am instrument --user current -w -e action flow \
  io.github.hehpollon.drivemood.test/io.github.hehpollon.drivemood.verification.RuntimeChecks
```

결과의 `result=PASS`를 확인한다. 이 커스텀 계측 검사는 실제 앱·서비스·저장소를 사용하며 시연 기록을 기기에 남긴다. UI를 개편한 뒤에도 동일한 핵심 흐름을 검사한다. 일정 리듬, 큰 변화, 회전 제외, 데이터 누락, 주행 상태 호흡 차단, 앱 비활성화 시 가이드 중단, 백그라운드 신호 수집, 30초 호흡, 자동 도착 저장을 확인한다. 약 2분이 걸린다. 기기의 내비 제공자·실제 물리 차량 대신 시연 입력을 사용한다는 범위를 구분한다.

동일한 계측 검사는 `python3 scripts/verify-pleos-runtime.py --action flow`로 실행하고 PASS 여부를 자동 확인할 수도 있다.

공식 Virtual Console을 통한 실제 SDK 입력 검증은 `python3 scripts/verify-pleos-runtime.py --action live`로 실행한다. 이 명령은 Android 에뮬레이터임을 확인한 뒤에만 조향·브레이크·속도·기어를 변경한다. 시연 입력 대신 실제 SDK 수신값으로 일정·변화 리듬과 리포트를 확인하며, 실제 D 전환 시 가상 여정과 호흡이 중단되는지도 검사한다. 실제 기록 버튼을 누른 다음 주행 요약이 전면에 유지되고 주차 기능·스크롤이 없는 것도 확인한다. 콘솔 입력은 종료 시 원래 값으로 복원한다. 로그는 `.tools/logs/runtime-live.txt`에 저장된다. 모션 입력만 변경하고 복원한다.

앱 프로세스를 다시 시작한 뒤 저장 복원 확인:

```bash
adb shell am force-stop --user current io.github.hehpollon.drivemood
adb shell am instrument --user current -w -e action verify-persistence \
  io.github.hehpollon.drivemood.test/io.github.hehpollon.drivemood.verification.RuntimeChecks
```

차량용 화면 검사는 `python3 scripts/verify-pleos-runtime.py --action ui`로 실행한다. 실제 접근성 트리에서 버튼의 최소 112dp 영역, 주행 화면의 스크롤·주차 기능 부재, 480×800dp·960×1000dp 레이아웃과 진입 Activity의 DO 선언을 검사한다. 주간·야간 배경과 주행 화면의 실제 픽셀, 쉼 화면 진입·복귀도 확인한다. 긴 목적지·남은 시간·회전 안내는 UI 상태 복사본으로 렌더링하며 저장소·SDK에 경로를 주입하지 않는다. 일반 버튼 높이는 112dp, 주요 버튼은 128dp·글자 32sp다. 왼쪽 메뉴 높이는 넓은 화면에서 144dp이며 세로 공간에 따라 112/96dp로 조절한다. 화면 캡처는 에뮬레이터의 앱 캐시에 `car-ui-*.png`로 남긴다. 창 크기·야간 검사는 Compose만 바꾸며 시스템 해상도·배율·야간 설정은 바꾸지 않는다.

현재 UI 접근성 내용을 확인하려면 `-e action snapshot`을 사용한다. 다중 창에서는 기본 `uiautomator dump`가 다른 창을 선택하거나 지속 갱신 화면에서 유휴 대기를 실패할 수 있어 계측 도구는 전체 접근성 창을 읽는다.

로그와 스크린샷은 `.tools/logs/`, `.tools/screenshots/`에 저장한다. 빌드·정적 검사 보고서는 `app/build/reports/`에서 확인한다. 첫 실행에는 의존성 다운로드가 필요하며 이후 `--offline`을 사용할 수 있다.

### 이 PC의 검증 결과 (2026-10-29)

- `assembleDemoDebug`, `assemblePleosDebug`, `assemblePleosDebugAndroidTest` 성공
- 순수 도메인 테스트 22개씩 두 변형에서 통과 (0.4.1 총 실행 44개, 실패 0개)
- 차량용 UI 검사 48항목 통과 (0.4.1): 큰 터치 영역, 주행 화면의 스크롤·주차 기능 부재, 진입 Activity의 DO, 480dp·960dp 대응, 주간·야간 픽셀, 호흡 화면 진입·복귀, 긴 목적지·상황별 홈·분석 범위 표시
- 설치 APK 전체 시연 계측 검사 21항목 통과: 일정·변화·회전·누락·호흡·백그라운드·자동 도착
- 프로세스 재시작 후 리포트 복원 검사 2항목 통과
- Virtual Console을 통한 실제 SDK 입력 검사 18항목 통과: 조향·브레이크·속도·기어, 리포트 저장, 실제 D 전환 시 시연·호흡 중단
- 한국어 TTS 미설치 상태의 화면 안내 경로 확인. 실제 음성 재생은 한국어 엔진 설치 기기에서 별도 확인 필요
- 현재 내비 서비스 연결 및 빈 경로 처리 확인. 실제 내비 제공자의 목적지 설정·도착 이벤트는 제공자별 추가 검증 필요
- `lintDemoDebug`, `lintPleosDebug` 성공: 오류 0개. 경고는 도구·라이브러리 업데이트 및 코드 스타일 안내를 포함하며 상세 내용은 생성 보고서에서 확인
- Pleos debug APK의 서명 검증 성공, 패키지명·최소 API·조회 권한 확인
- Pleos 시스템 이미지 설치 및 `DriveMood_Pleos` AVD 생성 완료
- BIOS 버전 `1.O1`, 펌웨어 빌드 날짜 `2026-06-26` 확인
- CPU의 `svm` 기능 및 `kvm`, `kvm_amd` 모듈 활성화 확인
- `han` 계정의 KVM 접근 권한 확인, `KVM (version 12) is installed and usable` 진단 통과
- Headless + SwiftShader 모드에서 Pleos 부팅 완료 (약 16초)
- ADB 기기 `emulator-5554` 연결, Pleos APK 설치 및 앱 화면 실행 성공
- Virtual Console HTTP 연결 및 차량 신호 실시간 변경 검증 성공
- 조향각·브레이크 테스트 값을 원래 값으로 복구했으며 앱 프로세스의 치명적 오류 없음 확인
- 운전 시간 조회 미지원 시 안내 문구 표시 확인
- Pleos 전용 프로필의 해상도·배율 적용 후 재부팅 및 하단 시스템 바 배치 확인

확인한 입력과 SDK 수신 값:

| 콘솔 조향각 | SDK 조향각 | 콘솔 브레이크 | 앱 표시 |
| --- | --- | --- | --- |
| +12° | -12° | 25% | 밟음 |
| -8° | +8° | 0% | 해제 |

현재 SDK·에뮬레이터 조합에서는 콘솔 조향 입력과 SDK 수신 값의 부호가 반대다. 앱은 SDK가 반환한 값을 표시한다. 실제 차량의 좌우 방향 해석은 별도로 검증한다.

검증 산출물 (로컬 전용):
- 실행 화면: `.tools/screenshots/pleos-ready.png`
- 화면 프로필 수정 후 캡처: `.tools/screenshots/pleos-display-fixed.png`
- 차량용 UI 개편 화면: `.tools/screenshots/car-ui-home.png`, `car-ui-driving.png`, `car-ui-rest.png`, `car-ui-history.png`
- 좁은 화면 검사: `.tools/screenshots/car-ui-narrow.png`, `car-ui-narrow-driving.png`
- 실제 SDK 주행·변화 화면: `.tools/screenshots/car-ui-live-driving.png`, `car-ui-live-changed.png`
- 개편 전 최종 홈 화면: `.tools/screenshots/drive-mood-final-home.png`
- 최종 호흡 화면: `.tools/screenshots/drive-mood-breathing-final.png`
- 기록 목록·리포트: `.tools/screenshots/drive-mood-history-v2.png`, `.tools/screenshots/drive-mood-report-v2.png`
- 전체 시연·복원·SDK 검사: `.tools/logs/runtime-flow.txt`, `runtime-verify-persistence.txt`, `runtime-live.txt`
- 최종 빌드·단위 테스트·정적 검사: `.tools/logs/final-checks-v2.log`, `.tools/logs/final-ui-checks.log`
- 신호 변경 화면: `.tools/screenshots/pleos-signals.png`
- 신호 검증 결과: `.tools/logs/vehicle-verification.json`
- 앱 로그: `.tools/logs/app-verified.log`

현재 검증은 에뮬레이터 기준이며 실제 차량 연동과 주행 성능 검증은 포함하지 않는다.

## 공식 문서

- [Pleos 개발 환경 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Vehicle SDK 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)
- [NaviHelper SDK 가져오기](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)
- [Virtual Console 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
- [Pleos 프로젝트 및 SDK 신청](https://document.pleos.ai/docs/pleos-only/create-my-project)
- [AGP 8.11 호환성](https://developer.android.com/build/releases/agp-8-11-0-release-notes)


### 차량용 UI/UX 리서치 및 적용

공식 출처·설계 선택·검증 범위는 [전체 기획서 9절](drive-mood-planning-full.md#9-차량용-uiux-리서치와-적용-2026-10-28)에 기록한다. 현재 자료는 주행 중 조작을 늘리는 대신, 한눈에 읽히는 상태와 단일 음소거 제어를 우선한다. 세부 기능은 주차 화면에서 제공한다. 선언만으로 OEM 승인이나 실차의 1~2초 인지 성능이 입증되는 것은 아니므로 실제 차량·배포 심사에서는 별도로 검증한다.


차량용 UI 개편 후 확인: 전체 시연 21항목, 저장 복원 2항목과 실제 SDK 입력 18항목. SDK 검사는 실제 D 상태에서 주행 화면의 픽셀까지 확인해 OEM 제한 화면이 접근성 트리 뒤에 겹치는 경우를 구분한다. 해당 기록은 `car-ui-final-checks.log`, `runtime-flow.txt`, `runtime-live.txt`에 남아 있다. 실제 차량 인증·앱 심사 완료를 의미하지 않는다.

0.3.2 색상 개편의 빌드·대비·캡처는 `fresh-colors-*` 이름으로 남아 있다. 해당 버전에서 전체 시연·SDK 입력 검사는 재실행하지 않았다.

0.4.0에서는 주차 홈을 큰 상태·두 행동·정적인 길 일러스트로 개편하고 경로 요약과 미리보기의 위치를 정리했다. 넓은 쉼 화면은 두 열, 작은 창의 실내 비교는 한 열로 표시한다. 벡터 일러스트는 `ui/JourneyLandscape.kt`, 팔레트는 `ui/MoodTheme.kt`에서 관리한다. 일러스트는 실제 경로·관찰 파형을 표현하지 않으며 주차 화면에서만 사용한다.

두 변형 빌드와 lint(오류 0개), UI 37항목 및 실제 SDK 입력 18항목을 확인했다. SDK 검사는 실제 P에서 기록 버튼을 누르고 D에서 정적 요약이 표시되는지, 변화 분류·저장·실제 이동 시 호흡 중단을 검사한다. 이는 에뮬레이터의 실제 SDK 경로 검증이며 실차 검증은 아니다. 긴 경로의 UI 검사는 합성 상태를 사용하므로 내비 제공자 검증과 구분한다. 주요 활성 글자·버튼·배지 조합 대비는 주간 최소 4.69:1·야간 최소 4.50:1이며 주 버튼은 각각 6.73:1·7.87:1이다. 로그는 `.tools/logs/journey-ui-build.log`, `journey-ui-contrast.json`, `runtime-ui.txt`, `runtime-live.txt`, 캡처는 `.tools/screenshots/journey-ui-*.png`다. 전체 시연·저장 복원 검사는 기존 로직 검증 이력으로 유지했다.

0.4.1에서는 `domain/ParkedContent.kt`가 실제 기록과 시간대를 바탕으로 주차 인사와 요약을 선택한다. 종료 인사는 완료 기록 종료 후 30분 동안 사용하고, 최근 실기록은 시연을 제외한 종료 시각으로 고른다. 기록 중에는 현재 여정을 표시한다. 일정 비율은 분석 구간을 분모로 계산하고 주행 시간과 분석 시간을 함께 표시한다. 이 로직은 새로운 분류 점수나 차량 상태를 만들지 않는다.

단위 테스트는 시연과 실기록의 선택 순서, 분석 구간 기준 비율, 기록 시작·주차·장거리·종료 인사, 중단·미래 시각·종료 인사 만료, 시간대 및 호흡과 비율의 독립성을 확인한다. UI 48항목에는 첫 사용·최근 여정·장거리 주차·중단 및 분석 부족·좁은 홈·주행 중 홈 요약 부재 검사를 추가했다. 상황별 캡처는 합성 UI 상태이며 사용자 기록에 저장하지 않는다. 새 변형 빌드·단위 테스트·lint 결과는 `.tools/logs/contextual-home-build.log`, UI 결과는 `runtime-ui.txt`, 캡처는 `.tools/screenshots/contextual-*.png`다. SDK 입력·전체 시연·저장 복원은 기존 검증 이력을 유지했다.

0.4.2에서는 `ui/RhythmIllustration.kt`에 주행용 정적 상태 일러스트를 추가했다. 큰 리듬 상태·짧은 문장 옆에 풍경과 고정 곡선을 표시하고 큰 변화는 살구·주황으로 구분한다. 작은 창에서는 심볼로 줄이며 대기·분석 제외 상태는 곡선을 표시하지 않는다. 일러스트는 실제 신호 그래프·경로·진행률을 표현하지 않고 애니메이션을 사용하지 않는다. 분류·기록·주차 제한 규칙은 유지한다.

두 변형 빌드, 단위 테스트 각 22개, lint 오류 0개(기존 경고 각 13개), 기존 UI 48개와 리듬 UI 34개를 확인했다. 리듬 UI 검사는 `python3 scripts/verify-pleos-runtime.py --action rhythm-ui`로 실행하며 주야간·경로·960dp·480dp 합성 화면을 검사하되 저장소에는 쓰지 않는다. 결과는 `.tools/logs/runtime-rhythm-ui.txt`, 전체 검증 요약은 `rhythm-visual-verification.json`, 캡처는 `.tools/screenshots/rhythm-ui-*.png`다. 새 배경의 주요 글자 대비는 최소 4.86:1이다. 실제 차량·사용자 시선 검증은 수행하지 않았다.

0.4.3에서는 사용자 요청으로 주행 일러스트의 파동에 12초 주기의 미세 움직임을 추가했다. 큰 곡선 제어점은 최대 4dp, 작은 심볼은 최대 1.5dp이며 파동 끝·글자·배경은 고정한다. 큰 그림이 있으면 작은 심볼은 고정해 한 곳만 움직인다. 상태가 같아도 움직이며 학습·신호 대기·분석 제외·앱 비활성 상태에서는 반복 애니메이션을 실행하지 않는다. Compose의 애니메이션 값을 Canvas 그리기 단계에서 읽고, 별도 데이터 타이머나 수집·분류 규칙은 추가하지 않는다. 공식 가이드 적합성·실차 시선 영향은 미검증인 시범 디자인이다.

두 변형 빌드·단위 테스트 각 22개·lint 오류 0개(경고 각 13개), 기존 UI 48개와 확장된 리듬 UI 45개를 확인했다. 고정 상태의 실제 렌더링 프레임을 3초 간격으로 비교해 주간·야간·작은 창에서 움직임이 있는지, 제목은 그대로인지, 신호 대기는 멈추는지 검사한다. 로그는 `.tools/logs/rhythm-motion-build.txt`, `rhythm-motion-pixels.txt`, `rhythm-motion-existing-ui.txt`, 비교 프레임은 `.tools/screenshots/rhythm-motion/`에 남긴다. 움직인 픽셀 비율 검사는 국소적인 화면 변화만 확인하며 시선 분산 안전성을 증명하지 않는다.

0.4.4에서는 주행 경로 카드를 `ui/RouteSummary.kt`로 분리했다. 세이지색 바탕, 목적지 핀, 최대 96sp 남은 시간, 거리·도착 예상 시각, 다음 안내로 정보 위계를 구분한다. 1km 미만은 m, 음수·누락은 확인 중으로 표시한다. 도착 예상은 기기 시계에 남은 초를 더한 추정값이며 15초 간격으로 시계를 갱신한다. 좁은 카드의 거리·도착 시각은 두 줄로, 세로 공간이 짧은 카드는 목적지·시간·거리·다음 안내를 우선한다. SDK 수신·저장·분류는 변경하지 않았다.

두 변형 빌드·단위 테스트 각 22개·lint 오류 0개(기존 경고 각 13개), 기존 UI 48개와 경로 UI 40개를 확인했다. `python3 scripts/verify-pleos-runtime.py --action route-ui`는 저장소에 쓰지 않는 합성 경로 상태로 주간·야간·긴 목적지·정보 누락·음수·1km 미만·360dp·짧은 창을 검사한다. 로그는 `.tools/logs/route-card-build.txt`, `route-card-runtime.txt`, `route-card-existing-ui.txt`, `route-card-verification.json`, 캡처는 `.tools/screenshots/route-card/`에 보관한다. 주요 글자 대비는 주간 최소 5.19:1, 야간 최소 5.60:1이다. 실제 차량·내비 제공자별 도착 예상 정확도는 검증하지 않았다.

0.4.5에서는 전체 검토에서 찾은 실차 대비 문제를 고쳤다. 기어(`GEAR_SELECTION`)는 바뀔 때만 알려주는 속성이라 5초 재확인이 지원 종료 예정인 SDK 기어 조회에만 의존했다. 이제 Android 차량 속성도 1초마다 직접 읽는다. 임시 빌드로 SDK 기어 조회를 끄면 주차 홈이 유지되고, 두 조회를 모두 끄면 P 상태에서도 '연결 확인 중' 화면으로 잠기는 것을 에뮬레이터에서 확인한 뒤 코드를 원래대로 되돌렸다. 시연 기록 중의 '다시 연결'·'권한 확인'은 실제 차량 상태를 초기화해 시연을 중단 기록으로 끝냈으므로 안내만 표시하고, 시연 중단 안내는 주차 해제와 주차 확인 불가를 구분한다. 주차 홈 경로 줄은 경로 카드와 같은 표기 규칙을 쓴다.

두 변형 빌드·단위 테스트 각 24개·lint 오류 0개(기존 경고 각 13개), 에뮬레이터 `flow` 21개·`live` 18개·`ui` 48개·`rhythm-ui` 45개·`route-ui` 40개·`verify-persistence` 2개를 확인했다. `verify-persistence`는 처음에 2026-10-29의 중단된 시연 기록이 기기 이력에 남아 실패했으므로, 검사를 직전 `flow` 리포트 기준으로 좁힌 뒤 `flow` → 강제 종료 → `verify-persistence` 순서로 다시 실행해 통과했다. 시연 중 '다시 연결'은 에뮬레이터에서 직접 눌러 안내 표시와 시연 유지, 정상 도착 리포트를 확인했다. 요약은 `artifacts/validation/0.4.5.json`에 있다.
