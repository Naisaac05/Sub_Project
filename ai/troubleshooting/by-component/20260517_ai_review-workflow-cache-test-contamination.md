---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review workflow cache contaminated later tests 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review workflow cache contaminated later tests

- 발생 일시: 2026-05-17
- 영역: backend / AI
- 심각도: medium

## 증상

Phase 4.7c에서 반복 질문 속도를 높이기 위해 메모리 캐시를 추가한 뒤, `python -m unittest discover -s tests -v` 전체 실행에서 `test_free_question_rejects_stale_original_context_answer`가 실패했다. 단독 실행에서는 같은 테스트가 통과했다.

## 원인

새 캐시는 프로세스 전역 `OrderedDict`로 유지된다. 전체 unittest 실행에서는 이전 workflow 테스트가 저장한 답변이 이후 테스트에도 남아, stale-answer 검증 경로가 테스트 순서에 영향을 받을 수 있었다. 관련 구현은 `ai/app/workflow/answer_cache.py:8`, 캐시 조회/저장은 `ai/app/workflow/answer_cache.py:24`, `ai/app/workflow/answer_cache.py:32`에 있다.

## 해결 방법

테스트 격리를 위해 캐시 초기화 함수를 추가하고 workflow 테스트의 `setUp()`에서 매 테스트마다 캐시를 비우도록 했다.

- `ai/app/workflow/answer_cache.py:41`에 `clear_answer_cache()` 추가
- `ai/tests/test_workflow_runner.py:4`에서 초기화 함수 import
- `ai/tests/test_workflow_runner.py:9`에서 테스트별 캐시 초기화

## 재발 방지 / 메모

서비스 런타임 캐시는 유지하되, 테스트에서는 전역 상태를 반드시 초기화해야 한다. 이후 TTL, 사용자별 캐시, Redis 등으로 확장할 때도 테스트 시작 전 clear hook이나 fixture를 먼저 마련해야 한다.
