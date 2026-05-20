# AI Review Observability Reindex Golden Hybrid Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the next AI review phases in order: observability, promotion/re-index workflow, golden dataset expansion, and lightweight hybrid retrieval hardening.

**Architecture:** Add route and resolver metadata to Python AI responses first, then formalize a single promotion/re-index command, expand the offline golden set toward production coverage, and finally improve lexical retrieval scoring without adding heavy memory dependencies.

**Tech Stack:** Python 3.11, unittest, existing FastAPI AI service, Markdown concept cards, JSONL evaluator.

---

### Task 1: Phase 4.11 / 7.5 Observability

**Files:**
- Modify: `ai/app/schemas.py`
- Modify: `ai/app/workflow/state.py`
- Modify: `ai/app/workflow/nodes.py`
- Modify: `ai/app/workflow/runner.py`
- Test: `ai/tests/test_workflow_runner.py`

- [x] Add response fields for `route`, `resolved_query`, `correction_type`, and `matched_concept_id`.
- [x] Mark static fast path, generated card fast path, cache, rag generation, and fallback routes.
- [x] Add workflow tests for static fast path route, generated card route, and RAG generation route.
- [x] Run focused tests and verify they fail before implementation.
- [x] Implement route metadata and rerun focused tests.
- [x] Add structured `ai_review.workflow_completed` observability event metadata.

### Task 2: Phase 8 Re-index / Promotion Workflow

**Files:**
- Create: `ai/scripts/promote_and_evaluate_knowledge.py`
- Test: `ai/tests/test_promotion_workflow.py`

- [x] Add a command wrapper that runs candidate promotion, knowledge lint, changed-only reindex, and lightweight evaluator.
- [x] Return non-zero when any stage fails.
- [x] Test with fake stage functions so the command behavior is deterministic.

### Task 3: Phase 9 Golden Dataset Expansion

**Files:**
- Modify: `ai/evals/golden_dataset.jsonl`
- Modify: `ai/tests/test_lightweight_evaluator.py`

- [x] Expand the golden dataset from 8 rows to at least 30 rows.
- [x] Include typo/alias, generated concept card, stale-context, and core Java/Spring concept cases.
- [x] Add a test that enforces at least 30 rows.
- [x] Keep evaluator metrics passing.
- [x] Evaluate workflow response route, required keywords, forbidden claims, and quality flags when expected fields are present.

### Task 4: Phase 10 Hybrid Retrieval Hardening

**Files:**
- Modify: `ai/app/rag/retriever.py`
- Test: `ai/tests/test_rag_retriever.py`

- [x] Add conservative alias expansion into tokenization/scoring.
- [x] Boost exact concept id/title matches before broad keyword overlap.
- [x] Add tests for `ControllerAdvice`, `aria label`, and pagination aliases.
- [x] Keep unknown/generic queries from returning false positives.
