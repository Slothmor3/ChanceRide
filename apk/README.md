# 제출용 APK

이 폴더에는 설치 가능한 **서명된 Release APK**를 보관합니다.

파일: [ChanceRide-release.apk](ChanceRide-release.apk)

- 패키지: `com.example.chanceride`
- 버전: 1.0 / versionCode 1
- 실행 대상: Pleos Connect 2.1.0 / Android Automotive, Android API 34 이상
- 빌드 종류: release, debuggable=false
- 검증: Release Lint 통과, apksigner 서명 검증 통과 (v2)
- 체크섬: [SHA256SUMS](SHA256SUMS), 패키징 작업 시 자동 갱신
- 음성 기능: Pleos 한국어 TTS/STT 엔진과 마이크 권한 필요

## 다시 만들기

로컬 서명 키와 `signing.properties`를 준비한 후 프로젝트 루트에서 실행합니다.

```bash
./gradlew :app:packageSubmissionApk
```

서명 설정이 없으면 제출용 패키징 작업은 실패합니다.
키와 비밀번호는 Git에 올리지 않으며 같은 앱을 업데이트할 때 사용할 수 있도록 별도로 백업합니다.

## 설치

```bash
adb install -r apk/ChanceRide-release.apk
```

기존 Debug 앱과 서명 키가 다르면 덮어쓸 수 없습니다.
기록이 필요하면 기존 앱을 유지한 별도 에뮬레이터에 설치하세요.
기존 앱 제거 후 설치하는 경우 앱의 로컬 대화 기록도 삭제됩니다.
