# AI review current problem N+1 blocked

- 발생 일시: 2026-06-25
- 영역: ai / backend
- 심각도: high

## 증상
스킬 테스트 Q3 `JPA의 N+1 문제를 줄이기 위한 방법으로 가장 적절한 것은 무엇인가요?` 화면에서 학습자가 `N+1이 뭐야?`라고 물으면, 현재 문제의 핵심 개념인데도 AI가 `현재 코스 복습 범위 밖의 기술 주제라 여기서는 답변하지 않을게요` 또는 `승인된 학습 근거만으로는 정확한 답변을 제공하기 어렵습니다`라고 응답했다.

## 원인
Python AI의 코스 범위 게이트가 승인 카드 검색 결과만 기준으로 판단했다. `N+1` 승인 v2 카드는 없고 `spring-jpa` 카드는 draft 상태라 fast-path 근거로 쓰이지 못했다. 여기에 RAG 토크나이저가 `O(n^2)` 같은 일반 `n` 토큰도 `n+1`로 확장해 `algorithm-2` 같은 다른 코스 카드를 오탐했고, 이 결과가 `out_of_course_redirect`로 이어졌다.

토큰 오탐을 제거한 뒤에도 현재 문제 문맥에 포함된 질문이 `course_card_miss`가 되면 grounded fallback 안전 게이트가 `missing_approved_evidence`로 다시 막았다. 마지막으로 품질 검증 fallback 경로가 일반 안내 문구를 앞에 붙여 실제 `N+1` 설명보다 “질문을 더 구체적으로 작성” 안내가 먼저 노출됐다.

## 해결 방법
`n` 단일 토큰을 `n+1`로 확장하지 않도록 토큰화 로직을 수정했다: `ai/app/rag/retriever.py:539`.

학습자 질문이 현재 문제 본문, 보기, 정답, 선택 답안과 겹치면 승인 카드가 없거나 다른 코스 카드가 오탐되어도 `current_problem_context`로 처리해 Ollama 생성 및 현재 문제 fallback으로 이어지게 했다: `ai/app/workflow/nodes.py:678`, `ai/app/workflow/nodes.py:699`, `ai/app/workflow/runner.py:388`.

품질 검증 fallback에서도 `current_problem_context`인 경우 일반 안내 문구 대신 현재 문제 주제 설명을 바로 보여주도록 조정했다: `ai/app/workflow/nodes.py:552`, `ai/app/workflow/semantic_gate.py:20`.

`N+1` 질문은 승인 카드가 없어도 최소한 현재 문제의 정답 기준과 함께 설명하도록 전용 fallback을 추가했다: `ai/app/workflow/nodes.py:800`.

회귀 테스트를 추가했다: `ai/tests/test_rag_retriever.py:221`, `ai/tests/test_workflow_runner.py:208`.

## 재발 방지 / 메모
`N+1이 뭐야?` 케이스를 API로 확인했을 때 수정 코드에서는 `current_problem_context` 플래그와 함께 `N+1 문제는 목록을 조회하는 1번의 쿼리 이후... fetch join 또는 EntityGraph...` 응답이 반환됐다.

현재 로컬 8001 포트는 이전 Python AI 프로세스가 계속 점유하고 있었고, Codex 권한에서는 종료할 수 없었다. 실제 화면 반영을 위해서는 사용자가 8001을 띄운 터미널을 종료한 뒤 `uvicorn app.main:app --host 127.0.0.1 --port 8001`로 다시 시작해야 한다.
