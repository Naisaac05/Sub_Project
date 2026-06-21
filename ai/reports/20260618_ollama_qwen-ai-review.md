---
type: report
category: ollama
status: active
updated: 2026-06-18
description: "Ollama 환경 내 Qwen 모델 기반 AI 리뷰 연동 셋업 가이드 및 리포트"

---

# Ollama Qwen AI Review Setup

## Purpose

The smart review flow can now use a local Ollama model before falling back to the rule-based MVP.

Default model:

```text
qwen3:4b-q4_K_M
```

This keeps the project usable without paid API keys. If Ollama is not running, DevMatch continues with the existing rule-based review.

## Local Setup

Install Ollama, then pull the model:

```powershell
ollama pull qwen3:4b-q4_K_M
```

Keep Ollama running:

```powershell
ollama serve
```

In another terminal, you can test the model:

```powershell
ollama run qwen3:4b-q4_K_M
```

## Backend Environment

For local development, use Ollama explicitly:

```powershell
$env:AI_REVIEW_PROVIDER="OLLAMA"
$env:OLLAMA_ENABLED="true"
$env:OLLAMA_MODEL="qwen3:4b-q4_K_M"
$env:OLLAMA_BASE_URL="http://localhost:11434"
```

Then restart the Spring Boot backend.

## Provider Behavior

```text
AI_REVIEW_PROVIDER=OLLAMA -> use Ollama if configured
AI_REVIEW_PROVIDER=AUTO   -> OpenAI if key exists, otherwise Ollama, otherwise rule-based
AI_REVIEW_PROVIDER=RULE_BASED -> no AI call
```

If Ollama is slow, missing, or returns an empty response, the backend catches the error and uses the rule-based follow-up question.

## Current Scope

Ollama is used for:

- the first follow-up question for a wrong answer
- feedback on the learner's written answer
- the next concrete follow-up question
- free-form learner questions about the current wrong answer

The final evaluation label is still rule-based:

```text
UNDERSTOOD
PARTIAL
NEEDS_REVIEW
```

That keeps the MVP predictable while making the conversation more natural.

## Review Modes

The review UI separates three user intents:

```text
CHECK_ANSWER  -> learner answers the AI's check question
FREE_QUESTION -> learner asks anything they are curious about
NEXT_QUESTION -> learner skips to the next wrong answer
```

This prevents the learner from feeling forced to answer every follow-up question perfectly. The AI acts as a review tutor, not as a strict re-grading system.

Stored message modes:

```text
CHECK_QUESTION
CHECK_ANSWER
FREE_QUESTION
FREE_ANSWER
EXPLANATION
NEXT_QUESTION
SYSTEM_SUMMARY
```
