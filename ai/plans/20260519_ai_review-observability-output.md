---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 관측성(Observability) 출력 지표 구현 계획"

---

# AI Review Observability Output Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Emit AI review structured events to FastAPI and Spring logs with correlation id propagation and log-friendly metrics.

**Architecture:** Add a Python observability helper used by FastAPI routes, then propagate/log `X-Correlation-ID` in the Spring Python AI client. Add candidate backlog logging to the v2 candidate service.

**Tech Stack:** Python logging/json, FastAPI request/response headers, Spring SLF4J, Java `UUID`.

---

### Task 1: FastAPI Structured Event Sink

**Files:**
- Create: `ai/app/observability.py`
- Modify: `ai/app/main.py`
- Test: `ai/tests/test_observability.py`

- [x] **Step 1: Write failing observability tests**
- [x] **Step 2: Implement correlation id and JSON event logging**
- [x] **Step 3: Run focused Python tests**

### Task 2: Spring Correlation And Event Logging

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java`
- Modify: `backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java`
- Modify: `backend/src/main/java/com/devmatch/repository/AiReviewCandidateRepository.java`

- [x] **Step 1: Add correlation header and response event fields**
- [x] **Step 2: Log FastAPI event summaries and candidate backlog counts**
- [x] **Step 3: Run focused backend tests**

### Task 3: Regression Verification

- [x] **Step 1: Run Python observability/workflow tests**
- [x] **Step 2: Run backend candidate tests**
