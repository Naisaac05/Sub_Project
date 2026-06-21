---
type: troubleshooting
category: rag
status: active
updated: 2026-06-18
description: "한국어 자유 질문이 문맥을 잃고 원래 RAG 컨텍스트로 표류(Drift)하는 현상 해결 기록"

---

# AI review Korean free question drifted to original RAG context

- Date: 2026-05-16
- Area: ai
- Severity: medium

## Symptoms

When the learner asked `분산환경이 어떤 환경을 의미하는 것인가요?`, the AI answer talked about lazy loading, additional queries, and repeated queries. The answer was unrelated to the latest learner question and followed the original JPA/N+1 review context instead.

## Cause

The free-question RAG query only treated Latin or numeric technical terms, such as `API` or `N+1`, as standalone learner questions. Korean-only technical terms such as `분산환경` were not recognized as standalone questions.

Because of that, the query mixed the original diagnostic question, selected answer, correct answer, and latest learner question. This retrieved unrelated concept cards such as `spring-fetch-join` and `spring-n-plus-one`, and the model answered using that stale context.

The running FastAPI process on port 8001 also had not reloaded the corrected code, so the stale behavior persisted until the Python server was restarted.

## Fix

- Updated free-question query routing so non-vague Korean learner questions are searched by the latest learner question only: `ai/app/workflow/nodes.py:122`.
- Kept short context-dependent follow-ups such as `왜요?` and `다시 설명해줘` eligible to use the original context.
- Strengthened the free-question prompt so unrelated background context is ignored: `ai/app/prompts.py:82`.
- Added a regression test for Korean-only technical terms such as `분산환경`: `ai/tests/test_workflow_runner.py:88`.
- Restarted the Python AI server on port 8001 and verified the live endpoint returns `retrieved_concept_ids: []` and `fallback_used: false` for the `분산환경` question.

## Prevention / Notes

For free-question mode, the latest learner question must be the primary intent. RAG should only use the original diagnostic context when the learner question is too vague to stand alone.
