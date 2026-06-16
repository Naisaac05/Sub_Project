# lightweight_only 강등 모드가 off-topic/unknown 차단에 가로채여 lightweight_only_miss 를 반환 못 함

- 발생 일시: 2026-06-16
- 영역: backend (Python AI 서비스 / ai-review 워크플로)
- 심각도: medium

## 증상

`ai/tests/test_workflow_degraded_modes.py` 의 두 테스트가 실패:

- `test_lightweight_only_miss_uses_template_without_cache_or_generator` (비스트리밍)
- `test_stream_lightweight_only_miss_streams_template_without_generator` (스트리밍)

`AI_REVIEW_LIGHTWEIGHT_ONLY=true` 강등 모드에서 의미 없는 질문(`xqzv plumbus frobnicate`)을 보내면
`route=lightweight_only_miss`, `fallback_used=True` 를 기대하지만, 실제로는
`route=off_topic_rejected`, `fallback_used=False` 가 반환됨.

pull 한 main / 순수 HEAD(미커밋 변경 0)에서도 동일하게 재현됨 → 환경·테스트 순서와 무관한 코드 회귀.

## 원인

PR #69(`a1159c3`, "v2_approved_fast_path 작업")가 free-question 흐름에 **off-topic/unknown intent 차단 분기**를 추가했는데, 이 분기가 **lightweight_only 강등 체크보다 먼저** 실행됨.

- 의미 없는 질문은 임베딩 분류기가 `intent="unknown"` (confidence 0.32)으로 분류함
- `ai/app/workflow/nodes.py:89` (와 streaming 측 `ai/app/workflow/runner.py`)의 `if intent in {"off_topic", "unknown"}:` 분기가 먼저 발동해 `route="off_topic_rejected"` (`fallback_used=False`)로 즉시 반환
- lightweight_only 체크(`ai/app/workflow/nodes.py:116`, `ai/app/workflow/runner.py:347`)는 그 **뒤**에 있어 도달조차 못 함

근본 비대칭: 다른 강등 모드(`template_fallback_only`, `cache_only`)는 `ai/app/workflow/degraded.py:26` `degraded_state_for` (워크플로 최상단, 분류 이전)에서 처리되는데, **lightweight_only 만** 분류·off-topic 이후 깊숙한 곳에서 처리됨. `degraded.py` 의 `degraded_state_for` 가 lightweight_only 모드에선 `None` 을 반환해 본 워크플로로 흘려보냈음.

같은 파일의 통과 테스트(`test_lightweight_only_returns_template_without_generator`)는 분류기를 `CONCEPT_DEFINITION` 으로 patch 해 off-topic 분기를 피했기 때문에 문제가 가려져 있었음.

## 해결 방법

lightweight_only 를 다른 강등 모드와 동일하게 `degraded_state_for` 에서 **조기 반환**하도록 이동.
`run_review_workflow`(`ai/app/workflow/runner.py:271`)와 `run_review_workflow_stream`(`ai/app/workflow/runner.py:293`)이 둘 다 최상단에서 `degraded_state_for` 를 호출하므로, **한 곳만 고쳐 양쪽 경로가 모두** 분류·off-topic 이전에 `lightweight_only_miss` 를 반환하게 됨.

- 수정: `ai/app/workflow/degraded.py:26` `degraded_state_for` 에 다음 분기 추가 (template_fallback_only 다음, cache_only 앞)
  ```python
  if lightweight_only_enabled():
      return lightweight_only_miss_state(mode, request)
  ```
- 테스트: `ai/tests/test_workflow_degraded_modes.py` 에 단위 테스트
  `test_degraded_state_for_handles_lightweight_only_as_early_template_miss` 추가 (RED → GREEN 확인)

검증: 실패했던 2건 + 신규 단위 1건 통과, 관련 스위트 90 passed / 0 failed.
부수효과로 강등 모드가 더 이상 2.5초짜리 임베딩 분류기를 거치지 않아 파일 실행이 9.8s → 0.5s 로 단축됨.

## 재발 방지 / 메모

- free-question 앞단에 새 라우팅 게이트(off-topic, intent 차단 등)를 추가할 때는, **강등 모드(`degraded_state_for`)가 그보다 먼저 처리되는지** 반드시 확인할 것. 강등 모드는 분류기에 의존하지 않는 kill-switch 여야 함.
- `ai/app/workflow/nodes.py:116` / `ai/app/workflow/runner.py:347` 의 잔존 lightweight_only 체크는 이제 이 모드에서 도달 불가한 데드 코드. 두 파일에 세션 무관 미커밋 WIP 수정이 걸쳐 있어 이번엔 건드리지 않음 → WIP 정리 후 제거 권장.
- 관련: [2026-06-15 AI v2-only 전환 후 테스트 계약 및 lightweight-only 우회 오류](2026-06-15-ai-v2-only-test-contract-and-lightweight-mode.md)
