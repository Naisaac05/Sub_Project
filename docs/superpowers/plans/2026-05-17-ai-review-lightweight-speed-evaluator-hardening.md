# AI Review Lightweight Speed + Evaluator Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Phase 4.7 free-question answers faster on a CPU-only laptop while strengthening evaluator checks for intent routing and stale-context protection.

**Architecture:** Keep full LangChain/Chroma work out of this phase. Add a deterministic lightweight-answer path before Ollama for common standalone concept questions, keep fallback safe, and expand the evaluator to measure retrieval, intent, and RAG policy behavior.

**Tech Stack:** Python 3.11, FastAPI app modules, unittest, local JSONL evaluator.

---

### Task 1: Speed Policy And Lightweight Answers

**Files:**
- Create: `ai/app/workflow/lightweight_answers.py`
- Modify: `ai/app/workflow/nodes.py`
- Test: `ai/tests/test_workflow_runner.py`

- [ ] Add tests proving common standalone concept questions skip the model generator and return a Korean answer.
- [ ] Add tests proving free-question max token limits are lower than the old 120-token cap.
- [ ] Implement topic matching for `API`, `aria-label`, `pagination`, `distributed environment`, `infinite scroll`, and `DTO`.
- [ ] Wire the fast path before `generate_answer_node` calls Ollama.

### Task 2: Encoding-Safe Intent And Fallback Text

**Files:**
- Modify: `ai/app/workflow/intent.py`
- Modify: `ai/app/workflow/nodes.py`
- Test: `ai/tests/test_intent_routing.py`, `ai/tests/test_workflow_runner.py`

- [ ] Replace mojibake Korean constants with Unicode-escaped or ASCII-safe source literals.
- [ ] Ensure standalone Korean questions such as `분산환경이 어떤 환경을 의미하는 것인가요?` classify as latest-question-only.
- [ ] Ensure fallback text contains the latest-question topic and no stale original-question terms.

### Task 3: Evaluator Hardening

**Files:**
- Modify: `ai/evals/golden_dataset.jsonl`
- Modify: `ai/scripts/evaluate_lightweight_rag.py`
- Test: `ai/tests/test_lightweight_evaluator.py`

- [ ] Expand the golden dataset beyond the initial 3 rows with standalone and context-dependent free questions.
- [ ] Report `intent_accuracy`, `rag_policy_accuracy`, and `stale_context_absent_rate`.
- [ ] Keep retrieval hit thresholds for rows with expected concepts.

### Task 4: Verification

**Files:**
- Verify only unless a test failure exposes a bug.

- [ ] Run `python -m unittest discover -s tests -v` from `ai`.
- [ ] Run `python scripts\lint_knowledge_cards.py` from `ai`.
- [ ] Run `python scripts\evaluate_lightweight_rag.py` from `ai`.
- [ ] Run `python -m compileall app scripts tests` from `ai`.
- [ ] If the Python AI server is running, restart it and make one live `POST /api/review/free-question` smoke call.
