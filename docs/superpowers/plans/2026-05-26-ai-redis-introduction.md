# AI Redis Introduction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional Redis-backed shared answer cache and document Redis admission/single-flight follow-up design.

**Architecture:** Keep `answer_cache.py` as the public API and add a small Redis adapter module behind it. Redis reads/writes are best-effort; memory and existing JSONL fallback continue to protect the answer path.

**Tech Stack:** Python stdlib, optional `redis` package via lazy import, unittest fake Redis clients.

---

### Task 1: Redis Answer Cache Adapter

**Files:**
- Create: `ai/app/workflow/redis_answer_cache.py`
- Modify: `ai/app/workflow/answer_cache.py`
- Test: `ai/tests/test_answer_cache.py`

- [x] **Step 1: Write the failing test**

Add fake Redis tests for cache hit hydration, TTL writes, and Redis failure fallback.

- [x] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests.test_answer_cache`
Expected: FAIL because Redis adapter injection functions do not exist.

- [x] **Step 3: Write minimal implementation**

Create the adapter, lazy-load Redis client from `AI_REVIEW_REDIS_URL`, and make `get_cached_answer()` / `put_cached_answer()` use it when `AI_REVIEW_ANSWER_CACHE_BACKEND=redis`.

- [x] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests.test_answer_cache`
Expected: PASS.

### Task 2: Redis Runbook and TODO

**Files:**
- Create: `docs/2026-05-26-ai-review-redis-introduction.md`
- Modify: `docs/2026-05-26-ai-inference-orchestration-todolist.md`

- [x] **Step 1: Document Redis answer cache**

Document env values, key shape, TTL, fallback behavior, and how cache namespace works.

- [x] **Step 2: Document admission/single-flight future design**

Include Redis key sketches and why implementation moves to P3.

- [x] **Step 3: Mark TODO complete**

Check P2 Redis and its three child items.

- [x] **Step 4: Run focused verification**

Run: `python -m unittest tests.test_answer_cache tests.test_workflow_degraded_modes`
Expected: PASS, or report missing optional dependencies if unrelated tests cannot import.
