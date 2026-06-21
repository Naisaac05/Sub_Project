---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "AI 프로덕션 환경 설정 강화(Config Hardening) 플랜"

---

# AI Production Config Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fail fast when AI Review production configuration is missing required tokens, bounded timeouts, durable state, or input/generation limits.

**Architecture:** Add a Spring prod-profile startup validator for backend AI Review properties and a Python FastAPI startup validator for AI service environment. Keep validators pure and directly testable so the production profile behavior can be verified without starting external services.

**Tech Stack:** Spring Boot `ApplicationRunner`, JUnit/AssertJ, Python stdlib env parsing, unittest.

---

### Task 1: Spring Production Validator

**Files:**
- Create: `backend/src/main/java/com/devmatch/config/AiReviewProductionConfigValidator.java`
- Create: `backend/src/test/java/com/devmatch/config/AiReviewProductionConfigValidatorTest.java`

- [x] **Step 1: Write the failing test**

Create tests that instantiate the validator with prod active profiles and assert missing service token, JSONL candidate paths, and non-positive limits fail.

- [x] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests com.devmatch.config.AiReviewProductionConfigValidatorTest`
Expected: FAIL because the validator class does not exist.

- [x] **Step 3: Write minimal implementation**

Create `AiReviewProductionConfigValidator` as an `ApplicationRunner` that checks prod profile and throws `IllegalStateException` with all violations.

- [x] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests com.devmatch.config.AiReviewProductionConfigValidatorTest`
Expected: PASS.

### Task 2: Python Production Validator

**Files:**
- Create: `ai/app/production_config.py`
- Modify: `ai/app/main.py`
- Create: `ai/tests/test_production_config.py`

- [x] **Step 1: Write the failing test**

Create tests proving production env rejects missing token, JSONL sink, and non-positive timeouts while local env stays permissive.

- [x] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests.test_production_config`
Expected: FAIL because `app.production_config` does not exist.

- [x] **Step 3: Write minimal implementation**

Create pure validation functions and call `validate_production_config()` from FastAPI startup before Ollama warmup.

- [x] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests.test_production_config`
Expected: PASS.

### Task 3: Docs and TODO

**Files:**
- Create: `docs/2026-05-26-ai-review-production-config-hardening.md`
- Modify: `docs/2026-05-26-ai-inference-orchestration-todolist.md`

- [x] **Step 1: Document required prod settings**

Document Spring and Python prod validation rules plus example safe env values.

- [x] **Step 2: Mark TODO complete**

Check P2 Production Config Hardening and its four child items.

- [x] **Step 3: Run focused verification**

Run:
`.\gradlew.bat test --tests com.devmatch.config.AiReviewProductionConfigValidatorTest --tests com.devmatch.config.AiReviewPropertiesTest`
`python -m unittest tests.test_production_config tests.test_ollama_client`
