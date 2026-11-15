# speech

Pleos 한국어 TTS/STT와 반이중 음성 플레이를 제공하는 Android 라이브러리입니다.

| 구성 요소 | 책임 |
| --- | --- |
| KoreanSpeechController | SDK 연결, 한국어/ON_DEVICE 설정, 요청·콜백·자원 해제 |
| SpeechEvent | 준비·재생 완료·중단·실패·인식 오류의 타입 이벤트 |
| HandsFreeLoop | 질문 읽기와 인식 순서, 재시도, 중단 이후 예약 작업 무효화 |
| TtsAudioFocus | Assistant/Speech 속성으로 포커스 요청·반환 |
| TtsPlaybackSession | 안내 한 번에 대한 포커스 수명, 전경·Suspend 상태 |

앱이 마이크 권한을 요청하고, 생명주기와 화면 이벤트를 컨트롤러에 전달합니다.
주행 정책은 이 모듈에서 결정하지 않으며 호스트 앱이 원문 표시 가능 여부를 전달합니다.
SDK 바이너리는 포함하지 않고 Gradle의 Pleos Maven 의존성으로 사용합니다.

상태 전이와 제약은 [아키텍처](../docs/ARCHITECTURE.md),
테스트는 [검증 가이드](../docs/TESTING.md)를 참고하세요.
