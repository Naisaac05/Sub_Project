---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "Ollama 인퍼런스 게이트웨이 로드밸런싱 및 모델 풀 시스템 설계서"

---

# AI Inference Gateway + Model Pool Design

## Goal

Remove the hard dependency on a single Ollama endpoint by adding a minimal Python-side inference gateway and model pool.

## Scope

This closes the P3 item:

- remove Ollama single endpoint bottleneck
- add model-level queue/capacity metrics
- define health check, routing, and draining policy

## Approach

Add `ai/app/ollama/gateway.py` behind the existing `call_ollama()` and `call_ollama_stream_async()` APIs. The workflow keeps calling the same public functions, while the gateway decides which endpoint and per-model capacity gate to use.

Configuration:

```text
OLLAMA_MODEL_POOL=qwen3:1.7b=http://localhost:11434,qwen3:4b-q4_K_M=http://localhost:11435
OLLAMA_MODEL_CAPACITY=qwen3:1.7b=1,qwen3:4b-q4_K_M=1
OLLAMA_DRAINING_ENDPOINTS=http://localhost:11435
```

If `OLLAMA_MODEL_POOL` is blank, the gateway falls back to existing `OLLAMA_BASE_URL`.

## Routing

Routing is model-first:

1. find endpoints registered for the requested model
2. skip endpoints listed in `OLLAMA_DRAINING_ENDPOINTS`
3. choose the first active endpoint
4. if every endpoint is draining, return the first endpoint and mark status so metrics can expose that all routes are draining

This is intentionally deterministic for the first implementation. Weighted routing and adaptive health scoring can come later.

## Capacity

Capacity is per model, not global. The gateway owns one bounded semaphore per model. Queue wait timeout remains `OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS`.

## Metrics

Each generation completion emits:

- `event=ai_review.ollama_stream_finished`
- `model`
- `endpoint`
- `capacity`
- `in_flight`
- `queue_wait_ms`
- `status`
- `semaphore_released`

Sync calls use the same model/endpoint/capacity routing and emit `ai_review.ollama_generation_finished`.

## Health and Draining

The first implementation defines health behavior without background probing:

- draining endpoints are skipped for new routing
- future health probes should call `/api/version` or `/api/tags`
- future active health state should be part of gateway routing state

This gives operators a deterministic draining control now without adding a scheduler.

## Testing

Use unit tests without real Ollama:

- model pool parser maps model names to endpoints
- draining endpoints are skipped
- unknown model falls back to base URL
- per-model capacity gates are independent
- streaming metrics include model, endpoint, capacity, and in-flight count
