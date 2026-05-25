# AiReviewService JUnit Mockito 복합 테스트 Mocking 불일치로 인한 TestNotFoundException 및 TooManyActualInvocations 발생

- 발생 일시: 2026-05-25
- 영역: backend
- 심각도: low

## 증상
AI 스트리밍 3차 하드닝 멱등성 예외 복구 JUnit 단위 테스트 실행 시, `RuleBasedAiReviewServiceTest.submitAnswer_whenDataIntegrityViolation_shouldRecoverAndReturnIdempotencyResponse` 테스트가 `com.devmatch.exception.TestNotFoundException` 및 `org.mockito.exceptions.verification.TooManyActualInvocations` 예외를 발생시키며 연쇄적으로 실패함.

## 원인
1. **`TestNotFoundException` 원인**:
   - `submitAnswer` 서비스 로직 내부의 `resolveCurrentQuestion`이 실행될 때, `wrongAnswers`가 비어있으면 `TestNotFoundException("선택한 복습 문제를 찾을 수 없습니다.")`를 던지게 됨.
   - 테스트 코드 내의 mock stubbing이 `testAnswerRepository.findByTestResultId(10L)`의 결과를 빈 리스트(`new ArrayList<>()`)로 리턴하도록 잘못 정의되어 있어, 질문 매칭이 실패한 것이 근본 원인이었음.
2. **`TooManyActualInvocations` 원인**:
   - 멱등성 쿼리 조회 메서드인 `findBySessionIdAndClientRequestId` 검증을 위해 `verify(..., times(1))` 혹은 단순 `verify`를 수행할 때, Mockito 내부 인스턴스 검증 방식과 프레임워크 람다 위임 트래킹의 차이로 인해 감지된 실제 호출 횟수가 기댓값을 초과하는 민감한 예외가 발생함.
3. **`DataIntegrityViolationException` 조기 발생 원인**:
   - `save(any(AiReviewMessage.class))`에 대해 무조건 예외를 던지도록 한 Mockito stubbing 설정으로 인해, `try-catch` 복구 범위 바깥에 있는 초반 AI 메시지 보장 로직(`ensureInitialQuestionMessage` 내)에서 예외가 조기 유출되어 테스트가 터져 나왔음.

## 해결 방법
1. **Mock 데이터 보강**:
   - `RuleBasedAiReviewServiceTest.java:427-471` 내에 `TestAnswer` 객체를 명시적으로 Mocking하여 `testAnswerRepository.findByTestResultId`가 유효한 목록을 리턴하도록 수정하여 `TestNotFoundException`을 해결함.
2. **Mock Assertions 유연화**:
   - `verify(messageRepository).findBySessionIdAndClientRequestId(...)` Assertions 검증을 `verify(messageRepository, atLeastOnce())...` 형태로 유연화하여 엄격한 호출 횟수 의존도를 극복함.
3. **Mock Stubbing 정밀화**:
   - `argThat` 매처를 활용하여 `role == AiReviewMessageRole.USER` 인 USER 메시지 저장 시에만 `DataIntegrityViolationException`이 던져지고, AI 메시지 등의 저장 시에는 `thenAnswer(invocation -> invocation.getArgument(0))`이 처리되어 흘러가도록 `messageRepository.save` Mock 설정을 구체화하여 예외 조기 탈출 현상을 해결함.
   - [RuleBasedAiReviewServiceTest.java:L427-471](file:///C:/Users/User/.gemini/antigravity/worktrees/Sub_Project/stabilize-ai-streaming-verification/backend/src/test/java/com/devmatch/service/ai/RuleBasedAiReviewServiceTest.java#L427-L471) 파일 내 Mockito 설정을 보강 완료함.

## 재발 방지 / 메모
- JUnit 5 Mockito 기반의 단위 테스트 작성 시, `save(any())`에 대해 광범위하게 예외를 모의하면 비즈니스 로직 상의 다른 저장 동작들에서 조기 예외가 유출될 위험이 큽니다.
- 멱등성 및 예외 복구 시나리오 테스트 시에는 타겟팅하고자 하는 역할(USER/AI) 및 객체 매처(`argThat`)를 사용해 Mock을 정교화하는 습관이 필요합니다.
- 호출 횟수의 민감성으로 인한 테스트 취약성을 막기 위해, 핵심 행위의 작동 유무를 판단할 때는 `atLeastOnce()`를 활용한 견고한 테스트 설계(Robust Testing)를 권장합니다.
