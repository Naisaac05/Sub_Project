---
type: plan
category: rag
status: active
updated: 2026-06-18
description: "RAG v2 도입 전 Shadow Fast Path 검증 및 적용 계획"

---

# RAG v2 Shadow Fast Path Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Select draft approval-review candidates and measure v2 Fast Path potential without approving or activating cards.

**Architecture:** A read-only evaluator loads isolated v2 cards, applies the existing quality audit, verifies self-term Top1 retrieval and payload completeness, then runs fixed user-question shadow samples through intent classification, v2 retrieval, payload selection, and fallback accounting.

**Tech Stack:** Python, unittest, Pydantic, lexical retrieval

---

### Task 1: Define Shadow Evaluation Contract

**Files:**
- Create: `ai/tests/test_shadow_rag_cards_v2.py`

- [ ] Add tests for intent classification, candidate selection, Fast Path success, fallback, and metrics.
- [ ] Run focused tests and confirm expected RED failures.

### Task 2: Implement Read-Only Shadow Evaluator

**Files:**
- Create: `ai/scripts/shadow_rag_cards_v2.py`

- [ ] Implement candidate selection without card mutation.
- [ ] Implement shadow intent, retrieval, payload selection, latency, score distribution, and LLM-call accounting.
- [ ] Add at least five samples for each generated intent, including React key, Java equals, Spring cache, BFS, and JSX.

### Task 3: Execute And Report

**Files:**
- Create: `docs/superpowers/specs/2026-06-12-rag-cards-v2-shadow-validation.json`
- Create: `docs/superpowers/specs/2026-06-12-rag-cards-v2-shadow-validation-summary.md`

- [ ] Execute the evaluator against all 142 draft cards.
- [ ] Record candidate count, Fast Path/fallback rates, expected Ollama reduction, latency, score distribution, and problem-card Top10.
- [ ] Verify draft status, v1 hash, active store, tests, and diff.
