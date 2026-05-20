# AI review Python vague free-question fallback

- 발생 날짜: 2026-05-19
- 범위: ai / backend
- 심각도: medium

## 증상

AI 복습 화면의 `궁금한 점 질문하기`에서 `@Transactional이 뭐야?` 같은 짧은 기술 개념 질문을 하면, 구체 설명 대신 "`@Transactional에 대한 질문으로 이해했어요... 정의, 쓰이는 상황, 헷갈리는 개념...`" 형태의 일반 안내 문구가 반환됐다.

## 원인

실제 문구는 Spring fallback이 아니라 `ai/app/workflow/nodes.py`의 Python workflow fallback에서 생성됐다. `@Transactional`과 계층 구조가 `ai/app/workflow/lightweight_answers.py`의 static fast-path 지식 사전에 없어서, generator 응답이 낮은 품질로 판정되면 승인된 지식 카드나 정적 답변 없이 일반 fallback 템플릿으로 내려갔다.

또한 fallback 주제 판별에 현재 문제 전체 맥락을 과하게 섞으면, 자유 질문이 원래 문제의 선택 오답으로 다시 끌려가는 회귀 위험이 있었다.

## 해결 방법

`ai/app/workflow/lightweight_answers.py`에 `@Transactional`과 계층 구조 static fast-path 답변 및 alias를 추가했다. `ai/app/workflow/nodes.py`의 topic-specific fallback에도 같은 개념을 보강했고, 최후 fallback은 더 이상 "질문으로 이해했어요" 문구를 쓰지 않고 현재 문제의 정답 기준 중심으로 답하도록 바꿨다.

회귀 테스트는 `ai/tests/test_workflow_runner.py`에 추가했다. `@Transactional이 뭐야?`, `계층은 어떻게 있나요?`가 static fast-path로 처리되고 일반 fallback 문구가 포함되지 않는지 확인한다.

## 재발 방지 / 메모

자주 나오는 짧은 기술 용어 질문은 LLM 생성 실패 시에도 static fast-path 또는 승인된 concept card로 답해야 한다. fallback에서 원래 문제의 선택 오답을 다시 문장에 넣으면 자유 질문 drift가 재발할 수 있으므로, 최후 fallback은 정답 기준만 참고한다.
