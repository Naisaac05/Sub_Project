---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 품질 향상 및 하이브리드 자동 후보 파이프라인 계획"

---

# AI Review Quality Auto Candidates Hybrid Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve answer quality, capture weak learner questions as reviewable knowledge candidates, and harden hybrid retrieval without adding heavy default dependencies.

**Architecture:** Keep the laptop-friendly fast path as the default path. Add quality style metadata and answer quality checks around the existing workflow, then feed low-confidence/no-match questions into the existing candidate approval model as a JSONL queue. Retrieval improvements remain lexical and deterministic by default, with an optional reranker interface that can be enabled later by environment flag.

**Tech Stack:** Python 3.11, unittest, existing FastAPI AI workflow, Markdown concept cards, JSONL candidate/eval files.

---

### Task 1: Phase 12 Answer Quality Signals

**Files:**
- Modify: `ai/app/schemas.py`
- Modify: `ai/app/workflow/state.py`
- Modify: `ai/app/workflow/lightweight_answers.py`
- Modify: `ai/app/workflow/nodes.py`
- Modify: `ai/app/workflow/runner.py`
- Test: `ai/tests/test_workflow_runner.py`

- [x] Add response metadata fields `answer_style` and `quality_flags`.
- [x] Return style-specific lightweight answers for definition, practical, comparison, and related-concept questions.
- [x] Validate that free-question answers directly mention the resolved topic or matched concept.
- [x] Add tests proving practical questions use a practical style and stale/too-generic answers receive quality flags.

### Task 2: Phase 13 Auto Candidate Queue

**Files:**
- Create: `ai/app/knowledge/auto_candidates.py`
- Modify: `ai/app/workflow/runner.py`
- Test: `ai/tests/test_auto_candidates.py`

- [x] Build a deterministic candidate row from low-confidence, fallback, or no-match free questions.
- [x] Include `source`, `source_question`, `resolved_query`, `route`, `confidence_score`, `needs_review_reason`, and `status`.
- [x] Deduplicate candidate IDs by normalized resolved query.
- [x] Keep workflow side effects optional by exposing a function that runner can call with an explicit queue path in tests.

### Task 3: Phase 14 Lightweight Hybrid Retrieval

**Files:**
- Modify: `ai/app/rag/retriever.py`
- Test: `ai/tests/test_rag_retriever.py`

- [x] Add phrase-level exact matching for concept IDs, titles, aliases, and generated-card terms.
- [x] Add conservative negative filtering so generic words do not return unrelated cards.
- [x] Add an optional reranker hook that is disabled by default and does not import heavy packages unless explicitly passed.
- [x] Add tests for exact title priority, generic no-match behavior, and optional reranker ordering.

### Task 4: Evaluator Hardening

**Files:**
- Modify: `ai/scripts/evaluate_lightweight_rag.py`
- Modify: `ai/tests/test_lightweight_evaluator.py`

- [x] Add `quality_flag_absent_rate` for rows that declare `expected_quality_flags_absent`.
- [x] Add `route_accuracy` for rows that declare `expected_route`.
- [x] Keep the existing retrieval and policy metrics intact.
- [x] Verify the full AI test suite, knowledge lint, evaluator, and promotion workflow.
