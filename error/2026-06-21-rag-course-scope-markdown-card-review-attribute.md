# RAG course scope가 Markdown 카드의 review 속성에 접근함

- 발생 일시: 2026-06-21
- 영역: AI / RAG
- 심각도: medium

## 증상

v2 approved Fast Path의 워크플로·스트리밍 테스트 7개가 `AttributeError: 'ConceptCard' object has no attribute 'review'`로 실패했다.

## 원인

`ai/app/rag/documents.py:29`의 공용 로더는 JSON `RagCard`와 Markdown `ConceptCard`를 함께 반환한다. 문서 인덱스 생성으로 `concepts_v2/index.md`가 추가된 뒤, `ai/app/workflow/nodes.py:591`의 course scope 카드 필터가 타입 확인 없이 모든 항목의 `review`에 접근했다. 후보 승인 스크립트에는 `RagCard` 필터가 있었지만 course scope 경로에만 누락돼 있었다.

## 해결 방법

`ai/app/workflow/nodes.py:591`에서 `isinstance(card, RagCard)`를 먼저 확인하고 approved JSON 카드만 반환하도록 수정했다. `ai/tests/test_v2_approved_fast_path.py:302`에 Markdown·JSON 혼합 로더 회귀 테스트를 추가했다.

## 재발 방지 / 메모

`load_concept_cards()`는 의도적으로 합집합 타입을 반환하므로 `review`, `payloads`, `retrieval` 같은 v2 전용 필드에 접근하는 호출자는 반드시 `RagCard` 타입을 좁혀야 한다.
