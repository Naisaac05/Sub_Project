---
type: report
category: inference
status: active
updated: 2026-06-18
description: "Ollama 기반 AI 리뷰 인퍼런스 게이트웨이 및 모델 풀 구성 보고서"

---

# AI Review Inference Gateway + Model Pool

> Date: 2026-05-27  
> Scope: P3 inference gateway and model pool

## What Changed

The Python AI service now routes Ollama generation through a small in-process gateway. Existing workflow APIs remain unchanged:

- `call_ollama()`
- `call_ollama_stream_async()`

The gateway adds model-to-endpoint routing, endpoint draining, and per-model capacity gates.

## Configuration

Single-endpoint behavior remains the default:

```text
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MAX_CONCURRENT_GENERATIONS=1
```

Model pool mode:

```text
OLLAMA_MODEL_POOL=qwen3:1.7b=http://localhost:11434,qwen3:4b-q4_K_M=http://localhost:11435
OLLAMA_MODEL_CAPACITY=qwen3:1.7b=1,qwen3:4b-q4_K_M=1
OLLAMA_DRAINING_ENDPOINTS=http://localhost:11435
```

`OLLAMA_MODEL_POOL` accepts repeated model entries, so one model can have multiple endpoints:

```text
OLLAMA_MODEL_POOL=qwen3:1.7b=http://ollama-a:11434,qwen3:1.7b=http://ollama-b:11434
```

## Routing Policy

Routing is deterministic and model-first:

1. select endpoints registered for the requested model
2. skip endpoints listed in `OLLAMA_DRAINING_ENDPOINTS`
3. route to the first active endpoint
4. if every endpoint is draining, route to the first endpoint and mark the route as all-draining in gateway state

Unknown models use `OLLAMA_BASE_URL`.

## Capacity Policy

Capacity is tracked per model. Each model receives its own bounded semaphore. This means a blocked fallback model does not consume the small model's queue, and vice versa.

Queue wait timeout remains:

```text
OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS=3
```

## Metrics

Streaming completion emits `ai_review.ollama_stream_finished` with:

- `model`
- `endpoint`
- `capacity`
- `in_flight`
- `queue_wait_ms`
- `elapsed_ms`
- `status`
- `semaphore_released`

Sync generation emits `ai_review.ollama_generation_finished` with:

- `model`
- `endpoint`
- `capacity`
- `in_flight`
- `queue_wait_ms`
- `elapsed_ms`
- `status`

## Health and Draining

Current implementation supports explicit draining through `OLLAMA_DRAINING_ENDPOINTS`.

Future active health probing should call one of:

- `GET /api/version`
- `GET /api/tags`

Routing should then exclude unhealthy endpoints before falling back to deterministic order.

## Verification

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe -m unittest tests.test_ollama_gateway tests.test_ollama_client
```
