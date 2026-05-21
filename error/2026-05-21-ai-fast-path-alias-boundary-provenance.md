# AI fast path alias boundary and generated-card provenance

- 발생 일자: 2026-05-21
- 영역: ai
- 심각도: medium

## 증상

`What is forest?` 같은 unrelated English question이 `REST` static fast path로 처리될 수 있었다. `forest` 안에 `rest`가 포함되어 있기 때문이다. 또한 retrieved context에서 generated concept card fast path를 선택하면 응답 metadata의 `retrieved_concept_ids`가 비어 provenance가 사라졌다.

## 원인

Static answer alias matching이 질문과 topic을 whitespace-stripped normalized string으로 만든 뒤 `normalized_alias in haystack` substring matching을 사용했다: `ai/app/workflow/lightweight_answers.py:283`. 이 방식은 짧은 Latin alias인 `rest`, `api`, `json` 등을 unrelated word 내부에서도 match시킨다.

Generated-card fast path는 retrieved context의 concept id를 이용해 card answer를 선택한 뒤에도 lightweight fast path 공통 처리에서 `state.contexts = []`를 실행했다: `ai/app/workflow/nodes.py:49`, `ai/app/workflow/nodes.py:67`. 그래서 `run_review_workflow`가 응답을 만들 때 retrieved provenance를 수집할 context가 남아 있지 않았다.

## 해결 방법

Latin/alphanumeric static aliases는 original text 기준 regex boundary matching으로 바꾸고, non-Latin aliases는 기존 normalized matching을 유지했다: `ai/app/workflow/lightweight_answers.py:283`, `ai/app/workflow/lightweight_answers.py:303`.

`generate_answer_node`는 `static_fast_path`일 때만 contexts를 비우고, `generated_card_fast_path`에서는 retrieved context를 보존하도록 변경했다: `ai/app/workflow/nodes.py:49`, `ai/app/workflow/nodes.py:67`.

Regression tests를 추가했다: `ai/tests/test_workflow_runner.py:313`, `ai/tests/test_generated_card_fast_path.py:131`.

## 재발 방지·메모

Short Latin aliases는 normalized substring matching을 사용하지 않는다. 새 fast path가 metadata를 줄이거나 지울 때는 static answer와 retrieved/generated-card answer를 구분하고, response metadata의 `retrieved_concept_ids` 또는 `matched_concept_id`가 남는지 함께 확인한다.
