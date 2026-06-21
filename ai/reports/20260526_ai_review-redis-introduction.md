---
type: report
category: inference
status: active
updated: 2026-06-18
description: "AI 워크플로우에 Redis 레이어를 성공적으로 도입한 결과 보고서"

---

# AI Review Redis Introduction

> Date: 2026-05-26  
> Scope: P2 Redis introduction for AI inference orchestration

## What Changed

The Python AI service can now use Redis as a shared answer cache while keeping the existing in-process and JSONL fallback behavior.

Redis is optional and best-effort. If Redis is unavailable, answer generation and local cache fallback continue.

## Answer Cache Configuration

```text
AI_REVIEW_ANSWER_CACHE_BACKEND=redis
AI_REVIEW_REDIS_URL=redis://localhost:6379/0
AI_REVIEW_ANSWER_CACHE_TTL_SECONDS=86400
AI_REVIEW_ANSWER_CACHE_REDIS_PREFIX=ai_review:answer
```

Set `AI_REVIEW_ANSWER_CACHE_BACKEND=memory` or leave it unset to keep local-only behavior.

## Key Shape

Redis stores answers as:

```text
ai_review:answer:<cache_namespace_version>|<mode>|<model>|<normalized request fields>
```

The `cache_namespace_version` is already included in `cache_key_for()`. When the immutable vector index changes, the cache namespace changes and old Redis answers are no longer read by new requests.

## Runtime Behavior

Read path:

1. memory cache
2. Redis cache when enabled
3. JSONL persistent cache when configured

Write path:

1. memory cache
2. JSONL persistent cache when configured
3. Redis cache with TTL when enabled

Redis errors are swallowed intentionally so cache outages do not block answer generation.

## Admission State Review

Current admission control is process-local:

```text
AI_REVIEW_MAX_IN_FLIGHT_REQUESTS
```

Redis-backed admission is a good P3/P2.5 candidate for multi-replica serving, but it needs atomic acquire/release semantics and stale recovery.

Suggested future key:

```text
ai_review:admission:<route_or_model>
```

Required semantics:

- atomic increment with TTL on acquire
- decrement on release
- TTL-based stale recovery after process crash
- metrics for acquired, rejected, released, and stale-recovered

## Distributed Single-Flight Pre-Design

Current single-flight is process-local and safe for one Python process. Redis single-flight requires a lock and a result handoff strategy.

Suggested future keys:

```text
ai_review:singleflight:lock:<cache_key_hash>
ai_review:singleflight:result:<cache_key_hash>
```

P3 should decide whether streaming join is supported. Until that is decided, shared answer cache gives cross-process cache hits without introducing stale lock or partial stream handoff risk.

## Verification

```powershell
C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m unittest tests.test_answer_cache
```
