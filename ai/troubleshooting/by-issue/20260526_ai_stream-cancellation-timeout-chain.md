---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI stream cancellation and timeout chain hardening 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI stream cancellation and timeout chain hardening

- 발생 일시: 2026-05-26
- 영역: backend / ai
- 심각도: high

## 증상
스트리밍 요청에서 클라이언트가 연결을 끊거나 `SseEmitter` timeout이 발생했을 때 Spring 구독이 실제 upstream까지 dispose되는지 회귀 테스트 근거가 부족했다. 또한 Python의 Ollama 호출 기본 timeout이 `0`으로 해석되어 무제한 대기가 가능했다.

## 원인
`AiReviewStreamingService`는 production에서는 `SseEmitter` lifecycle callback으로 cleanup을 수행하지만, 기존 단위 테스트는 servlet container 없이 raw `SseEmitter.complete()`만 호출해 callback을 결정적으로 재현하기 어려웠다. 그 결과 disconnect/timeout 이후 late event가 terminal state를 오염시키지 않는지 확인하기 어려웠다.

Python 쪽은 `ai/app/ollama/client.py:13`의 `OLLAMA_REQUEST_TIMEOUT_SECONDS` 기본값이 `0`이어서 `call_ollama()`와 `call_ollama_stream_async()`가 timeout을 `None`으로 설정할 수 있었다. Spring stream timeout은 45초인데 Ollama 호출은 무제한이면, 운영 중 local Ollama가 멈췄을 때 Spring 요청은 닫혀도 Python/Ollama 작업이 오래 남을 수 있다.

## 해결 방법
`backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:114`와 `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java:408`에서 `SseEmitter` 생성을 `createEmitter(timeoutMs)`로 분리해 테스트에서 lifecycle callback을 직접 트리거할 수 있게 했다.

`backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java:493`에 disconnect 후 upstream dispose 및 late done 무시 테스트를 추가했고, `backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java:536`에 timeout 후 upstream dispose 및 `STATUS:DISCONNECTED` 저장 테스트를 추가했다. `backend/src/test/java/com/devmatch/service/ai/AiReviewStreamingServiceTest.java:581`에는 duplicate terminal event가 단일 AI terminal message만 저장하는지 검증하는 회귀 테스트를 추가했다.

`ai/app/ollama/client.py:13`의 기본 `OLLAMA_REQUEST_TIMEOUT_SECONDS`를 `30`으로 변경해 Spring stream timeout 45초보다 안쪽에서 Python/Ollama 호출이 먼저 bounded timeout을 갖도록 맞췄다. `ai/tests/test_ollama_client.py:37`에 기본 timeout 정책 테스트를 추가했다.

## 재발 방지 / 메모
현재 보강은 Spring의 Reactor subscription dispose와 Python/Ollama timeout 기본값에 대한 최소 P0 방어다. 실제 네트워크 연결 단절이 FastAPI task cancellation과 httpx stream close까지 전파되는지는 통합 테스트 또는 local Ollama를 붙인 e2e 테스트가 추가로 필요하다.

향후 request-id 또는 stream-id가 DB에 저장되지 않는 한, 서로 다른 free question 요청 간 DB-level terminal uniqueness를 완전히 보장하기 어렵다. 현재는 request-scoped state machine 회귀 테스트로 late event 오염을 막는 수준이다.
 
2026-05-26 follow-up: Added an Ollama stream completion metric that records semaphore release even when the stream is cancelled or times out. The metric is emitted as `ai_review.ollama_stream_finished` with `status`, `queue_wait_ms`, `elapsed_ms`, and `semaphore_released`: `ai/app/ollama/client.py:180`, `ai/app/ollama/client.py:195`. Regression coverage verifies cancellation and timeout release metrics: `ai/tests/test_ollama_client.py:99`, `ai/tests/test_ollama_client.py:155`.

2026-05-26 follow-up: Closed the remaining timeout-chain gap. `OLLAMA_REQUEST_TIMEOUT_SECONDS=0` and negative values are now normalized to a bounded 30s timeout, and Ollama semaphore queue wait is bounded by `OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS=3`: `ai/app/ollama/client.py:16`, `ai/app/ollama/client.py:30`, `ai/app/ollama/client.py:34`, `ai/app/ollama/client.py:165`. Queue wait timeout emits `status=queue_timeout` without releasing an unacquired semaphore: `ai/app/ollama/client.py:169`, `ai/app/ollama/client.py:209`. Regression coverage: `ai/tests/test_ollama_client.py:47`, `ai/tests/test_ollama_client.py:52`, `ai/tests/test_ollama_client.py:217`.
