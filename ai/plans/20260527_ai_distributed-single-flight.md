---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "AI 중복 호출 방지를 위한 분산 Single-Flight 패턴 구현 계획"

---

# AI Distributed Single-Flight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional Redis-backed distributed single-flight for duplicate AI Review generation requests.

**Architecture:** Extend the existing answer cache Redis helper with request-key lock operations. Keep process-local single-flight as the first layer, then use Redis lock/wait behavior only for the local leader when distributed single-flight is explicitly enabled.

**Tech Stack:** Python stdlib, Redis client API already used by `redis_answer_cache`, unittest fake Redis.

---

### Task 1: Redis Lock Primitives

**Files:**
- Modify: `ai/app/workflow/redis_answer_cache.py`
- Modify: `ai/tests/test_answer_cache.py`

- [x] **Step 1: Write the failing test**

Add tests for owner-token guarded release and remote answer wait behavior.

- [x] **Step 2: Run test to verify it fails**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_answer_cache`
Expected: FAIL because lock helpers do not exist.

- [x] **Step 3: Implement minimal Redis lock helpers**

Add distributed single-flight enablement, lock acquire, owner-token release, and answer wait helpers.

- [x] **Step 4: Run test to verify it passes**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_answer_cache`
Expected: PASS.

### Task 2: Workflow Single-Flight Integration

**Files:**
- Modify: `ai/app/workflow/answer_cache.py`
- Modify: `ai/tests/test_answer_cache.py`

- [x] **Step 1: Write the failing test**

Add a test where a remote Redis lock exists, a remote answer appears, and local producer avoids expensive generation by returning cache-hit result.

- [x] **Step 2: Run test to verify it fails**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_answer_cache.AnswerCacheTest.test_distributed_single_flight_waits_for_remote_cached_answer`
Expected: FAIL because `run_single_flight()` ignores Redis lock state.

- [x] **Step 3: Implement integration**

Wrap local leader producer execution with Redis distributed single-flight when enabled.

- [x] **Step 4: Run test to verify it passes**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_answer_cache`
Expected: PASS.

### Task 3: Docs and TODO

**Files:**
- Create: `docs/2026-05-27-ai-review-distributed-single-flight.md`
- Modify: `docs/2026-05-26-ai-inference-orchestration-todolist.md`

- [x] **Step 1: Document runbook**

Document env vars, failure policy, and streaming join limitation.

- [x] **Step 2: Update TODO**

Check P3 Distributed Single-Flight and add implementation note.

- [x] **Step 3: Run focused verification**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_answer_cache tests.test_workflow_runner`
Expected: PASS.
