# Markdown and JSON card loader compatibility regression

- 발생 일시: 2026-06-12
- 영역: AI / RAG card loader
- 심각도: high

## 증상

Markdown generated-card fast path 테스트 2건이 `load_concept_cards()`에서 빈 목록을 받아 `IndexError`로 실패했다. Markdown knowledge lint도 JSON 카드가 섞이면 `sections` 속성이 없어 실패했다.

## 원인

v2 JSON 카드 지원 과정에서 `ai/app/rag/documents.py`의 기존 Markdown `ConceptCard` 모델과 파서가 제거되고 JSON 파일만 순회하도록 변경되었다. 그 결과 기존 v1 Markdown generated-card 동작과 JSON v2 로딩이 상호 배타적으로 동작했다.

## 해결 방법

- `ai/app/rag/documents.py:13`에 기존 `ConceptCard` 호환 모델을 복원했다.
- `ai/app/rag/documents.py:29`에서 Markdown과 JSON을 함께 로드하도록 변경했다.
- `ai/app/rag/documents.py:53`에 Markdown front matter 및 section 파서를 복원했다.
- `ai/scripts/lint_knowledge_cards.py:21`에서 legacy lint는 Markdown `ConceptCard`만 검증하도록 범위를 명확히 했다.
- `ai/tests/test_rag_documents.py`에 Markdown과 JSON 동시 로딩 회귀 테스트를 추가했다.

## 재발 방지 / 메모

v1 Markdown 카드와 v2 JSON 카드는 활성화 여부와 무관하게 같은 로더 API를 사용할 수 있으므로, 포맷 지원을 추가할 때 기존 포맷 파서를 제거하지 않는다. generated-card fast path 2건과 legacy lint를 카드 로더 변경의 필수 회귀 테스트로 유지한다.
