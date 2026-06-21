---
type: plan
category: rag
status: active
updated: 2026-06-18
description: "v1 RAG 파이프라인 제거 및 v2 승인 Fast Path 전환 플랜"

---

# Remove v1 RAG and Use v2 Approved Fast Path Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make free-question and follow-up operate without v1 cards, serving approved v2 payloads first and using Ollama on v2 misses.

**Architecture:** `concepts_v2` is the only runtime card source. Free-question skips legacy RAG and lightweight/static answers, then uses Ollama when the approved v2 Fast Path misses. Follow-up no longer loads v1 cards and uses its existing question/session context with Ollama.

**Tech Stack:** Python, unittest, Pydantic, existing workflow and retrieval adapters

---

### Task 1: Lock Runtime Policy With Tests

**Files:**
- Modify: `ai/tests/test_v2_approved_fast_path.py`
- Modify: `ai/tests/test_workflow_runner.py`

- [ ] Add failing tests proving free-question v2 miss calls Ollama without v1 retrieval or lightweight/static answers.
- [ ] Add failing tests proving follow-up does not load v1 cards.
- [ ] Run focused tests and confirm the new assertions fail for the expected legacy calls.

### Task 2: Switch Runtime to v2 Approved Then Ollama

**Files:**
- Modify: `ai/app/workflow/v2_approved_fast_path.py`
- Modify: `ai/app/workflow/nodes.py`
- Modify: `ai/app/workflow/runner.py`
- Modify: `ai/app/workflow/lightweight_answers.py`
- Modify: `ai/app/rag/documents.py`

- [ ] Load approved v2 cards dynamically from `concepts_v2`.
- [ ] Prevent free-question from retrieving v1 contexts.
- [ ] Skip lightweight/static answers for free-question v2 misses.
- [ ] Remove follow-up v1 card loading.
- [ ] Run focused workflow and Fast Path tests.

### Task 3: Remove v1 Assets and v1-only Tools

**Files:**
- Delete: `ai/app/knowledge/concepts/`
- Delete: `ai/scripts/compare_rag_v1_v2.py`
- Modify/Delete v1-only tests and script references discovered by repository search

- [ ] Confirm no runtime module references `CONCEPT_ROOT` or default v1 card loading.
- [ ] Delete the verified v1 card root and v1 comparison-only tool.
- [ ] Run repository search again and remove remaining v1-only runtime references.

### Task 4: Verify and Run Course E2E

**Files:**
- Use: `ai/scripts/evaluate_course_question_shadow.py` or the surviving v2 course evaluator
- Create: `ai/reports/v2_approved_ollama_e2e_2026-06-15.json`

- [ ] Run focused unit and workflow tests.
- [ ] Run the broad AI test suite.
- [ ] Run course-question E2E with v2 approved Fast Path and Ollama fallback.
- [ ] Delete-check v1 root and rerun focused tests.
- [ ] Record any root-cause fixes in `error/`.
