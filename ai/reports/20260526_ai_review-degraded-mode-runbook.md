---
type: report
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 장애 발생 시 성능 저하 모드(Degraded Mode) 런북"

---

# AI Review Degraded Mode Runbook

> 작성일: 2026-05-26
> 범위: P1 initial kill switches

## streaming_off

- Env: `AI_REVIEW_STREAMING_OFF=true`
- Layer: Spring
- Effect: `/messages/stream` immediately falls back to the synchronous answer path.
- Metric: `ai.review.fallback.sync{reason="streaming_off"}`
- Use when: SSE path, client disconnect handling, or upstream stream proxy is unstable.

## cache_only

- Env: `AI_REVIEW_CACHE_ONLY=true`
- Layer: Python workflow
- Effect:
  - cache hit: returns cached answer with `route=cache`
  - cache miss: skips Ollama generation and returns template fallback with `route=cache_only_miss`
- Use when: Ollama capacity is exhausted but cached answers should remain available.

## template_fallback_only

- Env: `AI_REVIEW_TEMPLATE_FALLBACK_ONLY=true`
- Layer: Python workflow
- Effect: bypasses cache and generation, returns template fallback with `route=template_fallback_only`.
- Use when: LLM/RAG answer path is suspected unsafe and deterministic fallback is preferred.

## lightweight_only

- Env: `AI_REVIEW_LIGHTWEIGHT_ONLY=true`
- Layer: Python workflow
- Effect:
  - lightweight hit: returns static/generated-card answer with routes such as `static_fast_path` or `generated_card_fast_path`
  - lightweight miss: skips cache and Ollama generation, returns template fallback with `route=lightweight_only_miss`
- Metric/log fields: `llm_call_avoided=true`, `quality_flags=["lightweight_only_miss"]` on misses.
- Use when: CPU/RAM pressure is high and only bounded local template/card answers should remain available.

## no_candidate_capture

- Env: `AI_REVIEW_NO_CANDIDATE_CAPTURE=true`
- Layer: Python workflow
- Effect: skips auto-candidate JSONL writes while preserving normal answer generation, cache, and fallback behavior.
- Metric/log fields: `candidate_capture_disabled=true`; candidate write exceptions are isolated and logged as `candidate_capture_failed=true`.
- Use when: candidate queue writes are failing, contaminated, or backlogged but learner answers should continue.

## Verification

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe -m pytest tests\test_workflow_degraded_modes.py tests\test_workflow_runner.py tests\test_observability.py -q

cd C:\Users\User\Desktop\Sub_Project\backend
.\gradlew.bat test --tests com.devmatch.config.AiReviewPropertiesTest --tests com.devmatch.service.ai.AiReviewStreamingServiceTest
```

## Remaining Kill Switches

None.
