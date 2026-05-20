# AI review fallback confidence stayed high

- 발생 일시: 2026-05-16
- 영역: ai
- 심각도: low

## 증상

Lightweight workflow 테스트에서 영어 답변이 template fallback으로 교체됐는데도 `confidence_score`가 `0.8`로 남아 있었다. fallback 응답은 검토 후보로 남겨야 하는데 high confidence처럼 보일 수 있었다.

## 원인

workflow가 `validate_answer -> confidence_gate -> fallback_answer` 순서로 실행되면서, confidence가 원래 모델 답변 기준으로 계산된 뒤 fallback 응답으로 교체됐다. fallback 적용 후 confidence를 다시 낮추거나 후보 저장 대상으로 표시하는 보정 단계가 없었다.

## 해결 방법

`fallback_answer_node`에서 fallback이 적용되고 기존 confidence가 high band이면 `0.79` medium band로 낮추고 `should_save_candidate=True`로 보정했다.

- `ai/app/workflow/nodes.py:89`
- `ai/tests/test_workflow_runner.py:22`

## 재발 방지 / 메모

추후 실제 LangGraph로 옮길 때도 confidence 계산 이후 응답이 바뀌는 노드가 있으면 confidence를 재계산하거나 명시적으로 보정해야 한다. fallback 응답은 정상 답변이 아니라 안전 응답이므로 high confidence로 반환하지 않는다.

