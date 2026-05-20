# AI Review Lightweight Phase 4.7 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a CPU/RAM-friendly workflow, confidence scoring, fallback routing, and evaluator for AI review without requiring heavy RAG packages.

**Architecture:** `service.py` calls a lightweight workflow runner that mirrors LangGraph node boundaries using plain Python functions. The runner uses existing concept card retrieval, validates generated answers, computes confidence, and falls back to template responses when local model output is weak or unavailable.

**Tech Stack:** Python standard library, Pydantic, FastAPI runtime, existing Markdown concept cards, unittest.

**Execution Order:** Run only after `docs/superpowers/plans/2026-05-16-ai-review-rag-phase-1.md` is complete. Do not execute concurrently with Phase 1 because both plans modify `ai/app/service.py`.

---

## File Structure

- Create `ai/app/scoring.py`: weighted confidence scoring.
- Create `ai/app/workflow/state.py`: workflow state dataclasses.
- Create `ai/app/workflow/nodes.py`: retrieve/rule/generate/validate/confidence/fallback nodes.
- Create `ai/app/workflow/runner.py`: sequential LangGraph-compatible runner.
- Create `ai/evals/golden_dataset.jsonl`: small local evaluation set.
- Create `ai/scripts/evaluate_lightweight_rag.py`: evaluator command.
- Create `ai/tests/test_scoring.py`, `ai/tests/test_workflow_runner.py`, `ai/tests/test_lightweight_evaluator.py`.
- Modify `ai/app/service.py`: delegate to workflow runner.
- Modify `ai/requirements.txt`: keep base dependencies only.

### Task 1: Confidence Scoring

**Files:**
- Create: `ai/app/scoring.py`
- Test: `ai/tests/test_scoring.py`

- [ ] **Step 1: Write failing tests**

Test weighted score normalization and threshold labels.

- [ ] **Step 2: Run RED**

Run: `python -m unittest tests.test_scoring -v`

Expected: FAIL because `app.scoring` does not exist.

- [ ] **Step 3: Implement scoring**

Implement `ConfidenceInputs`, `ConfidenceResult`, and `calculate_confidence()`.

- [ ] **Step 4: Run GREEN**

Run: `python -m unittest tests.test_scoring -v`

Expected: PASS.

### Task 2: Workflow Runner

**Files:**
- Create: `ai/app/workflow/state.py`
- Create: `ai/app/workflow/nodes.py`
- Create: `ai/app/workflow/runner.py`
- Modify: `ai/app/service.py`
- Test: `ai/tests/test_workflow_runner.py`

- [ ] **Step 1: Write failing tests**

Test successful generation, non-Korean fallback, low-confidence fallback, and Ollama exception fallback using an injected fake generator.

- [ ] **Step 2: Run RED**

Run: `python -m unittest tests.test_workflow_runner -v`

Expected: FAIL because workflow modules do not exist.

- [ ] **Step 3: Implement workflow**

Implement deterministic node functions and runner. Keep all heavy dependencies out.

- [ ] **Step 4: Run GREEN**

Run: `python -m unittest tests.test_workflow_runner -v`

Expected: PASS.

### Task 3: Lightweight Evaluator

**Files:**
- Create: `ai/evals/golden_dataset.jsonl`
- Create: `ai/scripts/evaluate_lightweight_rag.py`
- Test: `ai/tests/test_lightweight_evaluator.py`

- [ ] **Step 1: Write failing tests**

Test that the evaluator loads dataset rows and produces retrieval metrics.

- [ ] **Step 2: Run RED**

Run: `python -m unittest tests.test_lightweight_evaluator -v`

Expected: FAIL because evaluator files do not exist.

- [ ] **Step 3: Implement evaluator**

Implement JSONL loading and keyword retrieval checks.

- [ ] **Step 4: Run GREEN**

Run: `python -m unittest tests.test_lightweight_evaluator -v`

Expected: PASS.

### Task 4: Requirements And Verification

**Files:**
- Modify: `ai/requirements.txt`
- Modify: `ai/requirements-rag.txt`

- [ ] **Step 1: Keep base requirements light**

Ensure `requirements.txt` contains only `fastapi`, `uvicorn[standard]`, and `pydantic`.

- [ ] **Step 2: Run verification**

Run:

```powershell
python -m unittest discover -s tests -v
python scripts\lint_knowledge_cards.py
python scripts\evaluate_lightweight_rag.py
python -m compileall app scripts tests
```

Expected: all commands exit 0.

## Self-Review

- Spec coverage: covers lightweight Phase 4 through 4.7 only.
- Placeholder scan: no TODO/TBD items.
- Type consistency: workflow state uses existing `AiGenerateRequest`, `AiGenerateResponse`, and `RetrievedContext` types.

