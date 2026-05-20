# AI Review Security Guardrails Design

## Goal

Add the first security and guardrail layer for Spring Boot to FastAPI AI review calls: service token authentication, prompt-injection neutralization, input length policy, and broader PII masking.

## Architecture

FastAPI enforces `X-AI-Service-Token` only when `AI_REVIEW_SERVICE_TOKEN` is configured. Spring Boot sends the same header from `app.ai-review.python.service-token`. This keeps local development easy while allowing production hardening.

Input text is sanitized in Python request normalization before prompts, retrieval, or logs see it. Sanitization masks PII, neutralizes common prompt-injection phrases, and truncates long fields to `AI_REVIEW_MAX_INPUT_LENGTH` (default 700).

## Scope

This phase protects the AI review boundary and prompt inputs. It does not add user-facing moderation decisions or a full DLP engine.
