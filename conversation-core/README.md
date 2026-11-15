# conversation-core

Android UI와 독립적인 대화 모델과 상태 전이 규칙입니다.

- `PlaySession`: 불변 세션 상태, 질문 이동, 누적 시간, 대화 기록 상한, 저장 데이터 검증
- `PlayCommandParser`: 완성된 한국어 명령 문장의 정확한 일치 검사
- `Seat` / `ConversationLine`: 수동 선택 좌석과 최종 인식 발화
- `ThemeCatalog`: 번들로 제공하는 7개 테마와 질문

선택 좌석의 명시적 접두사만 화자로 사용하고, 자동 화자 판별은 수행하지 않습니다.
`PlaySession.isValid`로 복원 데이터를 검사한 후 화면에 전달합니다.

```bash
./gradlew :conversation-core:testDebugUnitTest
```
