# v2 코스 문항 보강 compact 불일치와 mojibake 감사 오탐

- 발생 일시: 2026-06-13
- 영역: ai / RAG
- 심각도: medium

## 증상

questions 기반 draft 보강 후 migration validate-only에서 algorithm 카드 4개의 `embedding_text` compact 규칙 불일치가 발생했다.
이후 정상 한국어 질문의 물음표까지 mojibake로 판정해 품질 감사에서 138개 카드가 보류되었다.

## 원인

보강기가 150자 제한을 맞추기 위해 `embedding_text` 값만 잘라 aliases와 boost_keywords 조합이 달라졌다.
또한 감사기의 mojibake marker에 일반 물음표 `?`가 포함되어 정상 질문 문장을 손상 데이터로 오인했다.

## 해결 방법

- aliases와 boost_keywords를 함께 축약한 뒤 동일 필드 조합으로 embedding_text를 생성하도록 수정했다: `ai/app/scripts/enrich_mapped_draft_cards.py`
- 일반 물음표는 허용하고 연속 `??`와 실제 mojibake marker만 검출하도록 수정했다: `ai/scripts/audit_rag_cards_v2.py`
- 정상 한국어 물음표 회귀 테스트를 추가했다: `ai/tests/test_audit_rag_cards_v2.py`

## 재발 방지·메모

- retrieval compact 검증은 생성된 embedding_text뿐 아니라 원본 aliases와 boost_keywords 조합도 함께 비교해야 한다.
- mojibake 검출 규칙에는 정상 문장부호를 단독 marker로 넣지 않는다.
- 자동 감사 통과는 검색 관련성과 교육적 정확성을 보장하지 않으므로 Shadow 평가를 별도로 유지한다.
