---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 자유 질문 처리 시 의도 라우팅(Intent Routing) 누락 문제 원인 분석 및 해결 기록"

---

# AI review free questions needed intent routing

- Date: 2026-05-16
- Area: ai
- Severity: medium

## Symptoms

Free-question answers could drift when the learner asked a natural tail question that branched away from the original diagnostic problem. Standalone questions such as `분산환경이 어떤 환경을 의미하는 것인가요?` needed to be answered directly, while vague questions such as `왜요?` still needed the original problem context.

During live endpoint verification, vague clarification questions also retrieved a low-score `java-equals` card together with N+1 cards, which caused the model to explain `equals()` instead of the original JPA/N+1 context.

## Cause

The Phase 4.7 lightweight workflow did not have an explicit free-question intent router. It used a simple query policy that could not distinguish concept definition, comparison, practical application, original-problem reasoning, and vague clarification questions.

The workflow also accepted all retrieved contexts above zero score, so weak keyword overlap could inject unrelated cards into the prompt.

## Fix

- Added deterministic free-question intent routing: `ai/app/workflow/intent.py`.
- Connected intent routing to workflow retrieval policy: `ai/app/workflow/nodes.py:21`.
- Added answer relevance validation for standalone free questions so stale original-context answers are rejected: `ai/app/workflow/nodes.py:58`.
- Added a workflow context score floor to remove low-score unrelated concept cards: `ai/app/workflow/nodes.py:15`.
- Added regression tests for concept, comparison, practical, clarification, stale-answer, and low-score-context cases: `ai/tests/test_intent_routing.py`, `ai/tests/test_workflow_runner.py`.

## Prevention / Notes

Free-question mode should treat the latest learner message as the primary intent unless the message is too vague to stand alone. RAG context must be selected by intent, not by blindly mixing the original problem with every learner question.
