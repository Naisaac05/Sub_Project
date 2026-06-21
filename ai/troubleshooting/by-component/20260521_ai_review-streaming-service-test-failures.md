---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AiReviewStreamingServiceTest NullPointerException 및 UnnecessaryStubbingExcept..."

---

# AiReviewStreamingServiceTest NullPointerException 및 UnnecessaryStubbingException 발생

- 발생 일시: 2026-05-21
- 영역: backend
- 심각도: medium

## 증상
`AiReviewStreamingServiceTest` 테스트가 아래 두 가지 원인으로 인해 실패함:
1. `streamAnswer_whenSessionCompleted_shouldFallbackToRuleBasedService` 테스트에서 `java.lang.NullPointerException` 발생.
2. 나머지 비동기 스트리밍 테스트들에서 Mockito의 `UnnecessaryStubbingException` 발생.

## 원인
1. `AiReviewStreamingService.streamAnswer(...)` 내에 세션 완료 상태(`AiReviewStatus.COMPLETED`)일 때 동기식 fallback(`fallbackToSynchronousSubmit`)을 수행하는 로직이 빠져 있어서, 해당 분기 진입 시 `pythonAiReviewClient.streamReview(...)`의 모킹이 누락되어 `NullPointerException`이 발생함.
2. `AiReviewStreamingService` 내의 스트리밍 DB 저장 로직 및 흐름이 모듈화되면서 `testAnswerRepository`, `messageRepository.findBySessionIdOrderByCreatedAtAsc`, `messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn` 등을 직접 호출하지 않게 되어, 기존 테스트 코드에 작성된 해당 모킹들이 Mockito의 엄격한 검증에 의해 `UnnecessaryStubbingException`으로 감지됨.
3. 스트리밍 시작 시 사용자 메시지 저장을 수행하는 `saveUserMessage(...)` 로직이 누락되어, 테스트에서 기대하는 총 `save()` 호출 횟수(2회)를 충족하지 못함.

## 해결 방법
1. [AiReviewStreamingService.java](file:///c:/Users/User/Desktop/Sub_Project/backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java#L70-L80): `streamAnswer` 시작 부분에 복습 세션의 완료 여부를 판단하여 `fallbackToSynchronousSubmit`를 호출하는 로직 추가.
2. [AiReviewStreamingService.java](file:///c:/Users/User/Desktop/Sub_Project/backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java#L80-L90): 스트리밍 시작 시 `self.saveUserMessage(...)`를 호출하여 사용자 입력 메시지를 DB에 트랜잭션 단위로 저장하도록 구현.
3. [AiReviewStreamingServiceTest.java](file:///c:/Users/User/Desktop/Sub_Project/backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java#L145-L150): 더 이상 스트리밍 본문에서 직접 호출하지 않는 레포지토리 모킹들을 `lenient().when(...)`으로 감싸 Mockito의 엄격한 미사용 스터빙 예외를 우회함.

## 재발 방지 / 메모
- 비동기 스트리밍 및 Reactive Stream 흐름에서 비동기 콜백 외부의 상태를 검증하거나 상태 전이에 따라 동기식 fallback 처리를 연동할 때, 테스트 대상이 모킹에 의존하고 있는 구조적 상태 판단 흐름을 항상 면밀히 파악해야 합니다.
- `@Transactional` 분리를 위해 내부적으로 자가 주입(`@Autowired @Lazy private AiReviewStreamingService self`)한 프록시를 통해 DB 저장소 메서드를 호출할 시, 테스트 셋업에서 `ReflectionTestUtils.setField(service, "self", service)`를 통해 `self` 프록시를 올바르게 초기화해주어야 NPE를 방지할 수 있습니다.
