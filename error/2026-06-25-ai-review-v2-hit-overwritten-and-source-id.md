# AI review v2 hit overwritten and source ID shown

- 발생 일시: 2026-06-25
- 영역: ai / backend / admin
- 심각도: medium

## 증상
AI 리뷰 채팅에서 `conditional-rendering이 뭔가요?`처럼 승인된 `concepts_v2` 카드가 있는 질문을 했는데도, 답변이 승인 카드 내용으로 나오지 않고 "더 정확한 답변을 준비하고 있습니다..." fallback 문구로 표시됐다.

관리자 AI 후보 화면에서는 `frontend-conditional-rendering` 카드의 원문 질문이 실제 문항이 아니라 `frontend:67` 같은 source ID로 표시됐다.

## 원인
Python AI 동기 API는 LangGraph 경로를 타고 있었다. 이 경로는 `generate_answer`에서 `v2_approved_fast_path` hit가 발생해도 무조건 `validate_answer`와 `fallback_answer`로 계속 진행했다. 그 결과 승인 카드 hit 자체는 성공했지만, 뒤쪽 품질 검증이 현재 Java 문제 맥락 기준으로 `stale_original_context`, `missing_topic`을 붙여 승인 카드 답변을 fallback 템플릿으로 덮어썼다.

관리자 후보 목록은 승인 카드 JSON의 `source_question_ids` 첫 값을 그대로 `sourceQuestion`에 매핑했다. `frontend:67`은 사람이 읽는 질문 문장이 아니라 카드 출처를 나타내는 논리 ID라 화면에 그대로 노출되면 어떤 질문에서 나온 카드인지 알 수 없었다.

## 해결 방법
LangGraph 실행 경로에서 `generate_answer` 이후 `state.route`가 `v2_approved_fast_path`이면 검증/fallback 노드를 건너뛰고 `cache_answer`로 진행하도록 분기했다. 순차 실행 경로도 같은 route에서 즉시 반환하도록 맞췄다.

- `ai/app/workflow/graph.py:82`
- `ai/app/workflow/graph.py:214`
- `ai/app/workflow/runner.py:568`
- `ai/tests/test_workflow_runner.py:297`

관리자 후보 응답은 카드 JSON의 `source_question` 또는 `source_question_text`를 우선 사용하고, 없을 때만 기존 `source_question_ids`로 fallback하도록 바꿨다. 문제 카드에는 실제 원문 질문을 추가했다.

- `backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java:386`
- `backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java:412`
- `backend/src/test/java/com/devmatch/service/AiReviewCandidateApprovalV2ServiceTest.java:65`
- `ai/app/knowledge/concepts_v2/frontend/frontend-conditional-rendering.json:18`

검증:

- `.venv\Scripts\python.exe -m pytest tests/test_workflow_runner.py -k sync_v2_approved_fast_path_hit_is_not_overwritten_by_quality_fallback`
- `.venv\Scripts\python.exe -m pytest tests/test_v2_approved_fast_path.py -k serve_stream_returns_approved_payload_without_generator`
- `.\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateApprovalV2ServiceTest`
- 로컬 Python AI `POST /api/review/free-question` 재검증 결과: `route=v2_approved_fast_path`, `fallback_used=false`, `matched_concept_id=frontend-conditional-rendering`
- 백엔드 관리자 API 재검증 결과: `frontend-conditional-rendering.sourceQuestion`이 `frontend:67` 대신 실제 질문 문장으로 반환됨

## 재발 방지 / 메모
승인 카드 fast path는 이미 검증된 payload를 서빙하는 경로이므로, 후속 품질 검증이 원문 문제 맥락으로 다시 판단해 덮어쓰면 안 된다. 스트리밍 경로와 동기 graph 경로의 early return 동작이 계속 동일한지 회귀 테스트를 유지한다.

`source_question_ids`는 기계용 provenance ID이고, 관리자 화면에는 표시용 `source_question`을 우선 노출한다. 새 카드 생성/승인 과정에서도 가능한 한 표시용 원문 질문 필드를 함께 보존한다.
