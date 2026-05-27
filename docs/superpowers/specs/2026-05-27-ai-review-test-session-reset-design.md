# AI 리뷰 테스트용 세션 초기화 — 설계 문서

- 작성일: 2026-05-27
- 작성자: aucu2005@gmail.com
- 상태: 설계 승인 대기 → 구현
- 관련 PR: 본 설계 적용 후 별도 PR로 진행

## 배경 / 동기

AI 복습(꼬리질문) 기능을 다양한 학생 응답 시나리오로 테스트하려고 한다. 현재 흐름:

- `POST /ai-review/test-results/{id}/start` 호출 시 기존 세션이 있으면 **그대로 반환** ([RuleBasedAiReviewService.java:50-58](../../../backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java#L50))
- 한 번 답변하면 메시지가 영구 저장되어 같은 진단평가에서 다른 시나리오 시험 불가
- 현재 우회: **다른 계정으로 재로그인** → 매우 불편

본 문서는 AI 리뷰 세션을 처음부터 다시 시작할 수 있는 **테스트 전용** 기능을 설계한다.

## 목표 / 비목표

### 목표
- 같은 사용자/진단평가에서 AI 리뷰 세션을 처음부터 다시 시작
- 환경변수로 켜고 끌 수 있어 운영 영향 0
- 테스트 종료 후 코드를 깨끗하게 제거 가능 (grep 한 번이면 모든 추가 코드 식별)

### 비목표
- 운영 사용자에게 노출되는 기능
- 세션의 부분 롤백 (마지막 답변만 취소)
- 다른 사용자의 세션 조작

## 요구사항

### 기능 요구
- R1. 본인의 진단평가 결과에 연결된 AI 리뷰 세션을 한 번에 삭제
- R2. 세션 삭제 후 페이지 새로고침 → 새 세션이 자동 생성됨 (기존 `/start` 흐름 활용)
- R3. UI에 "🧪 세션 초기화 (테스트)" 버튼 노출. confirm 다이얼로그 후 진행
- R4. 환경변수 OFF 시 백엔드 엔드포인트 404, 프론트 버튼 미렌더

### 비기능 요구
- N1. 운영 환경에서 환경변수 기본값 false
- N2. 다른 사용자의 testResult 세션 삭제 시도 시 403
- N3. 삭제는 세션 본체와 cascade 관계 (messages 등) 모두 처리
- N4. 모든 신규 코드에 `🧪 테스트 전용` 마커 주석 → 제거 시 grep 검색 1회로 발견 가능

## 아키텍처

```
[학생 브라우저]
  ├─ AI 리뷰 화면 (review/page.tsx)
  │     └─ TestResetButton 컴포넌트
  │          └─ NEXT_PUBLIC_AI_REVIEW_TEST_RESET=true 일 때만 렌더
  │     ▼ 클릭 + confirm
  │
  └─ POST /api/ai-review/test-results/{testResultId}/session/reset

         ▼

[Spring 백엔드 (8080)]
  ├─ TestResetAiReviewController (신규)
  │     └─ @ConditionalOnProperty(app.ai-review.test-reset.enabled=true)
  │           → 환경변수 OFF 시 컨트롤러 등록 안 됨 → 404
  │
  ▼
  ├─ 소유권 검증 (testResult.user.id == 로그인 유저 id)
  ├─ sessionRepository.findTopByUserIdAndTestResultIdOrderByCreatedAtDesc(...)
  ├─ 있으면 .delete() (cascade 로 messages/summaries 정리)
  │       (cascade 누락 시 messageRepository.deleteBySessionId(...) 명시 호출)
  └─ 200 OK

[학생 브라우저]
  └─ window.location.reload() → /start 가 새 세션 생성 → 첫 단계로 복귀
```

## 컴포넌트 명세

### Backend

**신규 파일**: `backend/src/main/java/com/devmatch/controller/TestResetAiReviewController.java`

```java
// 🧪 테스트 전용: AI 리뷰 세션 초기화
// 제거 방법은 본 파일 상단 주석 + docs/superpowers/specs/2026-05-27-* 참고

@RestController
@RequestMapping("/api/ai-review/test-results")
@ConditionalOnProperty(name = "app.ai-review.test-reset.enabled", havingValue = "true")
public class TestResetAiReviewController {
    private final AiReviewSessionRepository sessionRepository;
    private final AiReviewMessageRepository messageRepository;  // cascade 누락 대비
    private final TestResultRepository testResultRepository;

    @PostMapping("/{testResultId}/session/reset")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> resetSession(
        @PathVariable Long testResultId,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();

        // 소유권 검증
        TestResult result = testResultRepository.findById(testResultId)
            .orElseThrow(() -> new TestNotFoundException("..."));
        if (!Objects.equals(result.getUser().getId(), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(...);
        }

        // 세션 찾아서 삭제
        sessionRepository
            .findTopByUserIdAndTestResultIdOrderByCreatedAtDesc(userId, testResultId)
            .ifPresent(session -> {
                // cascade 미설정 케이스 대비
                messageRepository.deleteBySessionId(session.getId());
                sessionRepository.delete(session);
            });

        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

**기존 파일 1줄 수정**: `backend/src/main/resources/application.yml`
```yaml
app:
  ai-review:
    test-reset:
      enabled: ${AI_REVIEW_TEST_RESET_ENABLED:false}  # 🧪 테스트 전용
```

**구현 시 확인 사항**:
- `AiReviewSession` entity의 messages/summaries 관계가 `cascade = REMOVE` 또는 `orphanRemoval = true` 인지 확인. 없으면 위 명시 호출로 대응.
- `messageRepository.deleteBySessionId(...)` 메서드 존재 확인 (없으면 추가 또는 다른 패턴 사용).

### Frontend

**신규 파일**: `frontend/src/components/ai-review/TestResetButton.tsx`

```tsx
// 🧪 테스트 전용: AI 리뷰 세션 초기화 버튼
// 제거 시 본 파일 + page.tsx의 import/render + ai-review.ts의 resetAiReviewSession 함께 삭제

'use client';

import { useState } from 'react';
import { resetAiReviewSession } from '@/lib/ai-review';

interface TestResetButtonProps {
  testResultId: number;
}

export function TestResetButton({ testResultId }: TestResetButtonProps) {
  if (process.env.NEXT_PUBLIC_AI_REVIEW_TEST_RESET !== 'true') {
    return null;
  }

  const [resetting, setResetting] = useState(false);

  const handleReset = async () => {
    if (!confirm('AI 리뷰 세션을 처음부터 다시 시작합니다. 정말 진행할까요?')) {
      return;
    }
    setResetting(true);
    try {
      await resetAiReviewSession(testResultId);
      window.location.reload();
    } catch (e) {
      alert('초기화 실패: ' + (e as Error).message);
      setResetting(false);
    }
  };

  return (
    <button
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

**기존 파일 함수 추가**: `frontend/src/lib/ai-review.ts`
```typescript
// 🧪 테스트 전용: 세션 초기화 API — 제거 시 이 함수도 같이 삭제
export async function resetAiReviewSession(testResultId: number): Promise<void> {
  await apiClient.post(`/ai-review/test-results/${testResultId}/session/reset`);
}
```

**기존 파일 2줄 수정**: `frontend/src/app/tests/results/[id]/review/page.tsx`
```tsx
// 상단 import 추가:
import { TestResetButton } from '@/components/ai-review/TestResetButton';

// 적절한 위치 (상단 헤더 영역) 에 렌더:
<TestResetButton testResultId={Number(params.id)} />
```

## 데이터 흐름

1. 학생이 진단평가 결과 → AI 리뷰 화면 진입
2. 환경변수 ON 이면 🧪 버튼 표시
3. 학생이 버튼 클릭 → confirm 띄움
4. 확인 시 `POST /ai-review/test-results/{id}/session/reset` 호출
5. 백엔드: 소유권 검증 → 세션 + 관련 메시지 삭제 → 200 OK
6. 프론트: 페이지 새로고침
7. 새로고침이 트리거하는 `/start` 호출이 신규 세션 생성 → 첫 꼬리질문 등장

## 에러 처리

| 상황 | 응답 | 프론트 처리 |
|---|---|---|
| 환경변수 OFF + 호출 시도 | 404 (엔드포인트 없음) | alert로 "기능 비활성" |
| 다른 사람 testResult ID | 403 | alert로 "권한 없음" |
| testResult 없음 | 404 | alert로 "데이터 없음" |
| 세션 없음 (정상 case) | 200 (no-op) | 새로고침 진행 |
| DB 삭제 실패 | 500 | alert로 에러 메시지 표시 |

## 테스트 계획

### 단위 검증
- 환경변수 OFF 상태에서 컨트롤러 빈이 등록되지 않는지 (ApplicationContextRunner 사용)
- 환경변수 ON 상태에서 정상 경로 200, 다른 유저 testResult 시 403
- 세션 없는 경우 200 (no-op)
- 세션 + 메시지 삭제 후 row count 0 확인

### 통합 검증 (수동)
1. `AI_REVIEW_TEST_RESET_ENABLED=false`, `NEXT_PUBLIC_AI_REVIEW_TEST_RESET` 미설정 → 운영과 동일
2. `AI_REVIEW_TEST_RESET_ENABLED=true`, `NEXT_PUBLIC_AI_REVIEW_TEST_RESET=true` → 버튼 표시 + 동작
3. 다양한 답변 시나리오 (모름/부분정답/오개념/자신있게오답) 별로 reset → 답변 → 비교 가능

## 운영 보호

- 백엔드 `@ConditionalOnProperty(havingValue = "true")` — false/누락 시 빈 등록 안 됨
- 프론트 `process.env.NEXT_PUBLIC_AI_REVIEW_TEST_RESET !== 'true'` 가드
- 양쪽 환경변수 기본값 false (또는 미설정)
- `.env.example` 등에 명시적으로 false 표기

## 제거 가이드

테스트 종료 후 본 기능 완전 제거 절차:

### 자동 검색
```powershell
grep -rln "🧪 테스트 전용" backend/ frontend/
grep -rn "TestReset\|test-reset\|TEST_RESET\|resetAiReviewSession" backend/ frontend/ --include="*.java" --include="*.ts" --include="*.tsx" --include="*.yml"
```

### 제거 항목 (순서대로)

1. **삭제**: `backend/src/main/java/com/devmatch/controller/TestResetAiReviewController.java`
2. **편집**: `backend/src/main/resources/application.yml` → `test-reset` 3줄 제거
3. **삭제**: `frontend/src/components/ai-review/TestResetButton.tsx`
4. **편집**: `frontend/src/app/tests/results/[id]/review/page.tsx` → import + render 2줄 제거
5. **편집**: `frontend/src/lib/ai-review.ts` → `resetAiReviewSession` 함수 제거
6. **편집**: `.env.local`, `.env.example` 등에서 `AI_REVIEW_TEST_RESET_ENABLED`, `NEXT_PUBLIC_AI_REVIEW_TEST_RESET` 제거
7. **검증**: 위 grep 결과 0건 확인

권장: **별도 PR**로 제거 (한 번에 깔끔히, 리뷰 받기 좋음)

## 위험 평가

| 위험 | 확률 | 영향 | 완화 |
|---|---|---|---|
| 운영 환경에 환경변수 잘못 켜짐 | 낮음 (기본 false) | 학생이 자기 세션 삭제 가능 | `@ConditionalOnProperty` + `.env.example`에 false 명시 |
| 환경변수 켰는데 다른 유저 세션 삭제 시도 | 매우 낮음 | 거부됨 | 소유권 검증 |
| 테스트 후 제거 안 됨 | 보통 | dead code 잔존 | 마커 주석 + 본 문서의 제거 가이드 |
| Cascade 설정 누락으로 orphan 메시지 잔존 | 낮음 | DB에 고아 row | `messageRepository.deleteBySessionId` 명시 호출 |
| 시간 차이로 학생이 reset 중 다른 탭에서 답변 제출 | 매우 낮음 | race condition, 일부 데이터 남을 수 있음 | `@Transactional` 동일 트랜잭션 |

## 향후 작업

본 설계는 단기 테스트 도구로 한정. 다음 후속 작업 가능성:

- **자동화된 회귀 테스트 도구**: 본 reset 기능을 시드 데이터 자동 주입과 결합해 e2e 테스트로 발전
- **관리자 도구로 승격**: 운영 중에도 admin이 학생 세션 트러블슈팅 시 사용 가능하게 확장
- 단, 어느 쪽도 별도 설계 필요. 본 기능 자체는 그대로 제거 대상.

## 참고

- 관련 컨트롤러: [backend/src/main/java/com/devmatch/controller/AiReviewController.java](../../../backend/src/main/java/com/devmatch/controller/AiReviewController.java)
- 관련 서비스: [backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java](../../../backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java)
- 프론트엔드 리뷰 페이지: [frontend/src/app/tests/results/[id]/review/page.tsx](../../../frontend/src/app/tests/results/[id]/review/page.tsx)
- 프론트엔드 API 클라이언트: [frontend/src/lib/ai-review.ts](../../../frontend/src/lib/ai-review.ts)
