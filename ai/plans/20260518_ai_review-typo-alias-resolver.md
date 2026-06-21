---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 오타 및 별칭 리졸버(Typo Alias Resolver) 구현 계획"

---

# AI Review Typo Alias Resolver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a laptop-friendly typo and alias resolver for learner free questions before fast path and RAG retrieval.

**Architecture:** Create a small resolver module with deterministic alias and typo matching. Wire it into workflow state and nodes so free-question intent, retrieval, and lightweight answers use the corrected query while unknown/weak matches preserve the original question.

**Tech Stack:** Python 3.11, unittest, existing FastAPI AI service modules.

---

### Task 1: Resolver Unit

**Files:**
- Create: `ai/app/workflow/query_resolver.py`
- Test: `ai/tests/test_query_resolver.py`

- [ ] Write tests for alias, typo, and weak match behavior.
- [ ] Run `python -m unittest tests.test_query_resolver -v` and verify failure before implementation.
- [ ] Implement `ResolvedQuery` and `resolve_learner_query()`.
- [ ] Run focused tests and verify pass.

### Task 2: Workflow Integration

**Files:**
- Modify: `ai/app/workflow/state.py`
- Modify: `ai/app/workflow/nodes.py`
- Test: `ai/tests/test_workflow_runner.py`

- [ ] Add workflow tests showing `arila-label` reaches the `aria-label` fast path.
- [ ] Add workflow tests showing typo-corrected RAG query retrieves the generated `aria-label` card when generator path is used.
- [ ] Run focused workflow tests and verify failure before implementation.
- [ ] Store resolved query metadata in workflow state.
- [ ] Use resolved query in intent classification, RAG query, and lightweight answer lookup.
- [ ] Run focused workflow tests and verify pass.

### Task 3: Verification

**Files:**
- Existing Python AI tests and scripts.

- [ ] Run `python -m unittest discover -s tests -v`.
- [ ] Run `python scripts\lint_knowledge_cards.py`.
- [ ] Run `python scripts\evaluate_lightweight_rag.py`.
- [ ] Run sample calls for typo questions and confirm answer quality and latency.
