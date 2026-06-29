# AI review current problem semantic false contradiction

- 발생 일시: 2026-06-25
- 영역: ai
- 심각도: high

## 증상

로컬 Python AI와 Ollama를 정상 실행한 뒤 현재 문제에서 `N+1이 뭐야?`, `Spring Security가 뭐지?` 같은 질문을 던져도 화면에는 계속 fallback 답변처럼 보였다.

직접 `http://127.0.0.1:8001/api/review/free-question`에 요청했을 때 N+1 케이스는 `course_scope_applied`, `missing_approved_evidence`, `current_problem_context`까지는 붙었지만 최종 응답이 `route=fallback_template`, `model_used=template`, `fallback_used=true`였고 `contradiction_suspected`가 추가되어 있었다.

## 원인

두 가지 판정이 겹쳤다.

첫째, 자유질문 분류기가 `Spring Security가 뭐지?`를 `unknown/fallback`으로 분류하면 코스 범위 판정이 `not_applicable/unsupported_intent`에서 끝나 현재 문제 텍스트와 겹쳐도 `current_problem_context`로 승격되지 않았다. 관련 경로는 `ai/app/workflow/nodes.py:683`이다.

둘째, semantic judge가 검색된 RAG 카드가 없을 때 답변에 `N+1`, `fetch join` 같은 known concept marker가 나오면 무조건 `contradiction_suspected`를 붙였다. 현재 문제 자체가 해당 개념을 포함하는 상황에서도 같은 규칙이 적용되어, Ollama가 답을 생성한 뒤 `ai/app/workflow/semantic_gate.py`에서 다시 `fallback_template`으로 교체됐다. 관련 규칙은 `ai/app/evaluation/semantic.py:51`이다.

## 해결 방법

`ai/app/workflow/nodes.py:683`에서 범위 판정 결과가 `not_applicable`이더라도 학습자 질문 토큰이 현재 문제/정답/선택지와 겹치면 `current_problem_context`로 승격하도록 했다.

`ai/app/workflow/nodes.py:630`에서 자유질문 intent가 `latest_question_only`가 아니어도 topic-specific fallback을 먼저 시도하도록 바꿔, unknown intent라도 `Spring Security`, `N+1` 같은 현재 문제 주제는 구체 답변을 만들 수 있게 했다.

`ai/app/evaluation/semantic.py:51`에서 `current_problem_context`가 이미 붙은 답변은 검색되지 않은 known concept 언급만으로 `contradiction_suspected`를 붙이지 않도록 했다.

회귀 테스트를 추가했다.

- `ai/tests/test_workflow_runner.py:257`
- `ai/tests/test_semantic_evaluation.py:77`

검증:

- `.\.venv\Scripts\python.exe -m pytest tests\test_workflow_runner.py -k "unknown_intent_current_problem_overlap or current_problem_topic or spring_security_topic or missing_approved_evidence or timeout_retries" -q`
- `.\.venv\Scripts\python.exe -m pytest tests\test_semantic_evaluation.py tests\test_rag_retriever.py tests\test_course_scope_gate.py -q`
- AI 서버 재시작 후 `N+1이 뭐야?` 직접 호출 결과: `route=generation`, `model_used=exaone3.5:2.4b`, `fallback_used=false`
- AI 서버 재시작 후 `Spring Security가 뭐지?` 직접 호출 결과: `route=generation`, `model_used=exaone3.5:2.4b`, `fallback_used=false`

## 재발 방지 / 메모

현재 문제에서 파생된 자유질문은 승인 카드 검색 miss와 별개로 문제 본문/정답/선택지를 근거로 삼을 수 있다. 따라서 `missing_approved_evidence`와 `current_problem_context`가 함께 있는 상태를 일반 RAG miss와 동일하게 처리하면 안 된다.

`uvicorn --reload`가 켜져 있어도 이미 떠 있는 프로세스가 이전 모듈 상태로 응답할 수 있으므로, 판정 로직을 고친 뒤에는 `8001` 프로세스를 재시작하고 실제 API 응답의 `route`, `model_used`, `fallback_used`, `quality_flags`를 확인해야 한다.
