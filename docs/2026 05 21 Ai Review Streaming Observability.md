# AI Review Streaming & Observability Master Architecture Plan

작성일: 2026-05-21
버전: Production Version v1.0
상태: Ready for Implementation / Antigravity Execution
대상 시스템: AI Review System (Spring Boot + Python FastAPI + Ollama + React)

---

# 1. 문서 목적

본 문서는 현재 AI Review 시스템에 대해 다음 목표를 달성하기 위한 전체 스트리밍 아키텍처 및 운영 설계를 정의한다.

## 핵심 목표

* First Token Latency 대폭 개선 (< 860ms 목표)
* 실시간 AI 응답 Streaming UX 제공
* 기존 RAG / Guardrail / Workflow 구조 유지
* Graceful disconnect propagation 구현
* Non-blocking reactive streaming 구조 도입
* Backpressure 및 thread starvation 방지
* Partial persistence 지원
* Prometheus / Grafana 기반 observability 강화
* Canary rollout 가능한 feature flag 기반 배포 구조 확립
* 장애 시 즉시 non-streaming fallback 가능

---

# 2. 현재 시스템 구조 요약

현재 시스템은 다음 구조를 가진다.

```text
Frontend (React / Next.js)
  ↓
Spring Boot (RuleBasedAiReviewService)
  ↓
Python FastAPI Server
  ↓
Ollama
  ↓
Qwen3 Local LLM
```

현재 구조의 주요 문제점:

* Non-streaming 구조
* Blocking RestClient 기반 호출
* Long request 동안 Spring thread 점유
* First token latency 체감 큼
* 사용자 체감 로딩 UX 저하
* Streaming 미지원
* Disconnect propagation 없음
* Ollama generation cancel 불가능
* Partial persistence 없음
* 실시간 observability 부족

---

# 3. 최종 목표 아키텍처

```text
React Frontend
  ↓ fetch + ReadableStream
Spring Boot SSE Proxy
  ↓ WebClient (Reactive)
Python FastAPI StreamingResponse
  ↓ async chunk stream
Ollama Streaming API
  ↓ token generation
Qwen3
```

핵심 원칙:

* 기존 non-streaming 경로 유지
* Streaming은 별도 orchestration layer로 추가
* 기존 workflow / RAG / guardrail 우회 금지
* Feature Flag 기반 점진 rollout
* Abort / disconnect 전파 보장
* Backpressure 및 cleanup 필수
* Streaming 실패 시 graceful fallback

---

# 4. Feature Flag 전략

## 목적

Streaming 기능은 rollout risk가 높으므로 반드시 feature flag 기반으로 배포한다.

## application.yml

```yaml
app:
  ai-review:
    streaming-enabled: false
    stream-timeout-seconds: 45
```

## Rollout 전략

### Phase 1 — Internal Test

* 기본값 false
* 개발자 및 QA만 강제 활성화
* Latency / disconnect / fallback 검증

### Phase 2 — Canary

* 10% 사용자 대상 활성화
* p95 latency 및 error rate 측정
* disconnect / fallback metrics 확인

### Phase 3 — Full Rollout

* 안정성 검증 후 전체 활성화

## Rollback 전략

```yaml
streaming-enabled: false
```

설정만 변경하면 즉시 기존 non-streaming 경로로 복귀.

---

# 5. Streaming Endpoint 전략

## 목표

* 신규 endpoint 폭증 방지
* 기존 API 계약 유지
* Streaming / Non-streaming 공존

## Python Endpoint 전략

기존 endpoint 재사용:

```text
/api/review/follow-up
/api/review/free-question
```

Streaming 여부는 payload 기반으로 명시적으로 제어한다.

예시:

```json
{
  "answer": "...",
  "mode": "FREE_QUESTION",
  "stream": true,
  "streamOptions": {
    "batchMs": 150,
    "maxBufferChunks": 50
  }
}
```

`Accept: text/event-stream` 헤더는 보조적 signal로만 사용한다.

이유:

* Spring content negotiation 단순화
* endpoint ambiguity 감소
* frontend 제어 명확화
* future stream options 확장 가능

## 장점

* API surface explosion 방지
* 기존 client compatibility 유지
* Routing 단순화
* Swagger 계약 관리 용이

---

# 6. Zero-Bypass Workflow Streaming 원칙

## 절대 금지 사항

Streaming 구현 과정에서 기존 workflow를 우회하면 안 된다.

금지 예시:

```python
prompt = build_prompt(...)
call_ollama_stream(...)
```

이 경우 다음 기능이 우회될 위험이 있다.

* RAG retrieval
* confidence gate
* validation
* fallback
* candidate capture
* observability
* sanitization
* guardrails

## 올바른 구조

```python
async for event in run_review_workflow_stream(...):
    yield format_sse(event)
```

즉:

* retrieve_context
* rule evaluation
* prompt assembly
* validation
* fallback decision
* observability

는 기존 workflow 그대로 유지하고,

실제 token generation 단계만 streaming으로 전환한다.

---

# 7. Python Streaming Architecture

## 주요 파일

```text
ai/app/ollama/client.py
ai/app/workflow/runner.py
ai/app/main.py
```

---

## 7.1 call_ollama_stream_async

### 목표

* Non-blocking async chunk streaming
* httpx.AsyncClient 사용
* Ollama stream API relay

## 핵심 요구사항

* Async generator 사용
* Chunk yield
* Disconnect 즉시 중단
* Exception propagation
* Semaphore integration

## 예시 구조

```python
async def call_ollama_stream_async(...):
    async with httpx.AsyncClient(timeout=None) as client:
        async with client.stream("POST", url, json=payload) as response:
            async for line in response.aiter_lines():
                ...
                yield chunk
```

---

## 7.2 request.is_disconnected()

## 목적

사용자 disconnect 시 Ollama generation 즉시 종료.

## 필수 구현

```python
async for chunk in call_ollama_stream_async(...):
    if await request.is_disconnected():
        disconnected = True
        break

    yield chunk
```

## 중요성

이 로직이 없으면:

```text
사용자 이탈
→ Ollama generation 계속 진행
→ GPU 점유 지속
→ CPU 낭비
→ queue starvation
```

발생 가능.

---

## 7.3 SSE Event Type 표준화

Streaming event는 반드시 구조화한다.

## 이벤트 종류

### start

```json
{"type":"start"}
```

의미:

* Prompt 생성 완료
* 실제 generation 시작

---

### chunk

```json
{"type":"chunk","chunk":"안녕하세요"}
```

실제 token stream.

---

### done

```json
{"type":"done"}
```

정상 완료.

주의:

disconnect 발생 시 done을 보내면 안 된다.

---

### error

```json
{"type":"error","message":"timeout"}
```

Streaming 실패.

---

# 8. Spring Boot Streaming Architecture

## 핵심 목표

* 기존 RuleBasedAiReviewService 비대화 방지
* Streaming orchestration 분리
* Reactive streaming 지원
* Disconnect cleanup 보장

---

## 8.1 AiReviewStreamingService

신규 서비스:

```text
AiReviewStreamingService
```

## 역할

* session verification
* USER message 저장
* WebClient streaming 호출
* SSE proxy
* partial persistence
* cleanup
* fallback orchestration

## 절대 하지 말아야 할 것

Streaming 로직을 RuleBasedAiReviewService에 추가.

이유:

* 이미 1200+ line 수준
* SRP 위반
* maintenance cost 증가
* streaming lifecycle 복잡성 증가

---

## 8.2 Reactive WebClient 사용

## 기존 문제

RestClient 기반 streaming은 thread를 점유한다.

```text
Spring thread
 → waiting streaming
```

동시 사용자 증가 시 thread starvation 위험.

## 해결

```text
WebClient + Flux<String>
```

Reactive streaming 사용.

## build.gradle

```groovy
implementation 'org.springframework.boot:spring-boot-starter-webflux'
```

---

## 8.3 Backpressure 전략

## 문제

Streaming 속도가 frontend 소비 속도보다 빠를 수 있음.

## 해결

Bounded buffer 적용.

예시:

```java
.onBackpressureBuffer(50)
```

## 목적

* OOM 방지
* memory blowup 방지
* consumer lag 완화

---

## 8.4 SseEmitter Cleanup

반드시 구현:

```java
emitter.onCompletion(...)
emitter.onTimeout(...)
emitter.onError(...)
```

## Cleanup 목적

```java
subscription.dispose();
```

를 통해:

```text
Frontend disconnect
→ Spring cleanup
→ Python HTTP disconnect
→ Ollama generation stop
```

전파.

---

## 8.5 Partial Persistence 전략

## 목적

Streaming 중 disconnect 또는 error 발생 시 생성 내용을 잃지 않기 위함.

## 저장 전략

AI message를 먼저:

```text
STREAMING
```

상태로 생성.

이후:

* 매 5초마다
  또는
* 200자 이상 누적 시

AiReviewMessage를 STREAMING 상태로 UPSERT한다.

## 목적

* disconnect 시 partial text 유실 방지
* 장시간 generation durability 확보
* observability 및 replay 지원

## 최종 상태

### COMPLETED

정상 종료.

### CANCELLED

사용자 명시 취소.

### DISCONNECTED

브라우저/network disconnect.

### FAILED

streaming failure.

---

# 9. React Frontend Streaming UX

## 목표

* Real-time typing UX
* Safe SSE parser
* Cancel 지원
* Low render overhead

---

## 9.1 Fetch Streaming 사용

```typescript
fetch(...)
response.body.getReader()
```

사용.

## 필수 헤더

```typescript
Accept: 'text/event-stream'
```

---

## 9.2 response validation

반드시 체크:

```typescript
if (!response.ok)
if (!response.body)
```

이유:

* proxy error
* auth failure
* empty body

대응.

---

## 9.3 Buffered SSE Parser

## 기존 위험

```typescript
split("\n")
```

방식은 TCP boundary split 시 JSON parsing crash 가능.

## 해결

buffer 기반 parser 사용.

```typescript
buffer += decoder.decode(value, { stream: true });
const events = buffer.split("\n\n");
buffer = events.pop() ?? "";
```

---

## 9.4 AbortController

## 목적

사용자 cancel 지원.

## 구현

```typescript
const controller = new AbortController();
```

취소 시:

```typescript
reader.cancel();
controller.abort();
```

---

## 9.5 React Batch Update

## 문제

chunk마다 setState 호출 시 excessive rendering 발생.

## 해결

* 100~200ms batching
  또는
* 일정 글자 수 이상 누적 후 update

예시:

```typescript
flush every 150ms
```

## 효과

* render 감소
* CPU usage 감소
* smoother UX

---

## 9.6 AI Request Lifecycle State

필수 상태 머신:

```text
IDLE
LOADING
STREAMING
SUCCESS
ERROR
FALLBACK
CANCELLED
```

## 목적

* UX 일관성
* observability 연결
* retry/fallback state 관리

---

# 10. Fallback 전략

## 매우 중요

Streaming은 실패 가능성이 높은 기능이다.

따라서 fallback 전략이 반드시 필요.

---

## 10.1 Streaming 시작 전 실패

예:

* SSE 연결 실패
* proxy failure
* auth error

대응:

```text
자동 non-streaming API retry
```

---

## 10.2 Streaming 중 실패

정책:

* partial chunk 유지
* fallback message 출력
* non-streaming completion 호출

예시:

```text
응답 연결이 끊겨 기본 응답으로 마무리합니다.
```

---

## 10.3 Metrics 기록

반드시 metric 남김:

```text
fallback_after_stream_start
```

---

# 11. Observability & Monitoring

## 목표

* Streaming visibility 확보
* latency 분석
* disconnect 분석
* fallback 추적

---

## 핵심 Metrics

### first_token_latency_ms

First token까지 걸린 시간.

---

### streaming_duration_ms

전체 stream 시간.

---

### streaming_chunks_total

생성된 chunk 수.

---

### disconnect_total

중간 disconnect 수.

---

### cancel_generation_total

사용자 cancel 수.

---

### fallback_after_stream_start

stream 중 fallback 발생 수.

---

### generation_queue_wait_ms

Ollama generation queue 대기 시간.

---

### chunks_per_stream

stream당 생성 chunk 수 histogram.

---

### average_tokens_per_chunk

chunk 평균 token 크기.

---

### disconnect_during_stream_rate

stream 진행 중 disconnect 비율.

---

### fallback_after_stream_start_rate

stream 시작 후 fallback 발생 비율.

---

### stream_error_by_stage

예:

* python_timeout
* parse_error
* ollama_500
* sse_disconnect

---

# 12. Thread & Resource Management

## 핵심 위험

Streaming은 연결이 살아있는 동안 리소스를 지속 점유한다.

따라서:

* cleanup
* disconnect propagation
* reactive stream
* bounded buffer

가 필수.

---

## 현재 위험 시나리오

```text
사용자 50명
→ stream 20초
→ blocking thread 점유
→ thread starvation
→ 전체 API latency 증가
```

## 해결

```text
WebClient reactive stream
+ bounded backpressure
+ disconnect cleanup
```

---

# 13. Security

## 주요 보안 포인트

### POST + JSON Body 사용

query param 금지.

이유:

* URL logging
* browser history
* proxy logging
* length limit

문제.

---

### Service Token 유지

```text
X-AI-Service-Token
```

기존 Python 인증 유지.

---

### Prompt Injection 방어 유지

Streaming이어도 기존 sanitize_text() 유지.

---

# 14. 테스트 전략

## Python

### test_stream.py

검증:

* chunk yield
* disconnect stop
* SSE format
* error propagation

---

## Spring

### StreamingServiceTest

검증:

* SseEmitter cleanup
* Disposable dispose
* disconnect propagation
* fallback

---

## Frontend

검증:

* split JSON chunk
* parser stability
* AbortController
* reconnect

---

## E2E

도구:

```text
k6
```

측정:

* first token latency
* disconnect handling
* concurrency
* p95 latency

---

# 15. Streaming Trade-offs & 운영 위험 요소

## Streaming 도입의 단점

Streaming은 UX 개선 효과가 크지만 운영 복잡도 또한 크게 증가시킨다.

### 메모리 사용 증가

* chunk buffering
* partial persistence
* reactive queue
* frontend accumulated buffer

등으로 인해 non-streaming 대비 메모리 사용량 증가 가능.

---

### 구현 복잡도 증가

추가로 관리해야 하는 요소:

* disconnect propagation
* cancellation lifecycle
* SSE parser
* reactive cleanup
* partial persistence
* fallback orchestration
* backpressure

---

### Ollama Keep-Alive 영향

Streaming generation은 keep_alive 시간 동안 모델 메모리 점유를 더 오래 유지할 수 있다.

특히:

```text
장시간 stream
→ 모델 unload 지연
→ VRAM 점유 증가
```

가능.

---

### Observability 복잡도 증가

Streaming은 request/response 단위가 아니라 long-lived lifecycle 기반이므로:

* first token latency
* disconnect rate
* chunk throughput
* queue wait

등 추가 metric 관리 필요.

---

### 테스트 난이도 증가

Streaming은:

* partial chunk
* TCP boundary split
* disconnect timing
* cancellation race condition

등으로 deterministic testing이 어려워진다.

---

# 16. 운영 위험 요소

## 15.1 Ollama Cold Start

모델 unload 후 첫 요청 latency 급증 가능.

대응:

* keep_alive
* warmup request

---

## 15.2 Queue Starvation

Streaming generation이 오래 지속되면 queue 증가 가능.

대응:

* semaphore
* queue metrics
* timeout

---

## 15.3 Excessive Render

Frontend excessive state update 위험.

대응:

* batch update
* throttling

---

# 16. 최종 구현 순서

## Phase 1

* Feature Flag
* Python streaming generator
* SSE event type

---

## Phase 2

* Spring WebClient streaming
* AiReviewStreamingService
* cleanup/disconnect

---

## Phase 3

* React buffered parser
* AbortController
* lifecycle state

---

## Phase 4

* Metrics
* Grafana
* Canary rollout

---

# 17. Rollback & Emergency Procedure

## Emergency Rollback

운영 장애 발생 시:

```yaml
app.ai-review.streaming-enabled=false
```

로 즉시 non-streaming 경로로 복귀한다.

## Emergency 대응 기준

다음 조건 중 하나 충족 시 rollback 검토:

* p95 latency 급증
* disconnect rate 급증
* fallback rate 급증
* Ollama queue starvation
* excessive memory usage
* SSE proxy instability

---

# 18. 최종 결론

본 설계는 단순 SSE streaming 구현이 아니라:

* reactive resource management
* disconnect propagation
* workflow consistency
* observability
* graceful fallback
* 운영 안정성

까지 포함하는 production-grade streaming architecture를 목표로 한다.

핵심 원칙은 다음과 같다.

```text
Streaming은 단순 UX 기능이 아니라,
연결이 살아있는 동안 시스템 리소스를 지속 점유하는
장기 lifecycle resource management 문제다.
```

따라서:

* cleanup
* cancellation
* backpressure
* fallback
* observability

없이는 운영 안정성을 보장할 수 없다.
