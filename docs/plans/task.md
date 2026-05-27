# Task Tracker - AI Review Adaptive Judge Optimization

| Task | Status | Description |
| --- | --- | --- |
| Task 1: Schema Extension (`state.py`) | completed | `ReviewWorkflowState`에 최적화 메트릭 필드 (`judge_tier`, `semantic_judge_skipped`, `grounding_judge_skipped`, `grounding_async_executed`, `estimated_latency_saved_ms`) 추가 |
| Task 2: Tiered Judge & Async Grounding (`nodes.py`) | completed | `generate_answer_node` 하단에서 질문 난이도를 티어별(Tier 0/1/2) 분류하고, Grounding Judge의 백그라운드 스레드 비동기 실행 및 스킵 기동 구현 |
| Task 3: Metrics & Event Emission (`runner.py`) | completed | `_build_response_from_state` 및 observability events 발행 시 대시보드 메트릭 연동 |
| Task 4: Tests & Verification (`test_adaptive_judge.py`) | completed | `test_adaptive_judge.py` 신설하여 4대 조건 검증 및 전체 229개 테스트 100% 성공 확인 |
