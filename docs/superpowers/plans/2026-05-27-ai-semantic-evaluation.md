# AI Semantic Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add deterministic semantic safety flags and prevent suspicious generated answers from being cached.

**Architecture:** Add a small Python judge module, call it from workflow runner before cache writes, and expose deterministic metrics in the existing lightweight evaluator.

**Tech Stack:** Python stdlib, Pydantic response model already carrying `quality_flags`, unittest.

---

### Task 1: Semantic Judge

**Files:**
- Create: `ai/app/evaluation/semantic.py`
- Create: `ai/tests/test_semantic_evaluation.py`

- [x] **Step 1: Write the failing test**

Add tests for `evidence_missing`, `hallucination_suspected`, and fallback/lightweight exemption.

- [x] **Step 2: Run test to verify it fails**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_semantic_evaluation`
Expected: FAIL because `app.evaluation.semantic` does not exist.

- [x] **Step 3: Write minimal implementation**

Create deterministic judge helpers and cacheability decision.

- [x] **Step 4: Run test to verify it passes**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_semantic_evaluation`
Expected: PASS.

### Task 2: Workflow Cache Ban

**Files:**
- Modify: `ai/app/workflow/runner.py`
- Modify: `ai/tests/test_workflow_runner.py`

- [x] **Step 1: Write the failing test**

Add a workflow test where generated ungrounded concrete answer gets semantic flags and is not returned from cache on the next run.

- [x] **Step 2: Run test to verify it fails**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_workflow_runner`
Expected: FAIL because workflow does not apply semantic cache policy.

- [x] **Step 3: Write minimal implementation**

Apply semantic judge before cache writes in sync and streaming workflow paths.

- [x] **Step 4: Run test to verify it passes**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_workflow_runner`
Expected: PASS.

### Task 3: Evaluator Metrics and Docs

**Files:**
- Modify: `ai/scripts/evaluate_lightweight_rag.py`
- Modify: `ai/tests/test_lightweight_evaluator.py`
- Create: `docs/2026-05-27-ai-review-semantic-evaluation.md`
- Modify: `docs/2026-05-26-ai-inference-orchestration-todolist.md`

- [x] **Step 1: Write evaluator metric test**

Add tests for semantic grounding/contradiction/cache-ban rates from workflow quality flags.

- [x] **Step 2: Implement evaluator metrics**

Count semantic flags from workflow responses.

- [x] **Step 3: Document runbook and mark TODO**

Document flags, cache policy, and evaluator metrics; check P3 Semantic Evaluation items.

- [x] **Step 4: Run focused verification**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_semantic_evaluation tests.test_workflow_runner tests.test_lightweight_evaluator`
