---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "승인한 지식 카드가 실행 중인 AI 서비스에 반영되지 않음 (lrucache 로 카드 목록 고정) 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# 승인한 지식 카드가 실행 중인 AI 서비스에 반영되지 않음 (lru_cache 로 카드 목록 고정)

- 발생 일시: 2026-05-20
- 영역: ai (FastAPI)
- 심각도: high

## 증상

관리자에서 hashCode 지식 후보를 "수정 및 승인"해서 상태가 `승인됨`이 되고
지식 카드 파일(`ai/app/knowledge/concepts/generated/auto-review-hashcode.md`)도 정상 생성됐다.
그런데 사용자가 다시 "hashCode가 무엇인가요?"를 물어도 AI 답변이 승인 전과 똑같은
fallback("...승인된 지식 카드가 아직 부족해서...")로 나오고, 승인 내용이 답변에 반영되지 않았다.

## 원인

승인된 카드를 그대로 돌려주는 빠른 경로(`generated_card_fast_path`)는
`ai/app/workflow/lightweight_answers.py` 의 `_concept_card_answer_for` → `_concept_cards_by_id()` 를 통해
디스크의 concept 카드를 읽는다. 그런데 `_concept_cards_by_id()` 에 `@lru_cache(maxsize=1)` 가 걸려 있어
**FastAPI 프로세스가 살아있는 동안 카드 목록이 최초 1회 로딩 값으로 고정**된다.

타임라인:
1. AI 서비스 기동 → 첫 free-question 처리 중 `_concept_cards_by_id()` 가 호출되어 카드 목록이 캐시됨(이 시점에 hashCode 카드 없음).
2. 관리자가 hashCode 승인 → 디스크에 카드 파일 생성.
3. 같은 질문 재시도 → `_concept_cards_by_id()` 가 **stale 캐시**(hashCode 없음)를 반환 → fast path 미적용 → generation/fallback → 승인 전과 동일한 답변.

참고로 일반 retriever(`app/rag/retriever.py`)와 `load_concept_cards`(`app/rag/documents.py`)는 캐시가 없어
매 요청 새로 읽는다. 즉 이 lru_cache 하나만 카드 상태를 얼려서, 승인 결과가 서비스 재시작 전까지 안 보였다.

검증: 새 Python 프로세스(캐시 비어있음)로 동일 질문을 돌리면
`route=generated_card_fast_path`, 답변이 승인된 카드 핵심 설명 그대로 반환됨을 확인.

## 해결 방법

`_concept_cards_by_id()` 의 `@lru_cache(maxsize=1)` 제거 → 호출마다 디스크에서 최신 카드 로딩.
- [lightweight_answers.py:274](../ai/app/workflow/lightweight_answers.py:274) 데코레이터 및 `from functools import lru_cache` import 제거
- 카드 수가 적어(<10개) 매 요청 재로딩 비용은 무시 가능하고, retriever 등 다른 경로와 동작이 일관됨.

테스트: `python -m unittest discover -s tests` 기준, 본 변경 전/후 실패 테스트 동일(아래 메모 참조) — 회귀 없음(failure-neutral).

**적용하려면 FastAPI(AI) 서비스를 재시작해야 한다.** 재시작 후에는 lru_cache 가 없으므로,
이후 승인은 서비스 재시작 없이 즉시 반영된다. (단, 이미 떠 있던 기존 프로세스에는 코드 변경이 반영되지 않음 → 1회 재시작 필요)

## 재발 방지 / 메모

- 이미 떠 있는 AI 서비스의 경우, 코드 반영을 위해 1회 재시작이 필요. 재시작은 in-memory 캐시도 비운다.
- **이번 변경과 무관한 기존(HEAD) 실패 테스트** (현재 디스크 상태에서 원본 코드로도 동일하게 실패):
  - `tests/test_workflow_runner.py::test_successful_generation_returns_metadata`,
    `::test_generated_answer_returns_rag_generation_route` — N+1/equals 질문이 `generated_card_fast_path`로
    가로채져 `rag_generation` 기대와 불일치. fast-path 도입 후 갱신 안 된 stale 단언으로 보임.
  - `tests/test_knowledge_lint.py::test_valid_bundled_cards_pass_lint` — 관리자 승인 자동 생성 카드
    (`auto-review-recyclerview.md`(커밋됨), `auto-review-hashcode.md`(신규))가 lint 필수 섹션
    (`대표 해결`, `흔한 오해`, `평가 키워드`)을 갖지 않아 실패. Spring 리인덱서 `renderCard` 가 만드는 섹션
    (`핵심 설명`, `사용 맥락`, `주의할 점`, `검색 키워드`)과 lint 스키마가 불일치. 별도 정리 필요.
- 관련: 답변 in-memory 캐시(`app/workflow/answer_cache.py`)는 fallback 답변은 캐시하지 않으므로
  이번 hashCode 케이스(이전엔 fallback만 존재)에는 영향 없음. 다만 generation 경로로 캐시된 개념을
  나중에 관리자가 수정하면, 동일 질문에 대해 재시작 전까지 stale 답변이 나올 여지는 남아 있음.
