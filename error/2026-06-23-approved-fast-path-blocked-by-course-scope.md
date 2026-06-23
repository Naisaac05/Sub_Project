# 승인 Fast Path가 코스 범위 제한에 먼저 차단됨

- 발생 일시: 2026-06-23
- 영역: ai
- 심각도: medium

## 증상

승인된 RAG 카드와 같은 질문을 해도 현재 화면이나 문항의 `course_id`가 카드의 `category`와 다르면 승인 payload가 바로 반환되지 않았다. 예를 들어 `course_id=frontend` 상태에서 `Java equals`처럼 승인된 Java 카드를 정확히 묻는 경우, `v2_approved_fast_path` 대신 `out_of_course_redirect` 또는 LLM 생성 경로로 흐를 수 있었다.

## 원인

free-question 처리에서 course scope 판정이 fast-path보다 먼저 실행됐다. `resolve_course_scope()`가 전체 approved 카드에서 Java 카드를 top hit로 찾더라도, 현재 course의 허용 카드 묶음에 없으면 `out_of_course_tech`를 반환했고, `generate_answer_node()`와 streaming runner가 그 즉시 redirect를 반환했다. 이 때문에 exact approved card hit가 있어도 `resolve_v2_approved_fast_path()`까지 도달하지 못했다.

## 해결 방법

`out_of_course_tech` 판정 직후에 한 번만 unscoped approved fast-path를 시도하도록 바꿨다. serve 모드에서 exact approved hit와 approved payload가 확인되면 `v2_approved_fast_path`를 우선 반환하고, 그렇지 않으면 기존 out-of-course redirect를 유지한다.

관련 파일:

- `ai/app/workflow/nodes.py:98`
- `ai/app/workflow/nodes.py:679`
- `ai/app/workflow/runner.py:334`
- `ai/tests/test_v2_approved_fast_path.py:385`

## 재발 방지 / 메모

course scope는 애매한 질문을 보호하는 보조 제한으로 유지하되, 승인 payload가 있는 exact card hit는 더 강한 신뢰 신호로 취급한다. 회귀 테스트 `test_exact_approved_hit_bypasses_course_scope_limit_in_serve_mode`가 `frontend` course에서 `java-equals` approved payload가 바로 반환되는지 검증한다.
