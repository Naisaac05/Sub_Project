# AI Full RAG Evaluation Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand deterministic RAG evaluation coverage and automate candidate promotion before/after evaluation.

**Architecture:** Keep `scripts/evaluate_lightweight_rag.py` as the evaluation entrypoint. Add deterministic dataset expansion inside default `load_dataset()`, add answer-grounding metrics to `evaluate_dataset()`, and extend `promote_and_evaluate_knowledge.py` with baseline/post-promotion evaluation reports and metric deltas.

**Tech Stack:** Python stdlib, JSONL seed dataset, unittest.

---

### Task 1: Golden Dataset Expansion

**Files:**
- Modify: `ai/scripts/evaluate_lightweight_rag.py`
- Modify: `ai/tests/test_lightweight_evaluator.py`

- [x] **Step 1: Write the failing test**

Assert default `load_dataset()` returns at least 200 rows with unique ids.

- [x] **Step 2: Run test to verify it fails**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_lightweight_evaluator.LightweightEvaluatorTest.test_loads_bundled_dataset`
Expected: FAIL because the current seed dataset has 69 rows.

- [x] **Step 3: Implement deterministic expansion**

Generate variants from curated rows until the default dataset reaches 200 rows.

- [x] **Step 4: Run test to verify it passes**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_lightweight_evaluator.LightweightEvaluatorTest.test_loads_bundled_dataset`
Expected: PASS.

### Task 2: Answer Grounding Metric

**Files:**
- Modify: `ai/scripts/evaluate_lightweight_rag.py`
- Modify: `ai/tests/test_lightweight_evaluator.py`

- [x] **Step 1: Write the failing test**

Assert evaluator reports `answer_grounding_rate` and calculates it from semantic flags, required keywords, and forbidden claims.

- [x] **Step 2: Run test to verify it fails**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_lightweight_evaluator`
Expected: FAIL because `answer_grounding_rate` does not exist.

- [x] **Step 3: Implement metric**

Count grounded workflow rows and return the rate.

- [x] **Step 4: Run test to verify it passes**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_lightweight_evaluator`
Expected: PASS.

### Task 3: Promotion Before/After Evaluation

**Files:**
- Modify: `ai/scripts/promote_and_evaluate_knowledge.py`
- Modify: `ai/tests/test_promotion_workflow.py`

- [x] **Step 1: Write the failing test**

Assert promotion workflow evaluates before and after promotion and reports metric deltas.

- [x] **Step 2: Run test to verify it fails**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_promotion_workflow`
Expected: FAIL because only after-evaluation exists.

- [x] **Step 3: Implement before/after evaluation**

Add `before_evaluation`, `evaluation`, and `evaluation_delta` report fields.

- [x] **Step 4: Run test to verify it passes**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_promotion_workflow`
Expected: PASS.

### Task 4: Docs and TODO

**Files:**
- Create: `docs/2026-05-27-ai-review-full-rag-evaluation-pipeline.md`
- Modify: `docs/2026-05-26-ai-inference-orchestration-todolist.md`

- [x] **Step 1: Document pipeline**

Document dataset expansion, metrics, and promotion before/after evaluation.

- [x] **Step 2: Update TODO**

Check P3 Full RAG Evaluation Pipeline and add implementation note.

- [x] **Step 3: Run focused verification**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_lightweight_evaluator tests.test_promotion_workflow`
Expected: PASS.
