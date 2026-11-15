# driving-restrictions

Android Automotive의 실제 UX 규제를 구독하고 화면이 사용할 정책 상태로 전달합니다.

## 사용

```kotlin
val monitor = CarDrivingRestrictionsMonitor(activity) { state ->
    // state.canSelectCategory: 정차 중 설정 탐색 허용 여부
    // state.canShowVoiceTranscription: 음성 원문 표시 가능 여부
}
// Activity.onStart에서 monitor.start()
// Activity.onStop에서 monitor.stop()
```

Activity Context를 전달하여 해당 디스플레이의 규제 상태에 연결합니다.
콜백과 start/stop/retry는 메인 스레드에서 사용합니다.
별도의 기어·속도 권한이나 자체 임계값 추정을 사용하지 않습니다.

- `state.connection`: 연결 상태
- `state.restrictions`: 현재 플랫폼 스냅샷. 조회 전·연결 종료·실패에는 null
- `state.allows(flag)`: 기능별 제한 판단
- `requiresDistractionOptimization`: DO 필요 여부
- `maxRestrictedStringLength`, `maxCumulativeContentItems`, `maxContentDepth`: 플랫폼 한도

상태를 확인할 수 없는 경우에도 설정 조작과 원문 표시를 허용하지 않습니다.
실제 정책과 검증 범위는 [아키텍처](../docs/ARCHITECTURE.md)와
[검증 가이드](../docs/TESTING.md)를 참고하세요.
