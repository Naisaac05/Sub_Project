---
type: report
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 트래픽 대응을 위한 부하 테스트 프로파일 및 수행 결과"

---

# AI Review Load Test Profile

> 작성일: 2026-05-26
> 범위: P1 load test environment

## 실행 전제

- Backend: `http://localhost:8080`
- Spring streaming: `AI_REVIEW_STREAMING_ENABLED=true`
- Python AI: `PYTHON_AI_ENABLED=true`
- Python/FastAPI server and Ollama are running when testing real generation.
- Use a valid auth token if the endpoint is protected: `--token ...`

## Runner

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe scripts\run_stream_load_profile.py --scenario cache-hit --session-id 20 --requests 5 --concurrency 1
```

Reports are written to `docs/smoke/YYYY-MM-DD-ai-stream-load-profile.md` by default.

## Scenarios

### cache-hit

- Goal: repeated prompt path for answer-cache hit behavior.
- Default: 5 requests, concurrency 1.
- Watch: `ai.review.stream.first_token.latency`, `ai.review.stream.duration`, `ai.review.fallback.sync`.

### cache-miss

- Goal: unique prompt path for retrieval/generation misses.
- Default: 5 requests, concurrency 1.
- Watch: first-token p95 and partial failures.

### free-form-storm

- Goal: concurrent unique free-form questions on dev laptop.
- Default: 9 requests, concurrency 3.
- Watch: stream lifecycle status mix, queue wait side effects, timeout growth.

### stream-disconnect

- Goal: disconnect after first chunk to verify cancellation load behavior.
- Default: 6 requests, concurrency 2.
- Watch: `status=disconnected`, Spring cancellation logs, Python `ai_review.ollama_stream_finished`.

### ollama-timeout

- Goal: exercise timeout/hang path with short client timeout.
- Default: 3 requests, concurrency 1, client timeout 1s.
- Watch: `status=timeout`, semaphore release metric, fallback/degraded behavior.

## Suggested Local Sequence

1. `cache-hit`
2. `cache-miss`
3. `stream-disconnect`
4. `ollama-timeout`
5. `free-form-storm`

Run `free-form-storm` last so the dev laptop baseline is not polluted by thermal throttling before the simpler checks.
