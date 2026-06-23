# Grounded generation 응답이 fallback 노드에서 템플릿으로 덮어써짐

- 발생 일시: 2026-06-23
- 영역: ai
- 심각도: medium

## 증상

운영 Shadow 확장 live 평가에서 `python-asyncio-usage` 케이스가 승인 근거를 찾고 모델도 호출했지만 최종 route가 `grounded_fallback_generation`이 아니라 `fallback_template`으로 떨어졌다. 리포트에는 `missing_topic` 품질 플래그가 남았고, 최종 답변은 일반 템플릿 fallback 문구가 됐다.

## 원인

`ai/app/workflow/nodes.py:244`에서 grounded quality gate를 통과해 `grounded_fallback_generation` route로 설정됐지만, 후속 `fallback_answer_node`가 이 route를 보존하지 않았다. `ai/app/workflow/nodes.py:549`의 보존 가드는 `grounded_fallback_safe_response`만 대상으로 했기 때문에, 일반 topic 검사에서 `missing_topic`이 남은 grounded generation 답변이 템플릿 fallback으로 덮였다.

## 해결 방법

`ai/app/workflow/nodes.py:549`의 보존 가드에 `grounded_fallback_generation`을 추가했다. 승인 근거 기반 자체 품질 게이트를 통과한 generation 답변은 일반 fallback 노드에서 다시 덮지 않는다. 회귀 테스트는 `ai/tests/test_v2_approved_fast_path.py`의 `test_fallback_node_preserves_grounded_generation_route`로 추가했다.

## 재발 방지 / 메모

grounded fallback route는 일반 생성 route가 아니라 자체 근거 선택, 품질 게이트, 안전 응답 정책을 이미 통과한 route다. 후속 노드가 route별 소유권을 구분하지 않으면 검증된 답변도 일반 fallback 정책에 의해 손실될 수 있다.
