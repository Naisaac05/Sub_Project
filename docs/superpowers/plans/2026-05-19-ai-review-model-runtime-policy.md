# AI Review Model Runtime Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the first code slice with final.md model/runtime policy: small model by default, 4B fallback, larger RAG token budget, bounded Ollama residency, and one concurrent generation by default.

**Architecture:** Keep this as a narrow runtime policy change. Update Python request defaults and Ollama client policy, update Spring Boot defaults, and preserve existing AI generated-answer metadata while repairing compile blockers exposed by backend verification.

**Tech Stack:** Python unittest, FastAPI schema defaults, Ollama local generation, Spring Boot configuration properties, JUnit.

---

### Task 1: Python Defaults and Ollama Runtime

- [x] **Step 1: Write failing schema defaults tests**
- [x] **Step 2: Write failing Ollama runtime policy tests**
- [x] **Step 3: Update Python request defaults to `qwen3:1.7b`, `max_tokens=256`, `num_ctx=1024`**
- [x] **Step 4: Update Ollama default/warmup model, keep_alive policy, and generation semaphore**
- [x] **Step 5: Verify Python focused tests pass**

### Task 2: Spring Boot Defaults

- [x] **Step 1: Write config defaults test**
- [x] **Step 2: Update `AiReviewProperties` defaults**
- [x] **Step 3: Update `application.yml` env fallback defaults**
- [x] **Step 4: Verify backend focused tests pass**

### Task 3: Compile Blocker Fixes

- [x] **Step 1: Investigate compile failure root cause**
- [x] **Step 2: Preserve `AiGeneratedAnswer` metadata when saving free-question AI messages**
- [x] **Step 3: Restore `StudySummary` helper and stable labels**
- [x] **Step 4: Record root-cause fix in `error/`**
