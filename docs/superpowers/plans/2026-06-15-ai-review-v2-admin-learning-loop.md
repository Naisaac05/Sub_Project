# AI Review v2 Admin Learning Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자 승인 후보를 최소 v2 JSON 카드로 안전하게 발행하고, 카드 미존재 질문 학습 루프와 문맥 있는 꼬리질문을 제공하며, 로컬 Ollama judge를 기본 비활성화한다.

**Architecture:** Spring 백엔드는 승인 전 v2 카드 발행을 수행하고 발행 성공 후에만 후보를 승인한다. Python AI 런타임은 v2 Fast Path miss 정보로 후보 수집을 제한하고, 꼬리질문에 직전 AI 질문과 활성 개념만 전달하며 judge 환경 플래그를 존중한다. 관리자 UI는 DB 후보의 검토·발행 상태를 직접 표시한다.

**Tech Stack:** Java 17, Spring Boot 3.5, JPA, Jackson, Python 3/Pydantic/FastAPI, Next.js 14/TypeScript

---

### Task 1: v2 카드 Publisher

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexer.java`
- Modify: `backend/src/test/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexerTest.java`

- [ ] 승인 후보가 `concepts_v2/<category>/<card_id>.json` 최소 카드로 발행되는 실패 테스트를 작성한다.
- [ ] 테스트를 실행해 기존 v1 Markdown 출력 때문에 실패하는지 확인한다.
- [ ] `CONCEPT_DEFINITION`만 승인된 JSON 생성, Jackson 역직렬화 검증, 임시 파일과 atomic move를 구현한다.
- [ ] 동일 후보 재발행과 다른 출처 카드 충돌 테스트를 추가하고 통과시킨다.
- [ ] Chroma 및 v1 manifest 갱신 동작을 제거한 뒤 집중 테스트를 실행한다.

Run:
```powershell
.\gradlew.bat test --tests com.devmatch.service.LoggingAiReviewKnowledgeReindexerTest
```

### Task 2: 발행 성공 후 승인 상태 확정

**Files:**
- Modify: `backend/src/main/java/com/devmatch/entity/AiReviewCandidate.java`
- Modify: `backend/src/main/java/com/devmatch/entity/AiReviewCandidateWorkflowPhase.java`
- Modify: `backend/src/main/java/com/devmatch/dto/aireview/candidate/AiReviewCandidateV2Response.java`
- Modify: `backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java`
- Modify: `backend/src/test/java/com/devmatch/service/AiReviewCandidateApprovalV2ServiceTest.java`

- [ ] publisher 실패 시 후보가 `PENDING / PUBLISH_FAILED`로 남는 실패 테스트를 작성한다.
- [ ] 발행 경로, 카드 ID, 오류를 후보와 응답에 저장하도록 엔티티와 DTO를 확장한다.
- [ ] 승인 요청에서 발행을 먼저 수행하고 성공 후 승인·감사 로그를 저장하도록 순서를 변경한다.
- [ ] 성공·실패·재시도 집중 테스트를 실행한다.

Run:
```powershell
.\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateApprovalV2ServiceTest
```

### Task 3: 문맥 있는 꼬리질문

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java`
- Modify: `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java`
- Modify: `backend/src/test/java/com/devmatch/service/ai/RuleBasedAiReviewServiceTest.java`
- Modify: `ai/app/schemas/__init__.py`
- Modify: `ai/app/prompts/__init__.py`
- Modify: `ai/tests/test_workflow_runner.py`
- Modify: `ai/tests/test_service_helpers.py`

- [ ] 진단 follow-up 요청에 `previous_ai_question`, `active_concept`, `follow_up_type`이 포함되는 실패 테스트를 작성한다.
- [ ] 백엔드에서 최신 AI 질문과 활성 개념을 추출해 Python 요청으로 전달한다.
- [ ] Python 스키마와 prompt가 세 필드를 사용하도록 구현한다.
- [ ] 자유 질문 확인 질문은 `FREE_QUESTION_CHECK`, 진단은 `DIAGNOSTIC_FOLLOW_UP`으로 구분한다.
- [ ] follow-up prompt 및 서비스 테스트를 실행한다.

### Task 4: 후보 수집 범위와 로컬 judge OFF

**Files:**
- Modify: `ai/app/knowledge/auto_candidates.py`
- Modify: `ai/app/workflow/graph.py`
- Modify: `ai/app/workflow/judge.py`
- Modify: `ai/app/workflow/grounding.py`
- Modify: `ai/app/workflow/nodes.py`
- Modify: `ai/tests/test_workflow_runner.py`
- Modify: `ai/tests/test_adaptive_judge.py`

- [ ] v2 Fast Path 허용 miss만 후보로 저장하고 follow-up을 제외하는 실패 테스트를 작성한다.
- [ ] 후보 수집 판단에 `v2_fast_path_decision.reason`을 전달하고 허용 목록을 적용한다.
- [ ] `AI_REVIEW_SEMANTIC_JUDGE_ENABLED`와 `AI_REVIEW_GROUNDING_JUDGE_ENABLED` 기본 false 테스트를 작성한다.
- [ ] 비활성화 시 Ollama를 호출하지 않는 skipped 결과를 구현한다.
- [ ] Python 집중 테스트를 실행한다.

Run:
```powershell
.\.venv\Scripts\python.exe -m unittest ai.tests.test_workflow_runner ai.tests.test_adaptive_judge ai.tests.test_service_helpers
```

### Task 5: 관리자 후보 화면 정리

**Files:**
- Modify: `frontend/src/lib/admin/aiReviewCandidates.ts`
- Modify: `frontend/src/app/admin/ai-review-candidates/page.tsx`

- [ ] DTO에 workflow phase와 발행 정보를 추가한다.
- [ ] 깨진 한글을 정상화하고 상태 필터를 승인 대기, 검토 중, 반영 완료, 반영 실패, 거절, 병합으로 변경한다.
- [ ] 발행 카드 ID·경로·오류를 상세 화면에 표시한다.
- [ ] JSONL 가져오기와 일괄 승인을 일반 운영 화면에서 제거한다.
- [ ] TypeScript/Next build를 실행한다.

Run:
```powershell
npm run build
```

### Task 6: 통합 검증과 운영 문서

**Files:**
- Create: `docs/ai-review-v2-admin-learning-loop.md`
- Create when root cause is fixed: `error/2026-06-15-ai-review-admin-approved-v1-card.md`
- Modify: `error/README.md`

- [ ] 백엔드 AI review 집중 테스트를 실행한다.
- [ ] Python Fast Path, 후보 수집, follow-up, judge 집중 테스트를 실행한다.
- [ ] 프론트엔드 build와 관리자 페이지 수동 확인을 수행한다.
- [ ] 카드 미존재 질문 → 후보 → 관리자 승인 → v2 Fast Path E2E를 실행한다.
- [ ] 운영 흐름과 로컬 judge 설정을 문서화한다.
- [ ] v1 카드 발행 근본 원인과 해결을 `error/`에 기록한다.
