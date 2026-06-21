---
type: troubleshooting
category: general
status: active
updated: 2026-06-18
description: "RAG 기반 Priority v2 enrichment duplicated embedding tokens 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# Priority v2 enrichment duplicated embedding tokens

- 발생 일시: 2026-06-13
- 영역: AI / RAG cards v2
- 심각도: low

## 증상

`python ai/app/scripts/migrate_rag_cards.py --validate-only` 실행 시 `frontend-react-key`와 `spring-spring-question-59` 카드가 `embedding_text must be compact retrieval fields only` 오류로 실패했다.

## 원인

우선순위 카드 보강 과정에서 `embedding_text`를 구성할 때 term 또는 alias와 동일한 boost keyword를 다시 포함했다. Migration lint는 term, 상위 alias 3개, category, boost keywords를 `unique(...)`로 중복 제거한 정확한 문자열을 요구하므로 두 카드만 불일치했다.

관련 파일:
- `ai/app/knowledge/concepts_v2/frontend/frontend-react-key.json:17`
- `ai/app/knowledge/concepts_v2/spring/spring-spring-question-59.json:16`
- `ai/app/scripts/migrate_rag_cards.py:472`

## 해결 방법

두 카드의 `embedding_text`에서 이미 term 또는 alias로 포함된 중복 boost token만 제거했다. 카드 구조, payload, 승인 상태는 변경하지 않았다.

## 재발 방지 / 메모

카드 보강 스크립트가 embedding text를 작성할 때 migration의 `unique([term, aliases[:3], category, boost_keywords])` 규칙을 그대로 사용해야 한다. 보강 후에는 일반 knowledge lint뿐 아니라 migration `--validate-only`도 함께 실행한다.
