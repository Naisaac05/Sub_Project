---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI candidate capture write failure isolation 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI candidate capture write failure isolation

- 발생 일시: 2026-05-26
- 영역: ai
- 심각도: medium

## 증상

`candidate_save_node()`에서 auto-candidate JSONL 저장 중 예외가 발생하면 AI 답변 생성 자체가 실패할 수 있었다. Candidate capture는 학습 루프용 부가 기능인데, 저장소 장애가 learner answer path까지 막는 구조였다.

## 원인

`append_auto_candidate()` 호출을 감싸는 예외 격리가 없었다. 또한 candidate capture를 즉시 끌 수 있는 kill switch가 없어 queue contamination, backlog, 파일 쓰기 장애 상황에서 answer generation만 유지하는 운영 선택지가 부족했다.

## 해결 방법

- `ai/app/workflow/degraded.py:18`에 `AI_REVIEW_NO_CANDIDATE_CAPTURE` kill switch 판별을 추가했다.
- `ai/app/workflow/graph.py:117`에서 kill switch가 켜진 경우 candidate append를 건너뛰고 `candidate_capture_disabled` 플래그를 남긴다.
- `ai/app/workflow/graph.py:141`에서 `append_auto_candidate()` 예외를 격리하고 `candidate_capture_failed` 플래그를 남긴다.
- `ai/app/observability.py:35`에서 구조화 observability 이벤트에 `candidate_capture_disabled` / `candidate_capture_failed` 필드를 승격했다.
- `ai/tests/test_workflow_runner.py:272`와 `ai/tests/test_observability.py:58`에 회귀 테스트를 추가했다.

## 재발 방지 / 메모

Candidate capture는 core answer path 밖의 부가 write로 유지해야 한다. 향후 JSONL에서 durable queue/DB로 옮기더라도 write 실패는 answer 응답을 막지 않고 별도 metric/alert로만 드러나야 한다.
