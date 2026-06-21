---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "AI Review Test Session Reset Implementation Plan 도입 계획 및 마이그레이션 작업 방향"

---

# AI Review Test Session Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 테스트 중 AI 리뷰 세션을 처음부터 다시 시작할 수 있는 환경변수 플래그 경유 기능을 추가하고, 테스트 종료 후 grep 한 번으로 모두 식별/제거 가능하게 만든다.

**Architecture:** Spring `@ConditionalOnProperty` 로 백엔드 컨트롤러를 환경변수에 묶고, Next.js 컴포넌트는 `process.env.NEXT_PUBLIC_*` 가드로 제어. 모든 신규 코드에 `🧪 테스트 전용` 마커를 박아 제거 절차를 단순화한다.

**Tech Stack:** Spring Boot 3.x (Java 17), JPA, Spring Security, JUnit + MockMvc, Next.js 14 (React), Axios via `apiClient`

**Spec reference:** [docs/superpowers/specs/2026-05-27-ai-review-test-session-reset-design.md](../specs/2026-05-27-ai-review-test-session-reset-design.md)

---

## File Structure

### Backend (신규)
- `backend/src/main/java/com/devmatch/controller/TestResetAiReviewController.java` — 컨트롤러 + 인라인 로직 (1파일에 모두)
- `backend/src/test/java/com/devmatch/controller/TestResetAiReviewControllerTest.java` — MockMvc 통합 테스트

### Backend (기존 수정)
- `backend/src/main/java/com/devmatch/repository/AiReviewMessageRepository.java` — `deleteBySessionId` 메서드 추가
- `backend/src/main/resources/application.yml` — `test-reset.enabled` 3줄 추가

### Frontend (신규)
- `frontend/src/components/ai-review/TestResetButton.tsx` — 버튼 컴포넌트

### Frontend (기존 수정)
- `frontend/src/lib/ai-review.ts` — `resetAiReviewSession` 함수 추가
- `frontend/src/app/tests/results/[id]/review/page.tsx` — import + 렌더 위치

### Documentation
- `.env.example` (있으면) 또는 신규 — 환경변수 2개 명시

---

## Task 1: AiReviewMessageRepository에 deleteBySessionId 메서드 추가

**Files:**
- Modify: `backend/src/main/java/com/devmatch/repository/AiReviewMessageRepository.java`
- Test: `backend/src/test/java/com/devmatch/repository/AiReviewMessageRepositoryTest.java` (신규)

**Rationale:** `AiReviewSession` entity는 messages 관계를 정의하지 않으므로 JPA cascade 가 동작하지 않는다. 세션 삭제 전 메시지를 명시적으로 삭제해야 orphan row 가 남지 않는다.

- [ ] **Step 1: Repository 테스트 작성 (실패)**

Create `backend/src/test/java/com/devmatch/repository/AiReviewMessageRepositoryTest.java`:

```java
package com.devmatch.repository;

import com.devmatch.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AiReviewMessageRepositoryTest {

    @Autowired private AiReviewMessageRepository messageRepository;
    @Autowired private AiReviewSessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TestResultRepository testResultRepository;

    @Test
    void deleteBySessionId_removesAllMessagesForGivenSession_andLeavesOthers() {
        // Given: 두 세션, 각각 메시지 보유
        // (헬퍼 메서드로 user, testResult, session, message 생성 — 실제 코드에서는 기존 패턴 따라)
        AiReviewSession sessionA = persistedSession();
        AiReviewSession sessionB = persistedSession();
        persistedMessage(sessionA);
        persistedMessage(sessionA);
        persistedMessage(sessionB);

        // When
        long deleted = messageRepository.deleteBySessionId(sessionA.getId());

        // Then
        assertThat(deleted).isEqualTo(2);
        assertThat(messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionA.getId())).isEmpty();
        assertThat(messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionB.getId())).hasSize(1);
    }

    // 헬퍼 메서드: persistedSession, persistedMessage는 실제 구현 시
    // 기존 통합 테스트의 fixture 패턴 또는 직접 builder 사용
}
```

- [ ] **Step 2: 테스트 실행 (FAIL — 메서드 없음)**

Run: `cd backend && ./gradlew.bat test --tests "*.AiReviewMessageRepositoryTest"`  
Expected: 컴파일 에러 (`deleteBySessionId` 없음)

- [ ] **Step 3: Repository에 메서드 추가**

Modify `backend/src/main/java/com/devmatch/repository/AiReviewMessageRepository.java`:

```java
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface AiReviewMessageRepository extends JpaRepository<AiReviewMessage, Long> {

    // ... 기존 메서드들 ...

    // 🧪 테스트 전용 세션 초기화에서 사용
    @Modifying
    @Transactional
    @Query("DELETE FROM AiReviewMessage m WHERE m.session.id = :sessionId")
    long deleteBySessionId(Long sessionId);
}
```

- [ ] **Step 4: 테스트 재실행 (PASS)**

Run: `cd backend && ./gradlew.bat test --tests "*.AiReviewMessageRepositoryTest"`  
Expected: 1/1 PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devmatch/repository/AiReviewMessageRepository.java backend/src/test/java/com/devmatch/repository/AiReviewMessageRepositoryTest.java
git commit -m "feat(ai-review): AiReviewMessageRepository에 deleteBySessionId 추가

테스트 세션 초기화 기능에서 messages를 session보다 먼저 삭제하는 데 사용.
AiReviewSession entity에 messages 관계가 없어 JPA cascade가 동작하지 않으므로 명시적 삭제 필요."
```

---

## Task 2: application.yml 에 test-reset 프로퍼티 추가

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: yml 편집**

Modify `backend/src/main/resources/application.yml` — `app.ai-review:` 블록 아래에 추가:

```yaml
app:
  ai-review:
    # ... 기존 설정들 ...

    # 🧪 테스트 전용: AI 리뷰 세션 초기화 엔드포인트
    # 운영 환경에서는 절대 true 로 설정하지 말 것. 기본값 false.
    test-reset:
      enabled: ${AI_REVIEW_TEST_RESET_ENABLED:false}
```

위치는 기존 `rule-based:`, `limits:` 블록과 같은 들여쓰기 레벨.

- [ ] **Step 2: 백엔드가 정상 기동하는지 확인**

Run: `cd backend && ./gradlew.bat compileJava`  
Expected: `BUILD SUCCESSFUL`

(이 단계에선 컨트롤러가 아직 없으므로 별도 동작 검증 X)

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/application.yml
git commit -m "chore(ai-review): test-reset.enabled 프로퍼티 추가

기본값 false. 환경변수 AI_REVIEW_TEST_RESET_ENABLED=true 일 때만 활성.
실제 컨트롤러는 다음 커밋에서 추가."
```

---

## Task 3: TestResetAiReviewController 통합 테스트 작성 (env OFF 시 404)

**Files:**
- Create: `backend/src/test/java/com/devmatch/controller/TestResetAiReviewControllerTest.java`

**Rationale:** TDD 순서로 환경변수 OFF 시 엔드포인트가 등록되지 않는 것을 먼저 검증. 컨트롤러를 아직 만들지 않은 상태에서 호출 시 404 가 나야 함.

- [ ] **Step 1: 테스트 파일 작성 (env OFF 케이스만)**

Create `backend/src/test/java/com/devmatch/controller/TestResetAiReviewControllerTest.java`:

```java
package com.devmatch.controller;

import com.devmatch.entity.Role;
import com.devmatch.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai-review.test-reset.enabled=false")
class TestResetAiReviewControllerDisabledTest {

    @Autowired private MockMvc mvc;

    private CustomUserDetails menteePrincipal() {
        return new CustomUserDetails(1L, "learner@devmatch.com", Role.MENTEE);
    }

    @Test
    void resetSession_whenDisabled_returnsNotFound() throws Exception {
        mvc.perform(post("/api/ai-review/test-results/100/session/reset")
                        .with(user(menteePrincipal())))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: 테스트 실행 — 현재는 컨트롤러가 없으므로 자연스럽게 404 (PASS)**

Run: `cd backend && ./gradlew.bat test --tests "*.TestResetAiReviewControllerDisabledTest"`  
Expected: 1/1 PASS (라우트 없어서 404 정상 반환)

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/devmatch/controller/TestResetAiReviewControllerTest.java
git commit -m "test(ai-review): test-reset disabled 시 404 통합 테스트 추가

컨트롤러 추가 후에도 @ConditionalOnProperty 로 인해 동일 404가 유지되는지
회귀 가드 역할."
```

---

## Task 4: TestResetAiReviewController 작성 (env ON 시 200, 권한 검증 포함)

**Files:**
- Create: `backend/src/main/java/com/devmatch/controller/TestResetAiReviewController.java`
- Modify: `backend/src/test/java/com/devmatch/controller/TestResetAiReviewControllerTest.java` (테스트 케이스 추가)

- [ ] **Step 1: 컨트롤러 실패 테스트 추가 (env ON, 정상 케이스)**

Add to `TestResetAiReviewControllerTest.java`:

```java
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai-review.test-reset.enabled=true")
class TestResetAiReviewControllerEnabledTest {

    @Autowired private MockMvc mvc;
    @Autowired private AiReviewSessionRepository sessionRepository;
    @Autowired private AiReviewMessageRepository messageRepository;
    @Autowired private TestResultRepository testResultRepository;
    @Autowired private UserRepository userRepository;

    private CustomUserDetails principal(Long userId) {
        return new CustomUserDetails(userId, "u" + userId + "@devmatch.com", Role.MENTEE);
    }

    @Test
    void resetSession_whenEnabled_deletesOwnSessionAndMessages() throws Exception {
        // Given: 사용자 + testResult + session + 메시지 2개 (헬퍼로 생성)
        User user = persistUser();
        TestResult result = persistTestResult(user);
        AiReviewSession session = persistSession(user, result);
        persistMessage(session);
        persistMessage(session);

        // When: 본인이 reset 호출
        mvc.perform(post("/api/ai-review/test-results/" + result.getId() + "/session/reset")
                        .with(user(principal(user.getId()))))
                .andExpect(status().isOk());

        // Then: 세션 + 메시지 모두 사라짐
        assertThat(sessionRepository.findById(session.getId())).isEmpty();
        assertThat(messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())).isEmpty();
    }

    @Test
    void resetSession_whenOtherUsersTestResult_returnsForbidden() throws Exception {
        User owner = persistUser();
        User attacker = persistUser();
        TestResult result = persistTestResult(owner);
        persistSession(owner, result);

        mvc.perform(post("/api/ai-review/test-results/" + result.getId() + "/session/reset")
                        .with(user(principal(attacker.getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void resetSession_whenNoSessionExists_returnsOkNoop() throws Exception {
        User user = persistUser();
        TestResult result = persistTestResult(user);
        // 세션 없음

        mvc.perform(post("/api/ai-review/test-results/" + result.getId() + "/session/reset")
                        .with(user(principal(user.getId()))))
                .andExpect(status().isOk());
    }

    // 헬퍼: persistUser, persistTestResult, persistSession, persistMessage는
    // 기존 통합 테스트의 fixture 패턴 또는 builder 직접 사용
}
```

- [ ] **Step 2: 테스트 실행 (FAIL — 컨트롤러 없음)**

Run: `cd backend && ./gradlew.bat test --tests "*.TestResetAiReviewControllerEnabledTest"`  
Expected: 3/3 FAIL — 모두 404 또는 빈 응답

- [ ] **Step 3: 컨트롤러 작성**

Create `backend/src/main/java/com/devmatch/controller/TestResetAiReviewController.java`:

```java
// 🧪 테스트 전용: AI 리뷰 세션 초기화 컨트롤러
//
// 환경변수 AI_REVIEW_TEST_RESET_ENABLED=true 일 때만 활성화됨.
// 운영 환경에서는 절대 활성화하지 말 것.
//
// 제거 방법 (테스트 종료 후):
//   1. 이 파일 삭제
//   2. backend/src/test/java/com/devmatch/controller/TestResetAiReviewControllerTest.java 삭제
//   3. application.yml 의 app.ai-review.test-reset 블록 3줄 삭제
//   4. AiReviewMessageRepository 의 deleteBySessionId 메서드 (다른 곳에서 안 쓰면) 삭제
//   5. frontend/src/components/ai-review/TestResetButton.tsx 삭제
//   6. frontend/src/lib/ai-review.ts 의 resetAiReviewSession 함수 삭제
//   7. frontend/src/app/tests/results/[id]/review/page.tsx 의 import/render 2줄 삭제
//   8. .env 에서 AI_REVIEW_TEST_RESET_ENABLED, NEXT_PUBLIC_AI_REVIEW_TEST_RESET 제거
// 추가일: 2026-05-27, 추가자: aucu2005@gmail.com
package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.entity.AiReviewSession;
import com.devmatch.entity.TestResult;
import com.devmatch.exception.TestNotFoundException;
import com.devmatch.repository.AiReviewMessageRepository;
import com.devmatch.repository.AiReviewSessionRepository;
import com.devmatch.repository.TestResultRepository;
import com.devmatch.security.CustomUserDetails;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/ai-review/test-results")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ai-review.test-reset.enabled", havingValue = "true")
public class TestResetAiReviewController {

    private final AiReviewSessionRepository sessionRepository;
    private final AiReviewMessageRepository messageRepository;
    private final TestResultRepository testResultRepository;

    @PostMapping("/{testResultId}/session/reset")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> resetSession(
            @PathVariable Long testResultId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();

        TestResult result = testResultRepository.findById(testResultId)
                .orElseThrow(() -> new TestNotFoundException("진단평가 결과를 찾을 수 없습니다."));

        if (!Objects.equals(result.getUser().getId(), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("본인의 진단평가만 초기화할 수 있습니다."));
        }

        Optional<AiReviewSession> existing =
                sessionRepository.findTopByUserIdAndTestResultIdOrderByCreatedAtDesc(userId, testResultId);

        existing.ifPresent(session -> {
            messageRepository.deleteBySessionId(session.getId());
            sessionRepository.delete(session);
        });

        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

(`ApiResponse.error` 시그니처가 다르면 해당 클래스 확인 후 보정)

- [ ] **Step 4: 테스트 재실행 (PASS)**

Run: `cd backend && ./gradlew.bat test --tests "*.TestResetAiReviewController*"`  
Expected: 4/4 PASS (disabled 1건 + enabled 3건)

- [ ] **Step 5: 전체 백엔드 테스트가 깨지지 않는지 확인**

Run: `cd backend && ./gradlew.bat test`  
Expected: 기존 테스트 모두 PASS (회귀 없음)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devmatch/controller/TestResetAiReviewController.java backend/src/test/java/com/devmatch/controller/TestResetAiReviewControllerTest.java
git commit -m "feat(ai-review): 🧪 테스트 전용 세션 초기화 엔드포인트 추가

POST /api/ai-review/test-results/{id}/session/reset
- @ConditionalOnProperty 로 환경변수 OFF 시 컨트롤러 미등록 (404)
- ON 시 본인 testResult 의 세션 + 메시지 삭제
- 다른 사용자 testResult 시도 시 403
- 세션 없는 경우 200 (no-op)

테스트 종료 후 제거 가이드는 파일 상단 주석 참고."
```

---

## Task 5: frontend/lib/ai-review.ts 에 resetAiReviewSession 함수 추가

**Files:**
- Modify: `frontend/src/lib/ai-review.ts`

- [ ] **Step 1: 함수 추가**

Modify `frontend/src/lib/ai-review.ts` — 파일 끝에 추가:

```typescript
// 🧪 테스트 전용: AI 리뷰 세션 초기화 API
// 제거 시 본 함수 + TestResetButton.tsx + page.tsx의 import/render 함께 삭제
// 백엔드는 환경변수 OFF 면 404 반환
export async function resetAiReviewSession(testResultId: number): Promise<void> {
  await apiClient.post(
    `/ai-review/test-results/${testResultId}/session/reset`,
    null,
    { timeout: AI_REVIEW_TIMEOUT_MS }
  );
}
```

`AI_REVIEW_TIMEOUT_MS` 는 같은 파일 상단에 이미 정의돼 있어 재사용.

- [ ] **Step 2: TypeScript 컴파일 확인**

Run: `cd frontend && npx tsc --noEmit`  
Expected: 에러 없음

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/ai-review.ts
git commit -m "feat(frontend): 🧪 resetAiReviewSession API 클라이언트 함수 추가"
```

---

## Task 6: TestResetButton 컴포넌트 작성

**Files:**
- Create: `frontend/src/components/ai-review/TestResetButton.tsx`

- [ ] **Step 1: 컴포넌트 파일 작성**

Create `frontend/src/components/ai-review/TestResetButton.tsx`:

```tsx
// 🧪 테스트 전용: AI 리뷰 세션 초기화 버튼
//
// 환경변수 NEXT_PUBLIC_AI_REVIEW_TEST_RESET=true 일 때만 렌더.
//
// 제거 방법:
//   1. 이 파일 삭제
//   2. frontend/src/app/tests/results/[id]/review/page.tsx 에서 import/render 2줄 제거
//   3. frontend/src/lib/ai-review.ts 의 resetAiReviewSession 함수 제거
//   4. 백엔드 TestResetAiReviewController.java 등 제거 (해당 파일 주석 참고)
//   5. .env 에서 NEXT_PUBLIC_AI_REVIEW_TEST_RESET 제거
// 추가일: 2026-05-27
'use client';

import { useState } from 'react';
import { resetAiReviewSession } from '@/lib/ai-review';

interface TestResetButtonProps {
  testResultId: number;
}

export function TestResetButton({ testResultId }: TestResetButtonProps) {
  // 환경변수 OFF 시 컴포넌트 자체가 아무것도 렌더하지 않음
  if (process.env.NEXT_PUBLIC_AI_REVIEW_TEST_RESET !== 'true') {
    return null;
  }

  const [resetting, setResetting] = useState(false);

  const handleReset = async () => {
    const ok = window.confirm(
      'AI 리뷰 세션을 처음부터 다시 시작합니다.\n현재 대화 내역이 모두 삭제됩니다.\n진행할까요?'
    );
    if (!ok) {
      return;
    }
    setResetting(true);
    try {
      await resetAiReviewSession(testResultId);
      window.location.reload();
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      window.alert('초기화 실패: ' + msg);
      setResetting(false);
    }
  };

  return (
    <button
      type="button"
      onClick={handleReset}
      disabled={resetting}
      title="🧪 테스트 전용 — 운영 환경엔 표시되지 않습니다"
      className="rounded border border-red-400 bg-red-50 px-3 py-1 text-sm text-red-700 hover:bg-red-100 disabled:opacity-50"
    >
      {resetting ? '초기화 중...' : '🧪 세션 초기화 (테스트)'}
    </button>
  );
}
```

- [ ] **Step 2: TypeScript 컴파일 확인**

Run: `cd frontend && npx tsc --noEmit`  
Expected: 에러 없음

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ai-review/TestResetButton.tsx
git commit -m "feat(frontend): 🧪 TestResetButton 컴포넌트 추가

NEXT_PUBLIC_AI_REVIEW_TEST_RESET=true 일 때만 렌더.
클릭 시 confirm → reset API 호출 → 페이지 새로고침."
```

---

## Task 7: review/page.tsx 에 TestResetButton 연결

**Files:**
- Modify: `frontend/src/app/tests/results/[id]/review/page.tsx`

**Rationale:** 버튼을 페이지 상단(헤더 영역) 에 배치. 운영 환경에선 환경변수 미설정이라 컴포넌트가 null 반환.

- [ ] **Step 1: import 추가**

Modify `frontend/src/app/tests/results/[id]/review/page.tsx` — 기존 import 그룹 아래에 추가:

```tsx
// 🧪 테스트 전용 (제거 시 본 import + 아래 렌더 라인 함께 삭제)
import { TestResetButton } from '@/components/ai-review/TestResetButton';
```

- [ ] **Step 2: 렌더 위치 추가**

같은 파일에서 페이지 상단 헤더 영역을 찾아 (예: `Header` 또는 첫 번째 페이지 타이틀 근처) 다음을 삽입:

```tsx
{/* 🧪 테스트 전용 (제거 시 본 라인 + 상단 import 함께 삭제) */}
<TestResetButton testResultId={Number(testResultId)} />
```

`testResultId` 변수는 같은 파일에서 `useParams()` 로 이미 추출돼 있을 가능성이 높음. 변수명이 다르면 그에 맞춰 사용.

⚠️ 정확한 삽입 위치는 구현 시 파일을 보고 결정 — 페이지의 메인 제목 부근, 학생이 답변하는 영역의 헤더 옆 추천.

- [ ] **Step 3: TypeScript 컴파일 확인**

Run: `cd frontend && npx tsc --noEmit`  
Expected: 에러 없음

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/tests/results/[id]/review/page.tsx
git commit -m "feat(frontend): 🧪 리뷰 페이지에 TestResetButton 연결

환경변수 OFF 시 null 반환이라 운영 영향 0."
```

---

## Task 8: 환경변수 문서화 (.env.example 또는 README 메모)

**Files:**
- Modify or Create: `.env.example` 또는 `README.md` (프로젝트 컨벤션 따라)

- [ ] **Step 1: 기존 환경변수 파일 확인**

Run: `ls .env* backend/.env* frontend/.env* 2>/dev/null`

존재하는 .env.example 가 있으면 거기에 추가. 없으면 README 의 환경변수 섹션에 메모.

- [ ] **Step 2: 환경변수 2개 명시**

`.env.example` (또는 해당 위치) 에 추가:

```
# 🧪 테스트 전용 — AI 리뷰 세션 초기화 (운영 환경에선 false 또는 미설정 유지)
AI_REVIEW_TEST_RESET_ENABLED=false
NEXT_PUBLIC_AI_REVIEW_TEST_RESET=false
```

- [ ] **Step 3: Commit**

```bash
git add .env.example   # 또는 README.md
git commit -m "docs: 🧪 AI_REVIEW_TEST_RESET 환경변수 example 추가

테스트 종료 후 본 항목들도 함께 제거."
```

---

## Task 9: 수동 통합 검증 + Pull Request 작성

**Files:** (코드 변경 없음 — 검증/배포만)

- [ ] **Step 1: 환경변수 OFF 상태 회귀 확인**

확인:
```powershell
$env:AI_REVIEW_TEST_RESET_ENABLED="false"
# 또는 unset
cd backend && .\gradlew.bat bootRun
```

새 PowerShell에서:
```powershell
curl -X POST http://localhost:8080/api/ai-review/test-results/1/session/reset
```
Expected: **404** (엔드포인트 등록 안 됨)

브라우저에서 리뷰 화면 → 🧪 버튼 안 보임 확인.

- [ ] **Step 2: 환경변수 ON 상태 동작 확인**

```powershell
$env:AI_REVIEW_TEST_RESET_ENABLED="true"
$env:NEXT_PUBLIC_AI_REVIEW_TEST_RESET="true"
```

백엔드/프론트 재시작 후:
1. 브라우저에서 리뷰 화면 → 🧪 버튼 표시 확인
2. 꼬리질문 답변 → 🧪 클릭 → confirm → 페이지 새로고침
3. 첫 단계로 돌아온 것 확인
4. DB 직접 확인 (선택): `select * from ai_review_sessions where test_result_id = ?` → 행 없음 또는 새 세션 1개

- [ ] **Step 3: 브랜치 push + PR 생성**

```powershell
git push -u origin isc/feature/ai_review_test_session_reset
```

```powershell
gh pr create --title "feat(ai-review): 🧪 테스트 전용 세션 초기화 기능" --body "$(cat <<'EOF'
## Summary

테스트 시 같은 학생 계정/진단평가에서 AI 리뷰 세션을 처음부터 재시작할 수 있는 환경변수 플래그 경유 기능.

**운영 영향 없음** (환경변수 기본 false → 엔드포인트 미등록 + 버튼 미렌더)

## 무엇이 들어있나

| 파일 | 종류 |
|---|---|
| `backend/.../TestResetAiReviewController.java` | 신규 컨트롤러 |
| `backend/.../AiReviewMessageRepository.java` | 메서드 1개 추가 |
| `backend/.../application.yml` | 프로퍼티 3줄 |
| `backend/.../TestResetAiReviewControllerTest.java` | 4건 통합 테스트 |
| `frontend/.../TestResetButton.tsx` | 신규 컴포넌트 |
| `frontend/.../ai-review.ts` | API 함수 1개 |
| `frontend/.../review/page.tsx` | import + 렌더 2줄 |

## 활성화 방법 (로컬)

\`\`\`
AI_REVIEW_TEST_RESET_ENABLED=true
NEXT_PUBLIC_AI_REVIEW_TEST_RESET=true
\`\`\`

후 백엔드/프론트 재시작.

## 제거 방법 (테스트 종료 후)

각 신규 파일 상단 주석 + spec 문서 참고. 핵심: \`grep -rn \"🧪 테스트 전용\" .\` 로 모두 식별 가능.

## 관련 문서

- 설계: \`docs/superpowers/specs/2026-05-27-ai-review-test-session-reset-design.md\`
- 구현 계획: \`docs/superpowers/plans/2026-05-27-ai-review-test-session-reset.md\`

## Test plan

- [ ] 환경변수 OFF 상태에서 운영 동작 동일 확인 (curl 404 + 버튼 미렌더)
- [ ] 환경변수 ON 상태에서 reset → 새 세션 → 정상 동작
- [ ] 다른 사용자의 testResultId 로 시도 → 403
- [ ] 기존 백엔드 테스트 전체 PASS
- [ ] (선택) DB 직접 확인으로 세션/메시지 row 삭제 확인
EOF
)"
```

- [ ] **Step 4: PR URL 확인 + 팀 공유**

PR URL 을 콘솔 출력에서 확인. 리뷰 요청 후 머지.

---

## Self-Review Checklist (구현 전 확인)

다음을 한 번 확인하고 issue 발견 시 인라인 수정:

1. **Spec coverage**:
   - R1 (본인 세션 한 번에 삭제) → Task 4
   - R2 (새 세션 자동 생성) → Task 6/7 (window.location.reload)
   - R3 (버튼 + confirm) → Task 6
   - R4 (env OFF 시 404/미렌더) → Task 3 + Task 4 + Task 6
   - N1 (기본 false) → Task 2
   - N2 (403) → Task 4 Step 1 second test
   - N3 (cascade 처리) → Task 1 + Task 4 (messageRepository.deleteBySessionId)
   - N4 (🧪 마커) → 모든 신규 파일에 주석

2. **Placeholder scan**: TBD/TODO 없음, 모든 코드 블록은 실제 코드. (단, `persistUser`, `persistTestResult` 같은 헬퍼는 구현 시 기존 통합 테스트의 픽스처 패턴을 따라 작성 — 의도된 유연성).

3. **Type consistency**:
   - `resetAiReviewSession(testResultId: number): Promise<void>` — Task 5
   - `TestResetButton({ testResultId }: { testResultId: number })` — Task 6
   - `@PostMapping("/{testResultId}/session/reset")` + `@PathVariable Long testResultId` — Task 4
   - 모두 일치 ✓

4. **Ambiguity check**:
   - 한 가지 보강: Task 7 의 "렌더 위치" 는 구현 시 결정. 구체적인 위치는 page.tsx 의 구조에 따라 달라짐. 명시했음.
   - `ApiResponse.error` 시그니처는 실제 클래스 확인 권장 — Task 4 코멘트로 명시함.

이상 점검 완료. 추가 이슈 발견 없음.

---

## Execution

설계 → 구현으로 넘어가는 사용자 핸드오프:

**"Plan complete and saved to `docs/superpowers/plans/2026-05-27-ai-review-test-session-reset.md`."**

**Two execution options:**

1. **Subagent-Driven (recommended)** — 각 Task 마다 격리된 subagent 발사, Task 간 리뷰, 빠른 반복
2. **Inline Execution** — 본 세션에서 직접 batch 진행, 체크포인트로 리뷰

**어느 쪽으로 진행할지 골라주세요.**
