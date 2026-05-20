# AI Review Security Guardrails Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Secure Spring Boot to FastAPI AI review calls and sanitize AI prompt inputs.

**Architecture:** Add Python guardrail helpers used by request normalization and FastAPI token checks. Add Spring config/client support for forwarding `X-AI-Service-Token`.

**Tech Stack:** Python unittest, FastAPI `HTTPException`, Spring Boot configuration properties, RestClient headers.

---

### Task 1: Python Guardrails

- [x] **Step 1: Add failing tests for PII masking, prompt-injection neutralization, and input length**
- [x] **Step 2: Implement `app.guardrails` and wire schemas/main**
- [x] **Step 3: Run Python helper tests**

### Task 2: Spring Service Token

- [x] **Step 1: Add service token property**
- [x] **Step 2: Send `X-AI-Service-Token` from Python client**
- [x] **Step 3: Run backend focused tests**

### Task 3: Regression Verification

- [x] **Step 1: Run Python helper/workflow tests**
- [x] **Step 2: Run backend focused tests**
