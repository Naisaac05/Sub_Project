---
type: plan
category: rag
status: active
updated: 2026-06-18
description: "RAG Cards v2 레거시 분리 및 격리 마이그레이션 플랜"

---

# RAG Cards v2 Isolated Migration Implementation Plan

> **For agentic workers:** Execute task-by-task with test-first development. Git commits are prohibited for this plan.

**Goal:** Build and verify a dry-run-only, isolated v2 RAG card migration.

**Architecture:** Keep production loading on the existing concepts root. Make the migration script a pure generation and evaluation pipeline whose only declared output root is `concepts_v2`, with explicit validation helpers and injected retrieval dependencies.

**Tech Stack:** Python 3.11, Pydantic v2, unittest, existing RAG retriever adapters.

---

### Task 1: Lock Safety And Schema Rules

- [ ] Add failing tests for v2 output isolation, rejected `--write`, draft statuses, card ID rules, payload policy, and timezone-aware timestamps.
- [ ] Run the focused tests and confirm they fail for the missing behavior.
- [ ] Implement the minimal schema and migration helpers.
- [ ] Run the focused tests and confirm they pass.

### Task 2: Generate Concept-Centered Draft Cards

- [ ] Add failing tests for SQL parsing, zero-based correct answers, E2E exclusion, broad-term specialization, merge criteria, and source ID trace-only behavior.
- [ ] Run the focused tests and confirm they fail.
- [ ] Implement extraction, concept drafting, clustering, and draft card creation.
- [ ] Run the focused tests and confirm they pass.

### Task 3: Evaluate Retrieval Without Leakage Claims

- [ ] Add failing tests proving LOO retrieves 50 candidates before removing the source card.
- [ ] Run the focused tests and confirm they fail.
- [ ] Implement exact-match baseline and LOO analytics.
- [ ] Run the focused tests and confirm they pass.

### Task 4: Preserve Workflow Intent Policy

- [ ] Add failing tests proving OFF_TOPIC and UNKNOWN do not call RAG retrieval and FOLLOW_UP reuses existing context behavior.
- [ ] Run the focused tests and confirm they fail.
- [ ] Implement the minimal retrieval gate.
- [ ] Run workflow regression tests.

### Task 5: Execute Verification Sequence

- [ ] Create a backup snapshot without modifying production cards.
- [ ] Run schema and migration unit tests.
- [ ] Run `--validate-only`, `--limit 10`, and `--limit 30` without `--write`.
- [ ] Run lint, retrieval smoke, workflow regression, and `git diff --check`.
- [ ] Record any diagnosed and fixed errors in `error/`.

