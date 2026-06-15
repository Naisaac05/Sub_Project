# Draft retrieval enrichment caused Shadow ranking regression

- 발생 일시: 2026-06-13
- 영역: AI / RAG cards v2
- 심각도: medium

## 증상

draft 카드 payload 한국어 보강 후 Shadow Fast Path 성공률이 60%에서 40%로 하락했다. `Spring cache` 관련 3개 샘플의 Top1이 승인 카드 `spring-spring-question-59`에서 draft 카드 `spring-spring-bean-scope`로 바뀌었다.

## 원인

모든 draft 카드의 aliases와 retrieval boost keywords를 동시에 재작성하면서 `spring-spring-bean-scope`에 범용 Spring 키워드가 강화되었다. 그 결과 승인된 cache 카드보다 높은 lexical score를 얻어 응답에는 사용할 수 없는 draft 카드가 Top1을 차지했다.

관련 파일:
- `ai/app/scripts/enrich_draft_cards_korean.py:171`
- `ai/app/knowledge/concepts_v2/spring/spring-spring-bean-scope.json`
- `ai/scripts/shadow_rag_cards_v2.py:121`

## 해결 방법

개념별 전문 프로필이 있는 우선 카드 3개만 retrieval 필드를 개선하고, 나머지 draft 카드는 기존 retrieval 필드를 유지하도록 제한했다. 이미 변경된 범용 draft retrieval과 aliases는 작업 전 백업에서 복원하고 payload 개선은 유지했다.

## 재발 방지 / 메모

draft payload 품질 개선과 retrieval ranking 변경은 별도 단계로 수행한다. retrieval 필드를 변경한 경우 승인 카드 중심 Shadow 샘플의 Top1과 Fast Path 성공률이 기존보다 낮아지지 않는지 반드시 확인한다.
