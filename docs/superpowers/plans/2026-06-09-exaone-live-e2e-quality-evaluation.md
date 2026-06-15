# EXAONE Live E2E Quality Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and run a repeatable live evaluator that compares the real EXAONE workflow with BGE-M3 RAG against forced No-RAG.

**Architecture:** A standalone evaluator loads a small JSONL dataset and calls the existing `run_review_workflow()` twice per question. Forced No-RAG patches only the workflow retrieval dependency, while result extraction and report rendering remain deterministic and unit-testable without Ollama.

**Tech Stack:** Python standard library, `unittest`, `unittest.mock`, existing AI review workflow, Ollama EXAONE, Ollama BGE-M3.

**User Constraint:** Do not create Git commits.

---

### Task 1: Add Dataset And Evaluator Unit Tests

**Files:**
- Create: `ai/evals/exaone_live_e2e/dataset.jsonl`
- Create: `ai/tests/test_exaone_live_e2e_evaluator.py`

- [ ] Add 12 representative questions with required keywords and forbidden claims.
- [ ] Write tests for result extraction, metric aggregation, report rendering, and forced No-RAG patching.
- [ ] Run the focused tests and verify they fail because the evaluator does not exist.

### Task 2: Implement The Live E2E Evaluator

**Files:**
- Create: `ai/scripts/evaluate_exaone_live_e2e.py`

- [ ] Implement dataset loading and CLI options.
- [ ] Implement fresh workflow execution for `rag` and `no_rag_forced`.
- [ ] Clear answer and judge caches before each run.
- [ ] Record answer, route, model, retrieved concepts, latency, fallback, quality flags, rule checks, judge event, and errors.
- [ ] Implement JSON and Markdown report output.
- [ ] Return non-zero when no fresh EXAONE answer is observed or any run errors.
- [ ] Run focused tests and verify they pass.

### Task 3: Document And Run Live Evaluation

**Files:**
- Create: `ai/evals/exaone_live_e2e/README.md`
- Generate: `ai/evals/exaone_live_e2e/REPORT.json`
- Generate: `ai/evals/exaone_live_e2e/REPORT.md`

- [ ] Document the command and interpretation limits.
- [ ] Run the live evaluator for all 12 questions.
- [ ] Inspect full answers and metrics.
- [ ] Run focused evaluator tests again.
- [ ] Run `git diff --check` and final status without committing.
