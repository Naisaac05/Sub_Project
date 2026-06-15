# AI v2-only 전환 후 테스트 계약 및 lightweight-only 우회 오류

- 발생 일시: 2026-06-15
- 영역: AI workflow / test
- 심각도: medium

## 증상

AI 전체 테스트에서 v1 static/generated-card 경로, 로컬 judge 상시 실행, free-question grounding을 기대하는 실패가 다수 발생했다. 또한 `AI_REVIEW_LIGHTWEIGHT_ONLY=true`인 free-question이 템플릿으로 즉시 종료되지 않고 캐시 또는 Ollama 생성 경로로 진행했다.

## 원인

v1 삭제와 v2 approved fast path 전환 이후에도 일부 테스트가 제거된 v1 검색 계약을 유지하고 있었다. judge 관련 테스트도 로컬 기본값 OFF를 명시적으로 켜지 않아 실행 환경에 의존했다. 실제 런타임에서는 lightweight-only 분기가 `mode != "free-question"` 조건에 묶여 free-question을 차단하지 못했다.

## 해결 방법

- 모든 모드에서 lightweight-only가 캐시/Ollama 전에 종료되도록 분기를 수정했다 (`ai/app/workflow/nodes.py:95`, `ai/app/workflow/runner.py:326`).
- v1/static 및 검색 근거 없는 free-question grounding 테스트를 폐기 계약으로 명시하고 v2 fast-path 전용 테스트로 대체했다 (`ai/tests/test_workflow_runner.py:1810`, `ai/tests/test_generated_card_fast_path.py:1`).
- judge 동작을 검증하는 테스트만 환경 플래그를 명시적으로 켜도록 격리했다 (`ai/tests/test_adaptive_judge.py:17`, `ai/tests/test_prompt_versioning.py:14`).
- 누락된 승인 잠금 준비 API를 복구했다 (`ai/app/scripts/apply_and_lock_factchecked_next20.py:8`).

## 재발 방지 / 메모

free-question 검색 계약은 `v2 approved payload hit -> 응답`, `v2 miss -> Ollama`로 유지한다. v1/static 경로를 다시 기대하는 회귀 테스트를 추가하지 않는다. 현재 폐기 계약 테스트 20개는 skip으로 표시되어 있으며, 추후 파일에서 완전히 제거하거나 v2 시나리오로 재작성할 수 있다.
