# v2 Shadow miss reason overrode existing v1 fallback

- 발생 일시: 2026-06-13
- 영역: AI / RAG v2 Fast Path
- 심각도: medium

## 증상

v2 Fast Path 사유별 메시지를 사용자 fallback 답변에 직접 적용한 뒤 workflow regression 실패가 기존 6건에서 11건으로 증가했다. Git tag, idempotent, network 등 기존 주제별 fallback이 모두 `승인된 Fast Path 카드가 없습니다`로 대체됐다.

## 원인

Shadow Mode의 v2 판정은 기존 응답 흐름을 변경하면 안 되지만, `_fallback_for_state`가 v2 miss 사유를 v1 fallback보다 먼저 반환했다. immutable allowlist 5개와 관련 없는 질문도 v2 miss가 되므로 기존 v1 fallback 전체를 가로챘다.

관련 파일:
- `ai/app/workflow/nodes.py:513`
- `ai/app/workflow/v2_approved_fast_path.py:42`
- `ai/tests/test_workflow_runner.py:1007`

## 해결 방법

사유별 한국어 메시지는 `v2_fast_path.reason_message` 관측 metadata에만 기록하고, 사용자 fallback 답변은 기존 v1 로직을 그대로 사용하도록 복구했다.

## 재발 방지 / 메모

Shadow 기능의 metadata는 기존 route, answer, model, fallback을 변경하지 않아야 한다. 전체 workflow regression에서 기존 실패 기준보다 증가하지 않는지 반드시 확인한다.
