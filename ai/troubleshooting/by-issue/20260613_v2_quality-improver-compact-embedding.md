---
type: troubleshooting
category: general
status: active
updated: 2026-06-18
description: "RAG 기반 v2 quality improver did not match strict compact embedding rule 발생 원인 ..."

---

# v2 quality improver did not match strict compact embedding rule

- 발생 일시: 2026-06-13
- 영역: AI / RAG cards
- 심각도: medium

## 증상
일반 knowledge lint는 통과했지만 `migrate_rag_cards.py --validate-only`가 draft 카드 7개의 `embedding_text must be compact retrieval fields only` 오류를 보고했다.

## 원인
개선기는 embedding 문자열을 150자에서 단순 절단했다. 기존 strict lint는 `term + aliases 상위 3개 + category + boost_keywords`의 중복 제거 결과와 embedding 문자열이 정확히 일치해야 한다.

## 해결 방법
aliases와 boost keywords를 먼저 짧고 구체적인 값으로 압축한 뒤 strict lint와 동일한 필드 조합으로 embedding 문자열을 생성하도록 변경했다.

관련 파일:
- `ai/app/scripts/improve_rag_cards_v2.py:62`
- `ai/tests/test_improve_rag_cards_v2.py:69`

## 재발 방지 / 메모
테스트에서 embedding 문자열이 strict compact 조합과 정확히 같은지와 150자 이하인지 함께 검증한다.
