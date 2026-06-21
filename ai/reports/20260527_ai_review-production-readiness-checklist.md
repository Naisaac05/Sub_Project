---
type: report
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 시스템 릴리즈를 위한 프로덕션 준비 체크리스트 완료 보고"

---

# AI Review Production Readiness Checklist

> Date: 2026-05-27  
> Scope: P2 closeout checklist for AI inference orchestration

## Status

This checklist is an internal production-readiness gate for the local LLM AI Review inference orchestration system. It does not declare the system externally production-ready. External SLA language should wait until production metrics export, alerting, on-call ownership, and real production load evidence exist.

## SLO / SLA

Initial SLOs are internal targets. They should be measured from Micrometer metrics, Python structured observability events, and load-test reports.

| Area | Internal SLO | Measurement | Primary Response |
| --- | --- | --- | --- |
| Streaming completion | `stream_completed` remains dominant over `stream_partial_failed` during normal rollout | `ai.review.stream.lifecycle{status}` | investigate stream race, enable `streaming_off` if user impact continues |
| First token latency | p95 stays below the current accepted baseline for the active model/profile | `ai.review.stream.first_token.latency` | reduce concurrency, enable `cache_only` or `lightweight_only`, review Ollama capacity |
| Stream duration | p95 remains below Spring stream timeout with margin | `ai.review.stream.duration` | tune timeout chain, reduce prompt/token budget, use degraded mode |
| Fallback rate | unexpected fallback-to-sync/degraded spikes are short-lived and explained | `ai.review.fallback.sync`, observability events | check Python/Ollama health, kill switches, candidate capture errors |
| Cache correctness | zero known stale/wrong answer incidents after index changes | incident review, cache namespace/version | rotate `cache_namespace_version`, purge Redis namespace if needed |
| Candidate safety | zero unapproved candidate retrieval incidents | approval audit, vector index manifest | reject/rollback candidate, restore previous immutable index |

SLA is not yet offered. Required before SLA: production metrics exporter, alert routes, incident owner, production load test artifact, and at least one rollback drill.

## Failure Budget

Initial failure budget is conservative because the system still sits between local-first prototype and production-grade serving.

| Failure Class | Budget | Breach Condition | Action |
| --- | --- | --- | --- |
| Partial stream failure | less than 5% of stream windows | sustained `stream_partial_failed` increase for 5 minutes | enable `streaming_off`, inspect Spring/Python stream logs |
| Disconnect spike | less than 10% over baseline | disconnects exceed baseline outside user navigation patterns | inspect client disconnects, FastAPI cancellation, httpx close behavior |
| Degraded mode active time | less than 1 hour per day | kill switch needed longer than 1 hour | open capacity/reliability incident and keep degraded mode documented |
| Cache poisoning | zero tolerated | wrong/stale answer served from cache | disable Redis cache, rotate namespace, quarantine key |
| Candidate poisoning | zero tolerated | unapproved or bad candidate enters retrieval | stop candidate capture, rollback vector index, audit approval trail |
| Production config violation | zero tolerated | prod starts with missing token, unbounded timeout, or JSONL source-of-truth | fail startup, fix config before retry |

## Rollback Policy

Use the smallest rollback that removes user impact while preserving evidence.

| Change Type | Rollback Path | Evidence to Keep |
| --- | --- | --- |
| Streaming rollout | set `AI_REVIEW_STREAMING_OFF=true` in Spring | stream lifecycle counters, request ids, terminal statuses |
| Ollama overload | set `AI_REVIEW_CACHE_ONLY=true`, `AI_REVIEW_LIGHTWEIGHT_ONLY=true`, or `AI_REVIEW_TEMPLATE_FALLBACK_ONLY=true` | first-token latency, queue wait, semaphore release metric |
| Candidate capture issue | set `AI_REVIEW_NO_CANDIDATE_CAPTURE=true` or `AI_REVIEW_CANDIDATE_SINK=off` | failed candidate payload, audit rows, capture failure logs |
| Bad approved candidate | reject/merge correction in approval workflow, then reindex | candidate audit, reviewer/evidence fields |
| Bad vector index | restore previous manifest from `ai/app/vectorstore/manifests/<version>.json` and rebuild/repoint vector store | active manifest, previous manifest, `manifest_hash`, `knowledge_index_version` |
| Redis cache issue | set `AI_REVIEW_ANSWER_CACHE_BACKEND=memory` or rotate `AI_REVIEW_CACHE_NAMESPACE_VERSION` | offending Redis key, namespace, cached answer |
| Unsafe prod config | let startup validation fail and redeploy with bounded values | failed startup message and env/config diff |

## Incident Runbook

### 1. Triage

1. Identify user impact: no answer, slow first token, partial stream, wrong answer, or unsafe candidate.
2. Capture correlation id, stream request id, session id, and model/profile.
3. Check the metrics dashboard draft panels:
   - stream lifecycle
   - first-token latency
   - stream duration
   - fallback-to-sync
4. Decide whether a kill switch is needed before deeper debugging.

### 2. Contain

Use these runbooks first:

- Streaming or SSE instability: `docs/2026-05-26-ai-review-degraded-mode-runbook.md`
- Load or timeout symptoms: `docs/2026-05-26-ai-review-load-test-profile.md`
- Redis answer cache issue: `docs/2026-05-26-ai-review-redis-introduction.md`
- Candidate queue issue: `docs/2026-05-26-ai-review-candidate-durable-queue.md`
- Candidate approval issue: `docs/2026-05-26-ai-review-candidate-approval-workflow.md`
- Vector rollback issue: `docs/2026-05-26-ai-review-immutable-vector-index.md`
- Production startup/config issue: `docs/2026-05-26-ai-review-production-config-hardening.md`

### 3. Diagnose

Follow the evidence chain:

1. Spring stream lifecycle metric and terminal DB state.
2. Python observability events and Ollama stream finished metric.
3. Redis/memory cache key and namespace.
4. Candidate audit and workflow phase.
5. Active vector manifest hash/version.

### 4. Recover

Prefer reversible controls:

1. Enable the narrowest kill switch.
2. Restore cache namespace or immutable vector manifest when correctness is suspected.
3. Re-run focused regression tests for the affected layer.
4. Record root cause in `error/` if a bug or environment issue was actually fixed.

### 5. Post-Incident

For any incident with user-visible impact:

- link metrics/screenshots/log snippets
- list exact env/config changes
- record rollback start/end time
- add or update a regression test
- update this checklist if the runbook missed a step

## Pre-Release Gate

Before promoting AI Review inference changes beyond local/dev:

- [ ] Baseline artifact exists for active model/profile.
- [ ] P0 stream race and cancellation tests pass.
- [ ] P1 metrics dashboard panels are queryable.
- [ ] Load scenarios have a fresh report for the target profile.
- [ ] Required kill switches are documented and verified.
- [ ] Candidate capture is DB/durable by default.
- [ ] Candidate approval excludes unapproved content from retrieval.
- [ ] Vector index has `knowledge_index_version`, `manifest_hash`, and previous manifest snapshot.
- [ ] Production config validation passes with bounded values.
- [ ] Redis cache can be disabled without blocking answer generation.
- [ ] Rollback owner and incident owner are named for the release window.
