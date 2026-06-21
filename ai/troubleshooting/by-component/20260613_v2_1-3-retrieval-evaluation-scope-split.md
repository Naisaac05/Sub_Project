---
type: troubleshooting
category: evaluation
status: active
updated: 2026-06-18
description: "v2.1.3 검색 평가 범위 분리 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# v2.1.3 검색 평가 범위 분리

- 발생 일시: 2026-06-13
- 영역: ai
- 심각도: medium

## 증상
payload만 수정한 카드 배치에서도 LOO 지표가 변해, 실제 운영 검색 필드가 그대로인데 전체 배치가 회귀로 판단되어 롤백될 수 있었다.

## 원인
운영 Chroma 검색은 `retrieval.embedding_text`를 임베딩하지만, 기존 평가용 lexical 검색은 승인된 `CONCEPT_DEFINITION.content`를 `card.searchable_text`에 포함했다. 이 때문에 payload 품질 변경이 운영 검색 회귀처럼 측정되었다.

## 해결 방법
`ai/app/scripts/migrate_rag_cards.py:522`에 잠금 검색 필드만 사용하는 production 평가 텍스트를 추가하고, `ai/app/scripts/migrate_rag_cards.py:532`와 `ai/app/scripts/migrate_rag_cards.py:553`에서 production/content 평가 모드를 분리했다. `ai/app/scripts/patch_payload_batch_v213.py:167`은 카드별 평가와 롤백, 최종 production 불변 검사를 수행한다.

## 재발 방지 / 메모
payload-only 배치는 production_mode의 Exact, Hit@1, LOO 불변을 승인 기준으로 사용한다. content_mode LOO 변화만으로 전체 배치를 롤백하지 않으며, content Hit@1이 2%보다 크게 하락한 카드만 개별 롤백한다.
