---
type: report
category: rag
status: complete
updated: 2026-06-23
description: "approved RAG 카드 term alignment 보강 결과"
---

# RAG 카드 term alignment 보강 결과

## 대상

- 카드: `python-asyncio`
- 상태: approved
- payload: `CONCEPT_DEFINITION`

## 문제

기존 첫 문장은 `await` 설명으로 시작했다. 카드 term은 `asyncio`인데 첫 문장이 `await` 중심이라, 근거 추출 답변이 질문 주제보다 세부 키워드 중심으로 보일 수 있었다.

## 변경

`CONCEPT_DEFINITION.content` 첫 문장을 `asyncio는 ...` 형태로 보강했다.

현재 첫 문장:

```text
asyncio는 Python에서 이벤트 루프와 코루틴으로 비동기 I/O 작업을 조율하는 표준 라이브러리이며, `await`는 awaitable이 완료될 때까지 현재 코루틴을 일시 중단하고 이벤트 루프에 제어권을 돌려줘 다른 준비된 작업이 실행될 수 있게 한다.
```

검색 필드, 승인 상태, payload 승인 상태는 변경하지 않았다.

## 추가 보강

근거 추출 답변의 표시명 선택도 함께 조정했다.

- 질문형 alias는 표시명 후보에서 제외한다.
- 단일 canonical term이 alias에 그대로 있으면 term을 우선 사용한다.
- 근거 첫 문장이 이미 topic으로 시작하면 `topic은/는`을 중복으로 붙이지 않는다.

## 검증

- `tests.test_rag_card_term_alignment`
- `tests.test_grounded_fallback`
- `scripts/lint_knowledge_cards.py`

`python-asyncio` grounded answer 초안은 `asyncio는 ...`으로 시작하고 품질 게이트를 통과한다.
