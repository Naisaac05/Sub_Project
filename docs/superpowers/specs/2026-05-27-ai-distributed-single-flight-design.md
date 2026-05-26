# AI Distributed Single-Flight Design

## Goal

Reduce duplicate LLM generation across multiple Python worker processes by adding an optional Redis request-key lock around the existing workflow single-flight path.

## Scope

This closes the P3 Distributed Single-Flight item:

- Redis request-key lock
- timeout and stale lock release policy
- streaming join feasibility review

## Approach

Keep the existing in-process `run_single_flight()` API and add a Redis-backed coordination layer only when explicitly enabled with `AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT=true` and `AI_REVIEW_ANSWER_CACHE_BACKEND=redis`.

The lock key uses the same cache namespace as answer cache keys, so immutable index cache namespace changes also isolate distributed lock traffic.

## Data Flow

1. Local process-level single-flight still deduplicates threads inside one process.
2. The local leader tries Redis `SET NX EX` on `singleflight:<request-key>` with a random owner token.
3. If lock acquisition succeeds, it runs the producer and releases the lock with owner-token protection.
4. If lock acquisition fails, the process polls Redis answer cache for a bounded wait.
5. If the answer appears, the existing producer runs and returns a normal cache-hit workflow state.
6. If the answer does not appear before timeout, the process treats the lock as stale/slow and runs the producer locally.

## Timeouts

- `AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_LOCK_TTL_SECONDS`: default 60 seconds.
- `AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_WAIT_TIMEOUT_SECONDS`: default 30 seconds.
- `AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_POLL_INTERVAL_MS`: default 100 milliseconds.

Non-positive or invalid values fall back to defaults.

## Streaming Join

Full streaming join is not implemented in this step. The current design coordinates non-streaming workflow generation and lets followers join by answer cache after completion. Streaming join would require chunk fan-out or a per-request stream buffer and should be handled separately.

## Failure Policy

Redis failures are non-fatal. If lock acquire, wait, or release fails, the workflow falls back to the existing local single-flight behavior.

Owner-token release prevents one worker from deleting another worker's refreshed or newly acquired lock.

## Testing

Use deterministic fake Redis tests:

- follower waits for a remote answer and returns the cached result without duplicate generation
- stale/slow lock wait timeout falls back to local generation
- owner-token release does not delete another owner's lock
