---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review graph route overwrite 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review graph route overwrite

- 발생 날짜: 2026-05-19
- 영역: ai / workflow
- 심각도: medium

## 증상

Phase 18 LangGraph 전환 중 `dead_end_state_node()`가 `graph_status="dead_end"`를 설정해도 최종 `route`가 `fallback_template`으로 바뀌었다. 이 상태로 두면 dead-end/error graph state를 운영 지표에서 fallback과 구분하기 어렵다.

## 원인

`dead_end_state_node()`가 `state.route = "dead_end_state"`를 설정한 뒤 `fallback_answer_node()`를 호출했는데, `fallback_answer_node()`가 모든 fallback 상황에서 `state.route = "fallback_template"`으로 덮어썼다.

## 해결 방법

`fallback_answer_node()`가 `dead_end_state`와 `error_state` route를 받은 경우에는 해당 graph-level route를 보존하도록 수정했다.

- `ai/app/workflow/nodes.py:156`
- `ai/app/workflow/nodes.py:172`
- `ai/tests/test_workflow_runner.py:199`

## 재발 방지·메모

Graph-level terminal state는 답변 템플릿 fallback 여부와 별도의 운영 메타데이터다. 앞으로 fallback 로직을 수정할 때는 `test_dead_end_and_error_state_nodes_mark_graph_status`처럼 route와 `graph_status`를 함께 검증해야 한다.
