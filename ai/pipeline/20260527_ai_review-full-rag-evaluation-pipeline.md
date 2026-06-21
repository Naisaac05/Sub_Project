---
type: report
category: rag
status: active
updated: 2026-06-18
description: "AI 리뷰 Full RAG 평가 파이프라인 연동 설계 및 개요"

---

# AI Review Full RAG Evaluation Pipeline

> Date: 2026-05-27
> Scope: P3 Full RAG Evaluation Pipeline

## Summary

The deterministic RAG evaluation pipeline now covers 200 default golden rows, answer-grounding metrics, stale-context checks, and candidate promotion before/after evaluation.

## Dataset

`scripts/evaluate_lightweight_rag.py` still reads the curated seed file:

```text
ai/evals/golden_dataset.jsonl
```

When called without a custom path, `load_dataset()` deterministically expands the curated rows to 200 rows by adding marked question variants:

```json
{"generated_variant": true}
```

Passing an explicit path keeps the file exact and does not expand it.

## Metrics

The evaluator reports:

- `retrieval_hit_rate`
- `expected_concept_recall`
- `stale_context_absent_rate`
- `semantic_grounding_pass_rate`
- `contradiction_absent_rate`
- `hallucination_cache_ban_rate`
- `answer_grounding_rate`
- route, fallback, candidate capture, observability, and context gating metrics

`answer_grounding_rate` passes only when a workflow answer has no semantic risk flags, includes all configured required keywords, and avoids configured forbidden claims.

## Candidate Promotion Automation

`scripts/promote_and_evaluate_knowledge.py` now evaluates both sides of promotion:

1. `before_evaluation`
2. promotion
3. lint
4. changed-only reindex
5. post-promotion `evaluation`
6. `evaluation_delta`

The workflow still fails on lint errors, reindex failures, or post-promotion retrieval hit rate below threshold.

## Verification

Run from `ai/`:

```powershell
.\.venv\Scripts\python.exe -m unittest tests.test_lightweight_evaluator tests.test_promotion_workflow
.\.venv\Scripts\python.exe scripts\evaluate_lightweight_rag.py
```

Expected deterministic evaluator shape:

```text
total: 200
retrieval_hit_rate: >= 0.6
rag_policy_accuracy: >= 0.8
workflow_context_accuracy: 1.0
```

Observed on 2026-05-27:

```text
total: 200
retrieval_hit_rate: 0.9917
expected_concept_recall: 0.9917
stale_context_absent_rate: 1.0
answer_grounding_rate: 0.8125
workflow_context_accuracy: 1.0
```
