---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "AI 인퍼런스 게이트웨이 및 LLM 모델 풀 구현 플랜"

---

# AI Inference Gateway Model Pool Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Python-side Ollama inference gateway that routes by model, supports endpoint draining, and emits model capacity metrics.

**Architecture:** Keep existing workflow-facing Ollama functions stable. Insert `app.ollama.gateway` as a small routing/capacity layer used by `app.ollama.client`.

**Tech Stack:** Python stdlib dataclasses/threading, existing unittest suite, httpx streaming tests.

---

### Task 1: Gateway Routing and Capacity

**Files:**
- Create: `ai/app/ollama/gateway.py`
- Create: `ai/tests/test_ollama_gateway.py`

- [x] **Step 1: Write the failing test**

Add tests for model pool parsing, draining skip, fallback endpoint, and per-model capacity independence.

- [x] **Step 2: Run test to verify it fails**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_ollama_gateway`
Expected: FAIL because `app.ollama.gateway` does not exist.

- [x] **Step 3: Write minimal implementation**

Create dataclasses and `ModelPoolGateway` with deterministic model routing and per-model bounded semaphores.

- [x] **Step 4: Run test to verify it passes**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_ollama_gateway`
Expected: PASS.

### Task 2: Client Integration and Metrics

**Files:**
- Modify: `ai/app/ollama/client.py`
- Modify: `ai/tests/test_ollama_client.py`

- [x] **Step 1: Write the failing test**

Add/adjust tests proving stream metrics include endpoint/capacity/in-flight fields and routing uses the gateway endpoint.

- [x] **Step 2: Run test to verify it fails**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_ollama_client`
Expected: FAIL before client integration.

- [x] **Step 3: Write minimal implementation**

Use `ModelPoolGateway` for sync and streaming endpoint selection, acquire/release, and metric fields.

- [x] **Step 4: Run test to verify it passes**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_ollama_client`
Expected: PASS.

### Task 3: Runbook and TODO

**Files:**
- Create: `docs/2026-05-27-ai-review-inference-gateway-model-pool.md`
- Modify: `docs/2026-05-26-ai-inference-orchestration-todolist.md`

- [x] **Step 1: Document gateway config**

Document model pool, capacity, draining, metrics, and health policy.

- [x] **Step 2: Mark TODO complete**

Check P3 Inference Gateway + Model Pool and its three child items.

- [x] **Step 3: Run focused verification**

Run: `.\.venv\Scripts\python.exe -m unittest tests.test_ollama_gateway tests.test_ollama_client`
