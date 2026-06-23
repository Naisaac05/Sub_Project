# Grounded 안전 응답이 LangGraph 후속 노드에서 덮어써짐

## 증상

근거 부족 또는 품질 실패 시 `grounded_fallback_safe_response`를 만들었지만 실제 workflow 결과는 기존 `fallback_template` 문구와 route로 바뀌었다.

## 원인

`generate_answer` 이후의 `validate_answer`, `confidence_gate`, `fallback_answer`가 새 안전 route를 일반 생성 답변처럼 다시 평가했다. 안전 문구가 질문 주제를 직접 반복하지 않아 `missing_topic`으로 판정되고 기존 fallback이 덮어썼다.

## 해결 방법

`grounded_fallback_safe_response`를 이미 검증된 fail-closed 종단 상태로 취급해 validation 결과와 route를 보존했다. 그래프 수준 회귀 테스트와 실제 Ollama workflow 평가로 확인했다.

- `ai/app/workflow/nodes.py:430`
- `ai/app/workflow/nodes.py:510`
- `ai/tests/test_v2_approved_fast_path.py:460`
- `ai/reports/grounded_fallback_live_2026-06-22.json:1`

## 재발 방지·메모

새로운 종단 route를 추가할 때는 노드 단위 테스트뿐 아니라 전체 StateGraph를 통과한 최종 response의 answer와 route를 함께 검증한다.
