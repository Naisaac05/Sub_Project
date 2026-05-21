# AI workflow tests polluted auto-candidate queue and saved discourse marker as term

- 발생 일시: 2026-05-21
- 영역: ai / workflow / test
- 심각도: medium
- 영향 범위: 모든 free_question workflow + evaluate_lightweight_rag.py 실행 시 candidate 생성 로직

## 증상

`ai.tests.test_workflow_runner`를 실행한 뒤 `ai/app/knowledge/candidates/auto_candidates.jsonl`에 테스트 실행 산출물이 추가되거나 기존 후보 row의 `definition_draft`가 갱신됐다. 또한 `"혹시 AI 프롬프트가 뭐야?"` 같은 질문에서 후보 term이 `"AI 프롬프트"`가 아니라 담화 표지인 `"혹시"` 또는 너무 짧은 `"AI"`로 저장될 수 있었다.

## 원인

workflow 테스트의 공통 `setUp()`이 `AI_REVIEW_AUTO_CANDIDATES_PATH`를 격리하지 않아 `candidate_save_node()`가 기본 후보 파일로 fallback했다. 별도 env를 설정하지 않은 테스트가 `run_review_workflow()`를 호출하면 실제 repository data file이 변경됐다. 추가로 `ai/scripts/evaluate_lightweight_rag.py`의 deterministic evaluator 경로도 내부에서 `run_review_workflow()`를 호출하면서 후보 queue를 격리하지 않아, 전체 `unittest discover` 실행 시 golden dataset 평가가 실제 후보 파일에 row를 추가했다.

후보 저장 경로에서는 `free_question_intent.topic`을 사용하지 않고 원문 `state.request.user_answer` 또는 `resolved_query`를 그대로 `build_auto_candidate()`에 넘겼다. 그 결과 canonicalizer가 질문 요지를 정리해도 후보 추출기는 원문 첫 technical token을 term으로 삼았다. 혼합 토픽 `"AI 프롬프트"`도 `_extract_term()`이 첫 token `"AI"`만 반환했다.

## 해결 방법

`ai/tests/test_workflow_runner.py:21`에서 각 테스트마다 임시 `AI_REVIEW_AUTO_CANDIDATES_PATH`를 설정하고 `tearDown()`에서 원래 env와 answer cache를 복구하도록 했다. 기본 queue fallback 동작을 검증하는 테스트는 `append_auto_candidate`를 patch한 상태에서 env만 일시 제거하므로 실제 파일을 쓰지 않는다.

`ai/app/workflow/graph.py:131`에서 후보 저장용 query를 `_candidate_resolved_query()`로 분리하고, `free_question_intent.topic`이 있으면 이를 우선 사용하도록 했다. `ai/app/knowledge/auto_candidates.py:160`에서는 짧은 혼합 토픽 구문을 term으로 보존해 `"AI 프롬프트"`가 `"AI"`로 잘리지 않게 했다.

`ai/scripts/evaluate_lightweight_rag.py:227`의 deterministic workflow 실행은 임시 `AI_REVIEW_AUTO_CANDIDATES_PATH`를 설정한 상태로 감싸, evaluator가 repository 후보 파일을 변경하지 않게 했다. 실제 Ollama 평가(`--real`)는 운영성 평가 성격이므로 기존 runner 주입 경로를 유지한다.

## 재발 방지 / 메모

후보 저장처럼 repository data file에 append/update하는 workflow 테스트는 기본적으로 temp path를 사용해야 한다. 새 workflow 테스트를 추가할 때 `run_review_workflow()`가 candidate save까지 흐르는지 확인하고, 실제 후보 파일이 dirty 상태가 아닌지 `git status --short -- ai/app/knowledge/candidates/auto_candidates.jsonl`로 함께 검증한다.
