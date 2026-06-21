---
type: troubleshooting
category: general
status: active
updated: 2026-06-18
description: "RAG 기반 v2 partial approval failed draft-only lint policy 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# v2 partial approval failed draft-only lint policy

- 발생 일시: 2026-06-13
- 영역: AI / RAG card validation
- 심각도: high

## 증상

지정된 v2 카드 5개와 payload 15개를 approved로 변경한 뒤 `migrate_rag_cards.py --validate-only`가 승인 카드마다 `card_status must be draft`, `payload_status must be draft` 오류를 반환했다.

## 원인

`ai/app/scripts/migrate_rag_cards.py`의 v2 lint는 생성 단계의 draft-only 정책만 표현하고 있어, 명시적으로 허용된 부분 승인 상태를 유효한 상태로 검증할 수 없었다.

## 해결 방법

- `ai/app/scripts/migrate_rag_cards.py`의 lint가 `draft`와 `approved` 카드 상태를 허용하도록 확장했다.
- 카드가 draft이면 세 generated payload도 모두 draft, 카드가 approved이면 세 generated payload도 모두 approved여야 하도록 일관성 검증을 추가했다.
- `ai/tests/test_migrate_rag_cards_v2.py`에 일관된 approved 상태 통과 및 혼합 상태 실패 회귀 테스트를 추가했다.

## 재발 방지 / 메모

생성 단계 lint와 승인 단계 lint가 같은 함수를 사용할 때는 상태 전이를 무조건 거부하지 말고, 허용된 상태와 payload 일관성을 검증해야 한다. Lazy payload 승인 여부는 별도 정책으로 계속 제한한다.
