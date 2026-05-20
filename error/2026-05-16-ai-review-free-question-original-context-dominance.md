# AI review free question answered original context instead of learner question

- Date: 2026-05-16
- Area: ai
- Severity: medium

## Symptoms

On the review free-question flow, asking `API가 뭔데?` returned an answer focused on the original diagnostic context, such as lazy loading, circular references, or DB indexing, instead of explaining what an API is. The Python server returned `200 OK`, so the issue was not request failure.

## Cause

The free-question prompt placed `[Original Question]` before `[Learner Free Question]`, so small local models tended to answer the diagnostic context first. Retrieval also built the RAG query from the original question, correct answer, selected answer, and free question together, so a learner question with its own technical term could still pull unrelated concept cards from the original question.

When no relevant concept card existed for a valid free question, confidence scoring treated `no retrieved contexts` as low confidence and could replace a good Korean model answer with the generic fallback.

## Fix

- Moved `[Learner Free Question]` before `[Original Question]` and made the prompt explicitly answer the learner question first: `ai/app/prompts.py:81`.
- For free questions with an explicit technical token, retrieval now searches the learner question before the original diagnostic context: `ai/app/workflow/nodes.py:126`.
- Free-question answers without retrieved contexts now receive neutral retrieval confidence instead of automatic low confidence: `ai/app/workflow/nodes.py:81`.
- Added regression tests for prompt priority, retrieval priority, and preventing unrelated fallback replacement: `ai/tests/test_service_helpers.py:25`, `ai/tests/test_workflow_runner.py:50`.

## Prevention / Notes

Free-question flow should treat the latest learner message as the primary intent. The original test question is background only. If candidate approval or richer concept cards are added later, keep this priority rule so the RAG context does not override the user's actual question.
