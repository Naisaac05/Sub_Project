# Grounded retry 성공 답변이 템플릿 fallback으로 덮어써짐

- 발생 일시: 2026-06-23
- 영역: ai
- 심각도: medium

## 증상

승인된 근거가 있는 `grounded_fallback` 경로에서 1차 모델 호출이 실패하고 fallback 모델 호출이 성공해도 최종 응답이 `grounded_fallback_safe_response`로 떨어졌다. 테스트 `tests.test_v2_approved_fast_path.V2ApprovedFastPathWorkflowTest.test_grounded_retry_also_passes_quality_gate`에서 `attempts == 2`는 맞았지만, retry 결과가 근거 답변으로 복구되지 않고 안전 응답으로 덮였다.

## 원인

`ai/app/workflow/nodes.py:198`의 예외 처리 블록에서 fallback 모델 retry가 성공해도, `grounded_evidence is not None`인 경우 바로 반환하지 않고 아래 템플릿 fallback 대입부까지 계속 진행했다. 그 결과 `ai/app/workflow/nodes.py:226` 이후 코드가 성공한 retry 답변을 `_fallback_message(...)`로 덮어썼고, 이후 grounded quality gate는 `fallback_template` route를 보고 generation error로 처리했다.

## 해결 방법

`ai/app/workflow/nodes.py:198`에 `retry_succeeded` 플래그를 추가하고, fallback 모델 retry가 성공한 지점인 `ai/app/workflow/nodes.py:216`에서 `True`로 설정했다. 템플릿 fallback 대입은 `retry_succeeded`가 `False`인 경우에만 실행되도록 `ai/app/workflow/nodes.py:226`에 가드했다. 이후 저품질 retry 답변은 승인 근거 기반 초안 생성 로직으로 복구되어 `grounded_fallback_generation`으로 통과한다.

## 재발 방지 / 메모

grounded fallback 경로의 retry 성공, 저품질 생성 복구, stream 복구를 각각 테스트로 고정했다. 예외 처리 블록에서 성공 상태를 만든 뒤 같은 블록 끝에서 공통 fallback 상태를 대입하는 패턴은 route overwrite를 만들기 쉬우므로, 성공 플래그나 조기 반환을 명시해야 한다.
