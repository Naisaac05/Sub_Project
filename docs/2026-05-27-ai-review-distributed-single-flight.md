# AI Review Distributed Single-Flight

> Date: 2026-05-27
> Scope: P3 distributed duplicate-generation control

## Summary

Python workflow generation now has an optional Redis-backed distributed single-flight layer.

The existing in-process `run_single_flight()` remains the first guard. When Redis distributed single-flight is enabled, the local leader also coordinates with other Python workers through a Redis request-key lock.

## Enablement

Distributed single-flight is disabled by default.

Enable it with:

```powershell
$env:AI_REVIEW_ANSWER_CACHE_BACKEND = "redis"
$env:AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT = "true"
```

Redis URL and answer cache prefix continue to use the existing settings:

```powershell
$env:AI_REVIEW_REDIS_URL = "redis://localhost:6379/0"
$env:AI_REVIEW_ANSWER_CACHE_REDIS_PREFIX = "ai_review:answer"
```

## Settings

- `AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_LOCK_TTL_SECONDS`: lock TTL, default `60`.
- `AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_WAIT_TIMEOUT_SECONDS`: follower wait timeout, default `30`.
- `AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_POLL_INTERVAL_MS`: Redis answer cache poll interval, default `100`.

Invalid or non-positive values fall back to defaults.

## Behavior

1. A process-local leader tries to acquire Redis lock `singleflight:<request-key>`.
2. If acquired, it generates the answer, writes the normal answer cache, then releases the lock.
3. If not acquired, it waits for the answer cache to receive the remote result.
4. If the cached answer appears, the workflow returns through the normal cache-hit path.
5. If the wait times out, the workflow treats the lock as stale/slow and runs local generation.

Lock release is owner-token guarded so a worker does not delete another worker's newer lock.

## Failure Policy

Redis lock failures do not fail answer generation. The system falls back to the existing local single-flight behavior.

## Streaming Join

Streaming join is not implemented in this step. Streaming responses still run per active request. A real streaming join would need chunk fan-out or a durable per-request stream buffer, which should be designed separately before implementation.

## Verification

Run from `ai/`:

```powershell
.\.venv\Scripts\python.exe -m unittest tests.test_answer_cache tests.test_workflow_runner
```

Expected result:

```text
Ran 47 tests
OK
```
