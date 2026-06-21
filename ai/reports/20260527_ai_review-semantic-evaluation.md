---
type: report
category: evaluation
status: active
updated: 2026-06-18
description: "AI 시스템 시맨틱 평가(Semantic Evaluation) 도입 분석 리포트"

---

# AI Review Semantic Evaluation

> Date: 2026-05-27
> Scope: P3 Semantic Evaluation for local LLM inference orchestration

## Summary

The Python workflow now applies a deterministic semantic safety judge after validation/confidence/fallback and before answer cache persistence.

This first version is rule-based and does not call another LLM. The goal is bounded latency and reproducible local/CI behavior.

## Runtime Flags

- `evidence_missing`: generated answer has no retrieved evidence but contains concrete technical claims.
- `contradiction_suspected`: generated answer appears to reference a known concept that was not retrieved for the request.
- `hallucination_suspected`: generated answer is weakly grounded and contains concrete implementation claims.

Fallback, cache, static fast-path, generated-card fast-path, and degraded/template routes are exempt from the semantic judge.

## Cache Policy

Answers with either of these flags are not written to the answer cache:

- `hallucination_suspected`
- `contradiction_suspected`

Existing cache hits remain readable. The policy only blocks newly generated suspicious answers from becoming future cache hits.

## Evaluator Metrics

`ai/scripts/evaluate_lightweight_rag.py` now reports:

- `semantic_grounding_pass_rate`
- `contradiction_absent_rate`
- `hallucination_cache_ban_rate`

These metrics are derived from workflow `quality_flags`, so they run without Ollama in deterministic local tests.

## Verification

Run from `ai/`:

```powershell
.\.venv\Scripts\python.exe -m unittest tests.test_semantic_evaluation tests.test_workflow_runner tests.test_lightweight_evaluator
```

Expected result:

```text
Ran 44 tests
OK
```

## Follow-up

This closes the first P3 semantic safety layer. The next semantic improvement should expand the golden dataset and add stronger answer-grounding checks in the Full RAG Evaluation Pipeline.
