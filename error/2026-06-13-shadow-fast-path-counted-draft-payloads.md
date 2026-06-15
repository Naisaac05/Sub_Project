# shadow Fast Path metric counted draft payloads

- 발생 일시: 2026-06-13
- 영역: AI / RAG shadow evaluation
- 심각도: high

## 증상
Shadow baseline이 BFS와 JSX draft 카드까지 Fast Path hit로 계산해 15/15, 100%를 보고했다.

## 원인
`payload_available`가 payload 내용 존재 여부만 확인하고 `card_status=approved` 및 intent별 `payload_status=approved`를 확인하지 않았다.

## 해결 방법
Fast Path 가능 여부 계산 전에 카드 승인 상태와 해당 intent payload 승인 상태를 모두 검사하도록 수정했다.

관련 파일:
- `ai/scripts/shadow_rag_cards_v2.py:61`
- `ai/tests/test_shadow_rag_cards_v2.py:86`

## 재발 방지 / 메모
draft 카드에 내용이 있어도 Fast Path hit가 0으로 계산되는 회귀 테스트를 추가했다.
