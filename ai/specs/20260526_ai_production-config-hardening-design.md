---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "AI 운영 시스템 환경 설정 보호 및 강화(Hardening) 명세서"

---

# AI Production Config Hardening Design

## Goal

Production AI Review services must fail fast when safety-critical configuration is missing, unbounded, or pointed at local JSONL runtime state.

## Scope

This hardening covers the P2 checklist:

- prod requires a service token
- prod requires bounded timeouts
- prod forbids local JSONL as source of truth
- prod forbids unbounded stream/input/generation limits

## Approach

Use startup fail-fast validation in both runtimes.

Spring Boot validates `app.ai-review` settings when `prod` profile is active. The validator rejects missing Python service tokens, non-positive stream and upstream read timeouts, non-positive generation/input limits, and JSONL candidate paths under `app.ai-review.*candidates-path`.

FastAPI validates environment on startup when `ENVIRONMENT`, `APP_ENV`, or `PYTHON_ENV` equals `prod` or `production`. It rejects missing `AI_REVIEW_SERVICE_TOKEN`, non-positive Ollama/candidate timeout values, `AI_REVIEW_CANDIDATE_SINK=jsonl`, and non-positive generation limits.

## Components

- `backend/src/main/java/com/devmatch/config/AiReviewProductionConfigValidator.java`
  - Spring `ApplicationRunner`
  - no-op outside prod profile
  - throws `IllegalStateException` with clear messages on violations
- `backend/src/test/java/com/devmatch/config/AiReviewProductionConfigValidatorTest.java`
  - unit tests for prod pass/fail cases
- `ai/app/production_config.py`
  - pure functions for prod detection and validation
  - `validate_production_config()` raises `RuntimeError`
- `ai/app/main.py`
  - calls `validate_production_config()` during startup before Ollama warmup
- `ai/tests/test_production_config.py`
  - tests Python prod pass/fail cases

## Validation Rules

Spring prod profile rejects:

- `app.ai-review.python.service-token` blank
- `app.ai-review.stream-timeout-seconds <= 0`
- `app.ai-review.python.read-timeout-seconds <= 0`
- `app.ai-review.ollama.read-timeout-seconds <= 0`
- `app.ai-review.python.max-tokens <= 0`
- `app.ai-review.python.num-ctx <= 0`
- `app.ai-review.ollama.max-tokens <= 0`
- `app.ai-review.ollama.num-ctx <= 0`
- `app.ai-review.limits.max-user-answer-length <= 0`
- candidate source paths ending in `.jsonl`

Python prod environment rejects:

- `AI_REVIEW_SERVICE_TOKEN` blank
- `OLLAMA_REQUEST_TIMEOUT_SECONDS <= 0`
- `OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS <= 0`
- `AI_REVIEW_CANDIDATE_CAPTURE_TIMEOUT_SECONDS <= 0`
- `AI_REVIEW_CANDIDATE_SINK=jsonl`
- `PYTHON_AI_MAX_TOKENS <= 0`
- `PYTHON_AI_NUM_CTX <= 0`
- `AI_REVIEW_MAX_USER_ANSWER_LENGTH <= 0`

## Error Handling

Validation errors are collected and reported together in one exception message. This gives operators a single actionable list instead of one failure per restart.

## Testing

Use TDD:

1. Spring test verifies prod rejects missing token and JSONL candidate paths.
2. Spring test verifies prod accepts bounded tokenized config.
3. Python test verifies production env rejects missing token, unbounded timeout, and JSONL sink.
4. Python test verifies non-prod remains permissive for local development.

## Notes

This does not remove dev/local JSONL scripts. It only prevents runtime production services from starting with local JSONL source-of-truth settings.
