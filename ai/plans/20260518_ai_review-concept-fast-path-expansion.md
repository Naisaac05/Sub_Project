---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 개념(Concept) Fast Path 확장 구현 계획"

---

# AI Review Concept Fast Path Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand the lightweight free-question path so common programming concept questions answer without local Ollama model latency.

**Architecture:** Keep the existing Phase 4.7c fast path. Add more aliases and short Korean concept answers in `ai/app/workflow/lightweight_answers.py`, then prove through workflow tests that the generator is not called for common concept questions.

**Tech Stack:** Python 3.11, unittest, FastAPI AI workflow modules.

---

### Task 1: Add Common Concept Fast-Path Tests

**Files:**
- Modify: `ai/tests/test_workflow_runner.py`

- [ ] Add a parameterized unittest that asks common concept questions such as `리스트 컴프리헨션이 뭔지 모르겠음`, `REST API가 뭐야?`, `JSON이 뭐야?`, `Optional은 왜 써?`, `Stream map filter 차이가 뭐야?`, `ORM이 뭔데?`, and `JPA 엔티티가 뭐야?`.
- [ ] Use a generator that raises `AssertionError` so the test fails until the fast path handles each question.
- [ ] Assert each response uses `model_used == "lightweight-template"` and contains the expected keyword.

### Task 2: Expand Lightweight Answer Catalog

**Files:**
- Modify: `ai/app/workflow/lightweight_answers.py`

- [ ] Add short Korean answers for Python, web, Java, and Spring concepts.
- [ ] Add aliases for Korean, English, and casual phrasing.
- [ ] Keep answers to two concise sentences to preserve speed and UI readability.

### Task 3: Verify Existing Behavior

**Files:**
- Verify only unless failures expose a bug.

- [ ] Run `python -m unittest discover -s tests -v` from `ai`.
- [ ] Run `python scripts\lint_knowledge_cards.py` from `ai`.
- [ ] Run `python scripts\evaluate_lightweight_rag.py` from `ai`.
- [ ] Run `python -m compileall app scripts tests` from `ai`.
- [ ] Call the live Python API with `리스트 컴프리헨션이 뭔지 모르겠음` and check `model_used` and `latency_ms`.
