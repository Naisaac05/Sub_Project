# Spring SSE Proxy Implementation Plan

> **For Antigravity:** REQUIRED WORKFLOW: Use `.agent/workflows/execute-plan.md` to execute this plan in single-flow mode.

**Goal:** WebClient와 SseEmitter를 사용하여 Spring Boot 환경에 비동기 non-blocking AI Review Streaming SSE Proxy를 구현한다.

**Architecture:** 
1. `build.gradle`에 WebFlux 의존성을 추가하여 `WebClient`를 사용할 수 있게 한다.
2. `PythonAiReviewClient`에 `streamReview(...)` 메서드를 추가하여 파이썬과의 통신 관심사를 격리하고 `Flux<String>` 형태로 스트림을 제공한다.
3. `AiReviewStreamingService`를 신규 생성하여 오직 `SseEmitter` 수명주기, 생명주기 상태 추적 플래그(`StreamingState`), 세분화된 트랜잭션별 영속성 관리를 전담한다.
4. Feature Flag가 비활성화된 경우, 복사 없이 `RuleBasedAiReviewService.submitAnswer(...)`를 동기 호출하여 결과를 단일 done SSE Chunk로 포장해 내보내고 `emitter.complete()` 처리한다.

**Tech Stack:** Java 17, Spring Boot 3.5.14, Spring WebFlux (WebClient), Project Reactor, SseEmitter, JPA/Hibernate, MySQL

---

## 1. 전제 조건 및 설계 제약사항
* **접근 제어자 수정 금지**: `RuleBasedAiReviewService`의 어떠한 private 메서드(채점, 도우미)도 접근 제어자를 허용하지 않고 그대로 유지한다. 스트리밍 상태 적재 시 필요한 간단한 유저 채점 로직(normalize, 키워드 hit 등) 및 세션 유효성 검증은 레포지토리와 자체 헬퍼 메서드를 통해 안전하고 독립적으로 수행한다.
* **비동기 트랜잭션 안전 격리**: 서비스 전체 진입점인 `streamAnswer(...)`에 `@Transactional`을 명시하지 않는다. 대신 비동기 스레드 바운더리에서도 영속성이 온전히 유지되도록 `saveUserMessage()`, `saveCompletedAiMessage()`, `saveDisconnectedAiMessage()`, `savePartialFailedAiMessage()` 각각의 쪼개진 영속화 작업에 개별 `@Transactional`을 부여한다.
* **IOException 및 즉각적인 리소스 차단**: `SseEmitter.send()` 도중 `IOException` 발생 감지 시 즉시 WebClient 스트림 구독을 `.dispose()` 하고, `AtomicReference<StreamingState>`가 `DISCONNECTED`로 이행되도록 동기화한 뒤 누적된 텍스트를 `STATUS:DISCONNECTED` 태그와 함께 보존한다.
* **SSE Event Parser 책임 부여**: Python SSE 이벤트(`data: {"type":"chunk", "content":"..."}`)를 JSON 분석하기 위해 Jackson `ObjectMapper`를 활용해 파싱 로직을 구현하고, `type` 종류에 따라 `StringBuilder` 누적 및 최종 AI 메타데이터 수집을 분기한다.

---

## 2. 세부 구현 태스크

### Task 1: WebFlux 라이브러리 추가
**Files:**
- Modify: [build.gradle](file:///c:/Users/User/Desktop/Sub_Project/backend/build.gradle)

**Step 1: Implement dependency addition**
`build.gradle` dependencies 블록 내에 다음 라인을 추가한다.
```groovy
implementation 'org.springframework.boot:spring-boot-starter-webflux'
```

**Step 2: Run project build**
Run: `gradlew.bat compileJava` (backend 폴더 내 실행)
Expected: BUILD SUCCESSFUL

**Step 3: Commit**
```bash
git add build.gradle
git commit -m "build: add spring-boot-starter-webflux dependency"
```

---

### Task 2: PythonAiReviewClient 스트리밍 인터페이스 확장
**Files:**
- Modify: [PythonAiReviewClient.java](file:///c:/Users/User/Desktop/Sub_Project/backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java)

**구현 세부사항:**
1. `PythonAiRequest` 레코드에 `Boolean stream` 필드를 추가하여 잭슨 직렬화 시 `"stream": true`가 들어가도록 확장한다.
2. `WebClient` 필드 및 `@PostConstruct` 블록을 구성한다.
3. `streamReview(String uri, String correlationId, PythonAiRequest request)` 메서드를 구현하여 `Flux<String>`을 반환하도록 위임한다.

**Step 1: Write JUnit Mock test verifying stream request generation**
* `PythonAiReviewClientTest` 또는 신규 테스트 코드를 구성해 `streamReview` 호출 시 올바른 Payload가 전달되는지 Mock 검증 작성.

**Step 2: Implement Client Extensions**
* `PythonAiRequest` 수정 및 `streamReview` 구현 추가.

**Step 3: Run client test suite**
Run: `gradlew.bat test --tests *PythonAiReviewClientTest*`
Expected: PASS

**Step 4: Commit**
```bash
git add backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java
git commit -m "feat: add streamReview to PythonAiReviewClient using WebClient"
```

---

### Task 3: AiReviewStreamingService 신규 구현 및 트랜잭션 세분화
**Files:**
- Create: `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java`

**상태 플래그 구조:**
```java
public enum StreamingState {
    INIT, STARTED, COMPLETED, DISCONNECTED, FAILED
}
```

**세분화된 트랜잭션 구조:**
* `saveUserMessage(...)` : USER 메시지 적재 [Transactional]
* `saveCompletedAiMessage(...)` : 정상 Done 완료 시 AI 메시지 + done metadata 적재 (`low_confidence,STATUS:COMPLETED`) [Transactional]
* `saveDisconnectedAiMessage(...)` : 조기 연결 해제 시 partial text 적재 (`STATUS:DISCONNECTED`) [Transactional]
* `savePartialFailedAiMessage(...)` : 스트림 도중 에러 시 에러 안내 문구 추가 적재 (`STATUS:PARTIAL_FAILED`) [Transactional]

**Step 1: Write mock-based stream lifecycle integration test**
* `AiReviewStreamingServiceTest.java`를 신규 작성하여 완료, 에러, Disconnect 상황에서의 각각의 독립 트랜잭션 저장 메서드가 호출되는지 Mockito 검증.

**Step 2: Implement AiReviewStreamingService**
* WebClient Flux에서 나오는 SSE 데이터를 Jackson `ObjectMapper`로 분석해 `type`에 따라 처리하는 파서 로직과 리비전된 상태 전이 코드 구현.

**Step 3: Run service tests**
Run: `gradlew.bat test --tests *AiReviewStreamingServiceTest*`
Expected: PASS

**Step 4: Commit**
```bash
git add backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java
git commit -m "feat: implement AiReviewStreamingService with transactional database segments"
```

---

### Task 4: AiReviewController 스트리밍 엔드포인트 연동
**Files:**
- Modify: [AiReviewController.java](file:///c:/Users/User/Desktop/Sub_Project/backend/src/main/java/com/devmatch/controller/AiReviewController.java)

**Step 1: Add Route mapping**
* `/api/ai-review/sessions/{sessionId}/messages/stream` 에 POST 바인딩.

**Step 2: Run overall integration tests**
Run: `gradlew.bat test`
Expected: PASS (기존 non-streaming 테스트 및 새로운 스트리밍 테스트 100% 통과)

**Step 3: Commit**
```bash
git add backend/src/main/java/com/devmatch/controller/AiReviewController.java
git commit -m "feat: map streaming endpoint in AiReviewController"
```
