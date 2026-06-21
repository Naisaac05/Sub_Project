---
type: report
category: inference
status: active
updated: 2026-06-18
description: "AI 프로덕션 환경 설정 강화(Hardening) 및 보안 적용 보고서"

---

# AI Review Production Config Hardening

> Date: 2026-05-26  
> Scope: P2 production configuration safety guard

## Goal

Production AI Review services must not start with local-only, unbounded, or unauthenticated inference settings.

## Spring Boot validation

`AiReviewProductionConfigValidator` runs at startup when an active profile is `prod` or `production`.

It rejects:

- blank `app.ai-review.python.service-token`
- missing or non-positive `app.ai-review.stream-timeout-seconds`
- non-positive `app.ai-review.python.read-timeout-seconds`
- non-positive `app.ai-review.ollama.read-timeout-seconds`
- non-positive Python/Ollama `max-tokens` or `num-ctx`
- non-positive `app.ai-review.limits.max-user-answer-length`
- `app.ai-review.candidates-path` ending in `.jsonl`
- `app.ai-review.auto-candidates-path` ending in `.jsonl`

Example production values:

```yaml
spring:
  profiles:
    active: prod

app:
  ai-review:
    stream-timeout-seconds: 45
    candidates-path: ""
    auto-candidates-path: ""
    python:
      service-token: ${AI_REVIEW_SERVICE_TOKEN}
      read-timeout-seconds: 30
      max-tokens: 256
      num-ctx: 1024
    ollama:
      read-timeout-seconds: 30
      max-tokens: 256
      num-ctx: 1024
    limits:
      max-user-answer-length: 700
```

## Python FastAPI validation

`validate_production_config()` runs during FastAPI startup before Ollama warmup when `ENVIRONMENT`, `APP_ENV`, or `PYTHON_ENV` is `prod` or `production`.

It rejects:

- blank `AI_REVIEW_SERVICE_TOKEN`
- `AI_REVIEW_CANDIDATE_SINK=jsonl`
- missing/non-positive `OLLAMA_REQUEST_TIMEOUT_SECONDS`
- missing/non-positive `OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS`
- missing/non-positive `AI_REVIEW_CANDIDATE_CAPTURE_TIMEOUT_SECONDS`
- missing/non-positive `PYTHON_AI_MAX_TOKENS`
- missing/non-positive `PYTHON_AI_NUM_CTX`
- missing/non-positive `AI_REVIEW_MAX_USER_ANSWER_LENGTH`

Example production env:

```text
ENVIRONMENT=prod
AI_REVIEW_SERVICE_TOKEN=<shared-token>
AI_REVIEW_CANDIDATE_SINK=http
OLLAMA_REQUEST_TIMEOUT_SECONDS=30
OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS=3
AI_REVIEW_CANDIDATE_CAPTURE_TIMEOUT_SECONDS=2
PYTHON_AI_MAX_TOKENS=256
PYTHON_AI_NUM_CTX=1024
AI_REVIEW_MAX_USER_ANSWER_LENGTH=700
```

## Verification

Focused checks:

```powershell
.\gradlew.bat test --tests com.devmatch.config.AiReviewProductionConfigValidatorTest --tests com.devmatch.config.AiReviewPropertiesTest
C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m unittest tests.test_production_config tests.test_ollama_client
```
