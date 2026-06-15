# v2.1.4 선택적 payload null 점수 계산 오류

- 발생 일시: 2026-06-13
- 영역: ai
- 심각도: medium

## 증상
v2.1.4 후보 순위를 계산할 때 `EXAMPLE_REQUEST`가 null인 카드에서 `AttributeError: 'NoneType' object has no attribute 'get'`이 발생해 배치 실행 전 점검이 중단됐다.

## 원인
RagCard의 payload 항목은 선택적이므로 JSON 값이 null일 수 있지만, 후보 점수 계산기가 항상 객체라고 가정하고 연속해서 `get()`을 호출했다.

## 해결 방법
`ai/app/scripts/patch_payload_batch_v214.py:126`에서 선택적 payload를 빈 객체로 정규화한 뒤 값을 읽도록 수정했다. `ai/tests/test_patch_payload_batch_v214.py:56`에 null payload 회귀 테스트를 추가했다.

## 재발 방지 / 메모
후보 감사·점수 계산처럼 원시 JSON을 읽는 도구는 Pydantic 선택 필드가 null일 수 있음을 전제로 처리한다.
