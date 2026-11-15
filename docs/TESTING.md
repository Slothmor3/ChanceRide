# 검증 가이드

## 재현 가능한 로컬 검사

JDK·SDK·의존성 저장소 준비는 [README](../README.md#실행하기)를 참고하세요.

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:lintDebug :conversation-core:lintDebug :speech:lintDebug :driving-restrictions:lintDebug
./gradlew :conversation-core:testDebugUnitTest :speech:testDebugUnitTest :driving-restrictions:testDebugUnitTest
```

Windows에서는 `gradlew.bat`를 사용합니다.

## 현재 검증 상태 — 2026-11-15

| 검사 | 결과 |
| --- | --- |
| Debug 앱 빌드 | 통과 |
| Release APK 빌드 | 통과 — 배포용 서명은 별도 설정 |
| 기기 테스트 APK 빌드 | 통과 |
| 네 모듈의 Debug Lint | 통과 |
| conversation-core 단위 테스트 | 8개 통과 |
| speech 단위 테스트 | 11개 통과 |
| driving-restrictions 단위 테스트 | 4개 통과 |
| 정리 이후 실제 기기 UI 테스트 | 연결된 기기가 없어 재실행하지 못함 |

데모용 상태와 예제 덧셈 테스트는 제거했습니다.
현재 제품 로직의 단위 테스트는 총 23개입니다.
테스트 수는 기능 검증의 범위를 나타내며 실제 발화 인식 품질을 보증하지 않습니다.

이전 기기 검증에서는 저장 테스트가 통과했지만,
화면 테스트는 Activity 전환 대기 및 홈→테마 전환 대기에서 실패한 이력이 있습니다.
확대·축소되는 부모 View의 변환을 반영하도록 클릭 좌표 계산을 보완했습니다.
수정된 테스트 APK는 빌드했으나, 기기에서의 해결 여부는 아직 확인하지 않았습니다.

## 기기 테스트

Pleos Connect 에뮬레이터 또는 지원 기기를 **정차 상태**로 연결합니다.
자동 테스트 중에는 에뮬레이터를 수동으로 조작하거나 앱을 별도로 실행하지 않습니다.

```bash
./gradlew :app:connectedDebugAndroidTest
```

기기 테스트는 `RideApplication.sessions`에 별도 저장소를 주입하고 종료 시 원래 저장소로 복구합니다.
사용자의 진행 중 대화를 삭제하거나 테스트 기록으로 덮어쓰지 않습니다.

| 테스트 | 검사 |
| --- | --- |
| SessionStoreTest | 저장·복원, 완료/진행 분리, 유효성 검사, 손상 데이터 처리 |
| ConversationFlowTest | 다음 질문 후 이어하기, 완료 후 홈, 진행 중 대화를 확인창 없이 새로 시작 |

결과 위치:
- 단위 테스트: `<module>/build/reports/tests/testDebugUnitTest/index.html`
- Lint: `<module>/build/reports/lint-results-debug.html`
- 기기 테스트: `app/build/reports/androidTests/connected/debug/index.html`

## 실제 발화 확인

SDK 준비 완료나 화면 전환만으로 음성 인식 성공을 판정하지 않습니다.
기기의 마이크에 직접 말하고 처리 결과를 확인합니다.

1. 마이크 권한을 허용하고 플레이를 시작합니다.
2. 한국어 질문이 끝난 뒤 “다음”을 말해 질문 번호가 증가하는지 확인합니다.
3. “이전”, “다시 읽어줘”, “나가기”를 각각 확인합니다.
4. 일반 문장 “다음에 또 만나자”가 명령으로 처리되지 않는지 확인합니다.
5. “조수석 오늘 기분이 좋아요”는 선택한 조수석으로, 좌석이 없는 발화는 참여자로 표시되는지 확인합니다.
6. 무음 이후 재시도와 반복 오류 이후 중단을 확인합니다.
7. TTS 중에는 STT가 실행되지 않고 재생이 끝난 후 듣기가 시작되는지 확인합니다.

## 차량 상태와 오디오 확인

| 상황 | 기대 동작 |
| --- | --- |
| 주행 UX 규제 활성화 | 설정 차단, 원문 숨김, 질문 탐색 터치 비활성 |
| 규제 조회 실패/서비스 종료 | 허용 상태로 간주하지 않음 |
| 재생 전 포커스 거부 | TTS 재생하지 않음 |
| TTS 중 포커스 상실 | 안내 중단 및 포커스 반환 |
| 포커스 GAIN 수신 | 중단된 안내 자동 재생하지 않음 |
| 화면 꺼짐·백그라운드 | 음성 중단 |
| 화면 켜짐·앱 복귀 | 사용자 재시작 전 임의 재생·마이크 재개하지 않음 |

실제 규제 전환은 지원되는 차량/에뮬레이터의 공식 제어 환경에서 확인합니다.
`cmd hmg_car_service`가 없는 이미지도 있으며, 서비스 부재를 규제 해제나 테스트 성공으로 간주하지 않습니다.
이 프로젝트는 로컬 모의 스위치나 권한 우회로 규제를 해제하지 않습니다.
실차 IGN·pre-suspend 및 OEM UX 검증은 별도 검증 범위입니다.
