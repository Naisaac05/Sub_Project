# AI Review Streaming & Observability Master Architecture Plan

작성일: 2026-05-21
버전: Production Version v1.1
상태: Partial Implementation Reflected (2026-05-22 코드 대조)
대상 시스템: AI Review System (Spring Boot + Python FastAPI + Ollama + React)

---

# 1. 문서 목적

본 문서는 현재 AI Review 시스템에 대해 다음 목표를 달성하기 위한 전체 스트리밍 아키텍처 및 운영 설계를 정의한다.

> 2026-05-22 현재 코드 상태: Python FastAPI `StreamingResponse`, Ollama `stream: true` async generator, Spring `SseEmitter` 프록시, Frontend `ReadableStream` 소비 및 `STREAMING` UI 상태는 구현되어 있다. 단, `PYTHON_AI_STREAMING_ENABLED`와 `app.ai-review.streaming-enabled`가 모두 켜져야 실제 스트리밍 경로가 사용된다. Spring `AiReviewStreamingService`는 현재 Python 요청 일부를 stub question/correct/selected 값으로 구성하므로, 운영 주장 시에는 "스트리밍 인프라 구현"과 "기존 non-streaming 의미 컨텍스트 완전 동등화는 보강 필요"를 구분한다.

## 핵심 목표

* First Token Latency 대폭 개선 (< 860ms 목표, 실측 전 목표값)
* 실시간 AI 응답 Streaming UX 제공(부분 구현 완료)
* 기존 RAG / Guardrail / Workflow 구조 유지
* Graceful disconnect propagation 구현
* Non-blocking reactive streaming 구조 도입
* Backpressure 및 thread starvation 방지
* Partial persistence 지원
* 구조화 로그 기반 observability 강화 및 후속 Prometheus / Grafana 연동 준비
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

현재 non-streaming 기본 경로의 주요 문제점:

* Non-streaming 구조
* Blocking RestClient 기반 호출
* Long request 동안 Spring thread 점유
* First token latency 체감 큼
* 사용자 체감 로딩 UX 저하
* 기본 JSON 응답 경로에서는 Streaming 미사용
* 기본 JSON 응답 경로에서는 Disconnect propagation 없음
* 기본 JSON 응답 경로에서는 Ollama generation cancel 불가능
* Partial persistence 없음
* 실시간 observability 부족

별도 스트리밍 경로는 `/api/ai-review/sessions/{sessionId}/messages/stream` → Spring `SseEmitter` → Python `text/event-stream` → Ollama streaming API로 추가되어 있다.

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

현재 구현 완료된 persistence 범위와 향후 고도화 예정 범위를 명확히 분리하여 운영한다.

### 1. 현재 구현 완료 범위 (State-Transition Persistence)
* **COMPLETED 저장**: 스트림이 정상적으로 끝까지 수신된 경우 최종 완료 텍스트를 `STATUS:COMPLETED`로 저장한다.
* **DISCONNECTED 저장**: 클라이언트 disconnect/timeout/send IOException 발생 시 누적된 partial text를 `STATUS:DISCONNECTED`로 저장한다. (단, accumulated text가 비어있으면 저장하지 않고 로그만 남김)
* **PARTIAL_FAILED 저장**: Python stream error 이벤트 또는 WebClient upstream error 발생 시 누적된 partial text를 `STATUS:PARTIAL_FAILED`로 저장한다.

### 2. 향후 고도화 예정 범위 (Future Enhancement)
* **주기적 STREAMING UPSERT**: 스트리밍 진행 중(예: 매 5초마다 또는 200자마다) 중간 텍스트를 `STREAMING` 상태로 DB에 지속적으로 UPSERT하는 전략은 현재 미구현 상태이다. 이는 향후 durability 및 replay 기능 강화를 위한 future enhancement로 남겨둔다.

## 목적

* disconnect 및 partial failure 시 partial text 유실 방지 및 이력 관리
* 불필요한 DB 트랜잭션 및 쓰기 부하를 방지하기 위해 lifecycle 전이 시점에만 persistence 처리

## 최종 상태

### COMPLETED

정상 종료 시 저장되는 최종 상태.

### DISCONNECTED

네트워크 단절 또는 브라우저 timeout, 사용자 중간 이탈(disconnect) 시의 상태. 누적 텍스트가 존재할 때만 저장된다.

### PARTIAL_FAILED

중간 에러(Python API 에러, WebClient 에러 등) 발생 시의 상태. 누적 텍스트가 존재할 때만 저장된다.


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

Streaming은 네트워크 및 Upstream API의 상태에 따라 실패 가능성이 높은 기능이다. 
따라서 사용자의 UX 보호와 시스템 데이터 일관성 유지를 위한 명확한 Fallback 분리 정책을 적용한다.

---

## 10.1 Streaming 시작 전 실패 (첫 Chunk 수신 전)

예:
* SSE 연결 자체의 실패
* WebClient 초기 커넥션 에러 / Gateway Timeout
* 인증 에러 (Auth Error)

대응:
* **자동 Non-streaming Fallback**: 첫 chunk를 수신하기 전(`hasReceivedChunk === false`)에 실패가 감지되면, 클라이언트 측에서 자동으로 기존 non-streaming API(기존 `submitAnswer` 서비스 경로)로 retry 요청을 보내어 응답 생성을 시도한다.

---

## 10.2 Streaming 시작 후 실패 (첫 Chunk 수신 후)

예:
* 스트림 수신 도중 연결 유실 (Disconnect)
* 스트림 도중 upstream error 이벤트 수신 또는 WebClient downstream read timeout/error 발생

대응:
* **자동 재제출 금지**: 이미 화면에 첫 chunk 이상이 렌더링된 상태(`hasReceivedChunk === true`)에서는 **절대로 자동으로 non-streaming API를 재호출하지 않는다.** 중복 질문 제출 및 DB 저장 데이터 정합성 훼손을 방지하기 위함이다.
* **Partial/Error UI 처리**: 이미 누적된 텍스트는 화면에 그대로 유지(partial chunk 보존)하고, UI 하단에 에러 알림 및 `ERROR` 혹은 `PARTIAL_FAILED` lifecycle 상태 피드백을 제공하여 사용자가 인지하고 수동 대응할 수 있도록 처리한다.

---

## 10.3 Metrics 기록

반드시 metric 남김 (설계 단계):
```text
fallback_before_stream_start (시작 전 fallback 횟수)
fallback_after_stream_start_blocked (시작 후 fallback 차단 및 에러 UI 노출 횟수)
```

---

# 11. Observability & Monitoring

> [!NOTE]
> **Observability 상태 명시**: 현재 코드는 `X-Correlation-ID`, Python `observability_events`, Spring `AiReviewMetricSink`의 `metric.ai_review.*` 구조화 로그를 제공한다. 실제 Prometheus/Grafana 환경에 대한 Micrometer Counter, exporter, dashboard wiring은 다음 후속 구현 단계(Future Implementation Phase)에서 진행된다.

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

# 15. Streaming Trade-offs

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

Streaming generation is keep_alive 시간 동안 모델 메모리 점유를 더 오래 유지할 수 있다.

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

## 16.1 Ollama Cold Start

모델 unload 후 첫 요청 latency 급증 가능.

대응:

* keep_alive
* warmup request

---

## 16.2 Queue Starvation

Streaming generation이 오래 지속되면 queue 증가 가능.

대응:

* semaphore
* queue metrics
* timeout

---

## 16.3 Excessive Render

Frontend excessive state update 위험.

대응:

* batch update
* throttling

---

# 17. 최종 구현 순서

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

# 18. Rollback & Emergency Procedure

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

# 19. 최종 결론

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

---

# 20. Appendix: 실제 구현 완료 범위

현재 1차 스트리밍 기능 고도화 작업이 안전하게 완료되었으며, 프론트엔드 및 백엔드 전반의 빌드 및 통합 검증이 완료된 실제 구현 범위는 다음과 같다.

* **Python async streaming generator**: FastAPI 상에서 async generator와 `StreamingResponse`를 적용하여 chunk 단위의 스트림을 Non-blocking으로 생성 및 송신한다.
* **structured SSE event types**: SSE 이벤트 전송 규격을 `start`, `chunk`, `done`, `error`로 구조화하여 클라이언트와의 API 통신 명세를 통일했다.
* **Spring WebClient reactive proxy**: RestClient 대신 non-blocking Reactive WebClient를 사용하여 Python upstream stream을 프록시하고 동시성 및 backpressure를 효율적으로 관리한다.
* **SseEmitter cleanup/disconnect propagation**: SseEmitter의 Timeout, Error, Completion(IOException 포함) 발생 시 Reactive subscription을 명시적으로 `dispose()` 하여 리소스 누수를 방지하고 Python/Ollama upstream으로 취소를 즉각 전파한다.
* **STATUS:COMPLETED / DISCONNECTED / PARTIAL_FAILED persistence**: 스트림 수명 주기 완료 및 비정상 단절 상태에 따라 데이터베이스에 `COMPLETED`, `DISCONNECTED`, `PARTIAL_FAILED` 상태와 누적 partial text를 영속화하여 데이터 신뢰성을 보장한다. (주기적 스트리밍 저장은 future enhancement로 배제)
* **React buffered SSE parser**: 프론트엔드 상에서 TCP chunk 분할 수신 시 JSON parsing 오류를 방지하기 위해 버퍼 누적식 SSE 파서를 도입하였으며, CRLF(\r\n) 개행 포맷을 표준화(`\n`)하여 파싱 안정성을 더했다.
* **AbortController cancellation**: React 컴포넌트 마운트 해제, 신규 제출 또는 사용자의 명시적 취소 발생 시 `AbortController` 및 reader cancel을 호출해 리소스를 즉시 반환한다.
* **lifecycle state machine**: `IDLE`, `LOADING`, `STREAMING`, `SUCCESS`, `ERROR`, `FALLBACK`, `CANCELLED` 등 명시적인 스트리밍 수명 주기 상태 머신을 React state로 정의해 중복 요청 방지 및 일관된 UI 피드백을 실현했다.
* **streaming fallback separation policy**: 첫 chunk 수신 전 실패 시에만 기존 non-streaming API로 자동 fallback을 시도하고, chunk 수신 후 실패 시에는 자동 fallback을 차단하여 DB 데이터 중복을 원천 차단하고 Partial/Error UI 처리를 보장한다.
* **frontend/backend build verification completed**: Backend gradle test(전체 성공) 및 Frontend production build(`npm run build` static page optimization 완료)를 통한 전체 빌드 안정성 검증을 마쳤다.
