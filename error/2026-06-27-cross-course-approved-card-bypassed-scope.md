# 타 코스 approved 카드가 코스 범위 제한을 우회함

- 발생 일시: 2026-06-27
- 영역: AI RAG / course scope / streaming
- 심각도: high

## 증상

Frontend 코스 복습 중 `Spring IoC란 무엇인가요?`처럼 다른 코스의 approved 카드를 정확히 질문하면 `out_of_course_redirect`가 아니라 `v2_approved_fast_path`로 답변했다. 같은 현상이 frontend, spring, java, python, algorithm 코스 조합에서 모두 재현됐으며 스트리밍 경로도 동일했다.

## 원인

코스 범위 판정은 타 코스 카드를 `out_of_course_tech`로 정확히 식별했지만, 그 직후 approved 카드가 정확히 검색되면 예외적으로 답변을 허용하는 `_resolve_unscoped_v2_fast_path_for_exact_hit`를 호출했다. 따라서 course scope 판정이 Fast Path 예외에 의해 덮어써졌다.

동기 경로와 스트리밍 경로에 동일한 예외가 각각 존재했다: `ai/app/workflow/nodes.py:98`, `ai/app/workflow/runner.py:334`.

## 해결 방법

`out_of_course_tech` 판정 후 approved Fast Path를 다시 시도하는 예외를 제거했다. 이제 카드 승인 여부나 정확 일치 여부와 관계없이 즉시 `out_of_course_redirect`를 반환하며 Ollama도 호출하지 않는다: `ai/app/workflow/nodes.py:98`, `ai/app/workflow/runner.py:334`.

5개 코스의 교차 질문을 순환 검증하는 동기 테스트와 실제 화면 경로에 해당하는 스트리밍 테스트를 추가했다: `ai/tests/test_workflow_runner.py:208`, `ai/tests/test_v2_approved_fast_path.py:577`.

## 재발 방지 / 메모

- Course scope는 approved Fast Path보다 우선한다.
- `out_of_course_tech` 판정 이후에는 검색이나 생성 모델을 다시 호출하지 않는다.
- 코스별 단일 테스트가 아니라 frontend, spring, java, python, algorithm 전체 조합을 유지한다.
- OFF-TOPIC과 타 코스 기술 질문은 서로 다른 route를 사용하지만 둘 다 모델 생성 없이 template redirect로 종료한다.
