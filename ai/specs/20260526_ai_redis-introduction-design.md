---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "AI 파이프라인 내 Redis 캐시 및 큐 인프라 도입 설계서"

---

# AI Redis Introduction Design

## Goal

Introduce Redis as the first shared state backend for AI Review inference orchestration without making Redis availability part of the answer path.

## Scope

This closes the P2 Redis item:

- shared answer cache candidate design
- rate limit / admission state storage review
- distributed single-flight pre-design

## Recommended Implementation

Implement Redis-backed answer cache in the Python AI service first.

`ai/app/workflow/answer_cache.py` remains the public cache API used by workflow nodes. A new Redis adapter handles optional Redis reads/writes behind `get_cached_answer()` and `put_cached_answer()`.

Configuration:

- `AI_REVIEW_ANSWER_CACHE_BACKEND=memory|redis`
- `AI_REVIEW_REDIS_URL=redis://localhost:6379/0`
- `AI_REVIEW_ANSWER_CACHE_TTL_SECONDS=86400`
- `AI_REVIEW_ANSWER_CACHE_REDIS_PREFIX=ai_review:answer`

The existing cache key already includes `cache_namespace_version`, so vector index changes invalidate Redis answer cache by namespace.

## Runtime Behavior

Reads:

1. try memory cache
2. if Redis backend is enabled, try Redis
3. if Redis hit, hydrate memory cache and return
4. if Redis fails, continue to JSONL/memory fallback

Writes:

1. write memory cache
2. append JSONL persistent cache if configured
3. if Redis backend is enabled, write Redis with TTL
4. if Redis fails, ignore the Redis error so answer generation remains available

## Admission State Review

Current admission is process-local via `AI_REVIEW_MAX_IN_FLIGHT_REQUESTS`. Moving it to Redis would require atomic acquire/release semantics with TTL for crash recovery. That is valuable for multi-replica serving, but it should not be mixed into answer cache rollout.

P2 outcome: document Redis key shape and constraints.

Suggested future key:

```text
ai_review:admission:<route_or_model>
```

Suggested behavior:

- atomic increment with TTL on acquire
- decrement on release
- stale count recovery via TTL
- metrics for acquired/rejected/stale-recovered

## Distributed Single-Flight Pre-Design

Current single-flight is process-local. Redis single-flight needs a request-key lock and a result handoff strategy.

Suggested future keys:

```text
ai_review:singleflight:lock:<cache_key_hash>
ai_review:singleflight:result:<cache_key_hash>
```

P3 should decide whether streaming join is supported. Until then, P2 only introduces the shared answer cache. This avoids stale lock and partial stream complexity in the cache rollout.

## Testing

Use fake Redis clients in unit tests:

- Redis hit returns cached answer and hydrates memory cache
- Redis write uses TTL
- Redis failure falls back to memory/JSONL without raising
- `AI_REVIEW_ANSWER_CACHE_BACKEND=memory` keeps existing behavior
