# BGE-M3 Runtime Intent Classifier Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect BGE-M3 embedding intent classification to the production workflow and tests without any rule-based runtime fallback.

**Architecture:** Add a focused embedding classifier that returns the existing workflow intent contract. The workflow injects or uses this classifier before retrieval. Embedding failures and low-confidence results map to `UNKNOWN/general_question`.

**Tech Stack:** Python, Ollama embeddings, unittest/pytest

---

### Task 1: Production Intent Classifier

**Files:**
- Create: `ai/app/workflow/embedding_intent.py`
- Test: `ai/tests/test_embedding_intent.py`

- [ ] Write failing tests for semantic ranking, cached prototypes, low-confidence UNKNOWN, and embedding failure UNKNOWN.
- [ ] Run the focused tests and confirm they fail because the classifier is missing.
- [ ] Implement the minimal BGE-M3 classifier and 10-class-to-workflow mapping.
- [ ] Run the focused tests and confirm they pass.

### Task 2: Workflow Integration

**Files:**
- Modify: `ai/app/workflow/nodes.py`
- Modify: `ai/tests/test_workflow_runner.py`
- Modify: `ai/tests/test_intent_routing.py`

- [ ] Write failing tests proving the workflow uses the BGE classifier and never calls the legacy rule classifier.
- [ ] Run the focused tests and confirm the expected failures.
- [ ] Replace runtime `classify_free_question` calls with the BGE classifier.
- [ ] Convert intent routing tests to the production embedding classifier contract.
- [ ] Run the focused tests and confirm they pass.

### Task 3: Documentation And Verification

**Files:**
- Modify: `ai/README.md`
- Modify: `docs/ai-review-runtime-profiles.md`

- [ ] Document the BGE-only classifier, thresholds, caching, and UNKNOWN fallback.
- [ ] Run focused embedding, intent, workflow, prompt, and RAG tests.
- [ ] Run the full AI test suite.
- [ ] Run `git diff --check`.

No commit is created because the user explicitly requested no premature commits.
