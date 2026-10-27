# Drive Mood

운전 패턴을 리듬으로 읽고, 데이터에서 나온 짧은 문장으로 공감하며, 필요할 때 호흡으로 리듬을 맞춰주는 인카 동반자

현대자동차그룹 · 기아 **차량용 앱 개발 공모전** 제출작

## 한 줄 소개
조향각·브레이크 패턴을 “운전 리듬”으로 해석하고, 피로 시 호흡과 리듬을 맞춰주는 인카 웰니스 앱

## 핵심 기능
- **운전 리듬 시각화**: 조향각·브레이크 패턴을 파동으로 표현
- **마이크로 스토리**: 데이터 조건 조합으로 생성되는 짧은 공감 문장
- **숨 고르기 가이드**: 불안정 리듬 감지 시 30초 호흡 가이드 + 리듬 동기화
- **경로 연동**: NaviHelper를 활용한 분위기 제안
- **도착 리포트**: 오늘의 운전 리듬 요약

## 사용 가능 API (현재)
- Vehicle State: `CAR_DRIVE`, `CAR_CLIMATE`, `CAR_LIGHTS`, `CAR_SEATS` 등
- NaviHelper: 경로, 목적지, TBT

## 기본 구조
```
drive-mood-contest/
├── README.md
├── apk/
│   └── 팀명_DriveMood_release.apk
├── app/
│   └── (소스 코드)
└── docs/
    └── (기획서, 대본 자료)
```

## 실행 방법
1. `apk/` 폴더의 APK를 설치
2. Pleos Playground 환경에서 실행

## 테스트 환경
- Android API 28 이상
- Pleos Playground SDK

## 공모전 정보
- 접수 마감: 2026.11.15
- 공식 사이트: https://appdevchallenge.auto-hmg.io
