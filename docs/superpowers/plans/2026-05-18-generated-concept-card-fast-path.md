# Generated Concept Card Fast Path Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let approved generated concept cards answer high-confidence learner concept questions without calling Ollama.

**Architecture:** Keep existing static lightweight answers first. If no static answer exists and `query_resolver` matched a generated concept id, load the concept card and return its `핵심 설명` as a compact `lightweight-template` answer.

**Tech Stack:** Python 3.11, unittest, existing RAG concept card loader.

---

### Task 1: Workflow Regression Tests

**Files:**
- Modify: `ai/tests/test_workflow_runner.py`

- [ ] Add a test where `ConrollerAdvice는 실무에서 어떻게 쓰여?` must not call the generator.
- [ ] Assert `model_used == "lightweight-template"`.
- [ ] Assert the answer contains `@ControllerAdvice`, `Spring MVC`, and `예외`.
- [ ] Assert `retrieved_concept_ids == []` because fast path answers clear RAG metadata.
- [ ] Run the focused test and verify it fails before implementation.

### Task 2: Concept Card Answer Source

**Files:**
- Modify: `ai/app/workflow/lightweight_answers.py`
- Modify: `ai/app/workflow/nodes.py`

- [ ] Extend `lightweight_answer_for()` with optional `matched_concept_id`.
- [ ] Keep static `_ANSWERS` lookup first.
- [ ] If no static answer exists, find the matched card from `load_concept_cards()`.
- [ ] Return the card `핵심 설명` section when available.
- [ ] Pass `state.resolved_query.matched_concept_id` from `generate_answer_node()`.
- [ ] Run the focused workflow test and verify it passes.

### Task 3: Verification

**Files:**
- Existing Python AI tests and scripts.

- [ ] Run `python -m unittest discover -s tests -v`.
- [ ] Run `python scripts\lint_knowledge_cards.py`.
- [ ] Run `python scripts\evaluate_lightweight_rag.py`.
- [ ] Run a sample `ConrollerAdvice` request and confirm latency is under 50ms and `model_used` is `lightweight-template`.
