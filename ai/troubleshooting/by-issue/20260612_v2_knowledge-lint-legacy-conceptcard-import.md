---
type: troubleshooting
category: general
status: active
updated: 2026-06-18
description: "v2 knowledge-card lint could not run because legacy ConceptCard was removed 발..."

---

# v2 knowledge-card lint could not run because legacy ConceptCard was removed

- 발생 일시: 2026-06-12
- 영역: ai / knowledge lint
- 심각도: medium

## 증상

v2 draft 카드 생성 후 `python scripts/lint_knowledge_cards.py`를 실행하면 시작 단계에서 `ImportError: cannot import name 'ConceptCard'`가 발생한다.

## 원인

`ai/scripts/lint_knowledge_cards.py:9`는 기존 Markdown 카드용 `ConceptCard`를 import하지만, 현재 `ai/app/rag/documents.py`는 JSON `RagCard` 로더만 제공한다. lint 실행기와 현재 카드 스키마가 서로 다른 세대에 남아 있다.

## 해결 방법

이번 카드 생성 단계에서는 운영 lint 코드를 변경하지 않고, `ai/app/scripts/migrate_rag_cards.py:361`의 v2 전용 lint와 `--validate-only`를 사용해 생성된 129개 카드의 JSON, schema, draft 상태, payload 상태, card ID 중복, retrieval 필드를 검증했다.

## 재발 방지 / 메모

근본 원인은 남아 있다. 후속 작업에서 `lint_knowledge_cards.py`를 `RagCard`와 명시적 root 인자를 지원하도록 재설계해야 한다. 그 전까지 v2 검증에는 migration의 v2 전용 validator를 사용한다.

