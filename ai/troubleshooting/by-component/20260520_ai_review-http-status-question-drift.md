---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review HTTP status question drift 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review HTTP status question drift

- 발생 날짜: 2026-05-20
- 영역: ai
- 심각도: medium

## 증상

AI 복습 화면의 "궁금한 점 질문하기"에서 `204 No Content는 어떤 상태 코드인가요?`라고 물었는데, 204 자체의 의미를 답하지 않고 `201 Created가 왜 맞는지와 204 No Content가 어떤 개념을 놓쳤는지...`처럼 원래 문제의 정답/오답 비교로만 답했다.

## 원인

`ai/app/workflow/lightweight_answers.py`의 경량 답변 테이블에 HTTP 상태 코드가 없었다. 그래서 `204 No Content` 질문이 `static_fast_path`로 처리되지 못하고 generator 경로로 내려갔고, generator가 현재 문제 맥락의 `201 Created`와 `204 No Content` 차이에 끌려가 사용자가 물은 코드 정의를 먼저 답하지 못했다.

## 해결 방법

`ai/app/workflow/lightweight_answers.py`에 REST API에서 자주 나오는 HTTP 상태 코드 fast-path를 추가했다.

- `200 OK`
- `201 Created`
- `204 No Content`
- `400 Bad Request`
- `401 Unauthorized`
- `403 Forbidden`
- `404 Not Found`
- `500 Internal Server Error`

`ai/tests/test_workflow_runner.py`에 `204 No Content`와 `201 Created` 질문이 `static_fast_path`로 처리되고, 사용자가 물은 코드의 의미를 먼저 답하는지 검증하는 회귀 테스트를 추가했다.

## 재발 방지 / 메모

자유 질문이 원래 문제의 정답/오답을 포함하더라도, 사용자가 특정 용어 또는 상태 코드를 직접 물으면 해당 용어의 정의를 먼저 답해야 한다. 시험에 자주 나오는 짧은 표준 용어는 RAG/generator보다 curated fast-path에 우선 추가한다.
