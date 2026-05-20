# AI review fast path RAG context leak

- 발생 날짜: 2026-05-18
- 범위: ai
- 심각도: medium

## 증상

승인된 `@ControllerAdvice` 후보를 concept card로 승격한 뒤 Python AI 전체 테스트에서 `test_common_programming_concepts_skip_generator`가 실패했다. `REST API가 뭐야?`처럼 fast path로 바로 답해야 하는 질문의 응답 메타데이터에 `java-backend-controlleradvice`가 `retrieved_concept_ids`로 포함됐다.

실제 사용자 응답 본문은 빠른 답변으로 생성되지만, 메타데이터만 이전 RAG 검색 결과를 들고 있어 “승인된 지식이 엉뚱하게 쓰인 것처럼 보이는” 문제가 생길 수 있었다.

## 원인

workflow는 `retrieve_context_node`에서 먼저 RAG 검색을 수행한 뒤 `generate_answer_node`에서 lightweight fast path 답변 여부를 판단한다. 새로 승격된 `@ControllerAdvice` 카드 설명에는 `REST API` 문구가 포함되어 있었고, 이 때문에 `REST API가 뭐야?` 질문에서 해당 카드가 검색됐다.

그 뒤 fast path 답변이 선택되었지만 `state.contexts`를 비우지 않아, 실제 답변은 RAG를 사용하지 않았는데도 검색된 concept id가 응답 메타데이터로 누수됐다.

관련 파일:

- `ai/app/workflow/nodes.py:47`
- `ai/app/workflow/nodes.py:52`
- `ai/app/knowledge/review.py:13`

## 해결 방법

`generate_answer_node`에서 `free-question` fast path 답변을 사용할 때 `state.contexts = []`를 설정해 사전 검색된 context가 응답 메타데이터로 흘러가지 않도록 했다.

함께 확인한 내용:

- `aria-label`과 `@ControllerAdvice`는 curated draft/critic 정의를 사용하도록 보강했다.
- 기존 승인 후보의 draft/critic 메타데이터를 갱신하고 `promote_concept_candidates.py`를 다시 실행해 generated concept card를 재생성했다.
- RAG retriever 자체는 `aria-label`, `@ControllerAdvice` 질문에서 각각 `frontend-aria-label`, `java-backend-controlleradvice`를 찾는 것을 확인했다.

검증 명령:

- `C:\Users\User\anaconda3\envs\devmatch-ai\python.exe -m unittest tests.test_workflow_runner.WorkflowRunnerTest.test_common_programming_concepts_skip_generator -v`
- `C:\Users\User\anaconda3\envs\devmatch-ai\python.exe -m unittest discover -s tests -v`
- `C:\Users\User\anaconda3\envs\devmatch-ai\python.exe scripts\lint_knowledge_cards.py`
- `C:\Users\User\anaconda3\envs\devmatch-ai\python.exe scripts\evaluate_lightweight_rag.py`

## 재발 방지·메모

fast path 답변은 RAG 검색 결과와 별개로 동작한다. 따라서 fast path 응답에서 `retrieved_concept_ids`가 비어 있는 것은 정상이다. “승인된 지식이 RAG knowledge로 승격되었는가”는 workflow 응답 메타데이터가 아니라 `retrieve_context(...)` 또는 evaluator로 확인해야 한다.

향후 generated concept card가 늘어나면 일반 용어(`REST API`, `API`, `HTTP`)가 특정 card에 과매칭될 수 있으므로, fast path와 RAG metadata의 경계를 계속 테스트로 유지한다.
