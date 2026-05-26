# 로컬 LLM 기반 AI Inference Orchestration 운영 리스크 분석

> 작성일: 2026-05-26  
> 역할 관점: Senior AI Research Engineer / LLM Infrastructure Architect / MLOps Engineer  
> 대상 시스템: Spring Boot + FastAPI + Ollama + SSE Streaming + RAG + Validation/Fallback workflow + Runtime candidate queue  
> 개발 환경 전제: Intel i5-10210U, RAM 16GB, Intel UHD, 로컬 Ollama, GPU 서버 없음

---

## 전제

이 프로젝트는 단순 AI 모델 학습 프로젝트가 아니다. 실제 구조는 로컬 LLM을 FastAPI/Python workflow가 호출하고, Spring Boot가 세션/저장/프록시를 담당하며, Frontend가 SSE stream을 소비하는 local-first AI inference orchestration 시스템이다.

노트북 환경에서 로컬 Ollama를 사용한 선택은 개발 현실상 이해된다. 하지만 운영 기준으로 보면 병목, 장애 전파, timeout, cancellation, runtime state consistency, cache/candidate contamination이 핵심 리스크다. 특히 i5-10210U/16GB/내장 GPU 환경에서 얻은 latency/concurrency 수치는 프로덕션 성능 주장으로 쓰면 안 된다. 반드시 `dev-laptop` 실험값과 운영 유사 환경 벤치마크를 분리해야 한다.

아래 평가는 칭찬이 아니라 운영 장애를 줄이기 위한 냉정한 리스크 점검이다. 근거가 코드나 문서에서 확인되지 않는 부분은 명시적으로 "근거 부족"이라고 적었다.

---

## 시스템 흐름도

```text
Frontend Review Page
  └─ fetch + ReadableStream
      ↓
Spring Controller / SSE Endpoint
      ↓
AiReviewStreamingService.streamAnswer()
  ├─ session/question context resolve
  ├─ SseEmitter lifecycle hooks
  ├─ user/AI message persistence
  └─ AiReviewMetricSink stream metrics
      ↓
PythonAiReviewClient.streamReview()
  └─ WebClient text/event-stream proxy
      ↓
Python FastAPI
  └─ ai/app/main.py::_generate()
      ↓
Workflow Runner
  └─ ai/app/workflow/runner.py::run_review_workflow_stream()
      ↓
RAG / Fast-path / Cache
  ├─ retrieve_context_node()
  ├─ resolve_lightweight_answer()
  └─ get_cached_answer()
      ↓
Ollama Streaming Generation
  └─ ai/app/ollama/client.py::call_ollama_stream_async()
      ↓
Validation / Fallback / Candidate Capture
  ├─ validate_answer_node()
  ├─ confidence_gate_node()
  ├─ fallback_answer_node()
  └─ candidate_save_node()
      ↓
SSE chunk / done event
      ↓
Spring persistence + Frontend rendering
```

이 흐름에서 가장 취약한 지점은 Spring SSE lifecycle, Python stream cancellation, Ollama semaphore, file-based runtime state, cache/candidate promotion 경계다. 즉 "LLM이 답을 잘하느냐"보다 "오래 걸리고 끊기고 실패할 때 상태가 일관되게 닫히느냐"가 운영 리스크의 핵심이다.

---

## Normal Streaming Flow

정상 흐름은 다음과 같다.

1. 사용자가 Frontend review page에서 free question 또는 답변을 제출한다.
2. Frontend는 streaming endpoint에 요청하고 `ReadableStream`으로 SSE event를 읽는다.
3. Spring `AiReviewStreamingService.streamAnswer()`가 session ownership, 현재 question, correct/selected answer, free question 제한을 해석한다.
4. Spring이 `PythonAiReviewClient.streamReview()`로 Python FastAPI `/api/review/free-question`에 `Accept: text/event-stream` 요청을 보낸다.
5. Python `main.py::_generate()`가 `PYTHON_AI_STREAMING_ENABLED`와 request payload/header를 보고 streaming path로 진입한다.
6. `run_review_workflow_stream()`이 context retrieval과 rule evaluation을 수행한다.
7. lightweight fast-path를 확인한다.
8. answer cache를 조회한다.
9. cache miss이면 prompt를 구성하고 `call_ollama_stream_async()`로 Ollama stream generation을 시작한다.
10. Python은 chunk event를 Spring으로 yield한다.
11. Spring은 첫 chunk에서 user message 저장과 `stream_first_token` metric을 남긴다.
12. Python generation이 끝나면 validation, confidence gate, fallback, cache write, candidate save를 수행한다.
13. Python은 done event와 정규화된 response metadata를 보낸다.
14. Spring은 completed AI message를 저장하고 `AiReviewSubmitResponse` 형태의 done event를 Frontend로 보낸다.
15. Frontend는 streaming content를 최종 message list로 반영한다.

정상 흐름을 기준으로 보면 비정상은 크게 네 가지다. 첫째, first chunk 전 실패해서 sync fallback으로 내려가는 경우. 둘째, first chunk 후 실패해서 partial failed/disconnected로 닫혀야 하는 경우. 셋째, Python/Ollama upstream은 계속 생성 중인데 client/Spring은 이미 닫힌 경우. 넷째, done event는 왔지만 persistence 또는 response normalization이 실패하는 경우다.

---

## System Boundary

| Boundary | 책임 | 책임지면 안 되는 것 | 주요 코드 |
|:---|:---|:---|:---|
| Frontend | 사용자 입력, stream rendering, optimistic UI, abort signal | AI 답변 생성, DB 저장 판단 | `frontend/src/app/tests/results/[id]/review/page.tsx` |
| Spring Controller/SSE | HTTP/SSE endpoint, 인증 사용자 연결, client-facing response contract | LLM prompt 생성, RAG retrieval | `AiReviewStreamingService.streamAnswer()` |
| Spring AI Service | session ownership, current question resolution, message persistence, SSE lifecycle, sync fallback policy | Python workflow 내부 validation/fallback 판단 | `RuleBasedAiReviewService`, `AiReviewContextSupport` |
| Python Client | Spring에서 Python FastAPI로 요청 전달, SSE proxy, service token propagation | stream event 의미 변경, AI message DB 저장 | `PythonAiReviewClient.streamReview()` |
| Python FastAPI | service token 검증, admission gate, streaming/non-streaming route 선택 | Spring session ownership 판단 | `ai/app/main.py::_generate()` |
| Python Workflow | fast-path, cache lookup, RAG retrieval, prompt build, validation, fallback, candidate capture | Spring DB transaction, user/session authorization | `run_review_workflow_stream()` |
| Ollama Client | local model generation, stream parsing, semaphore, keep_alive | RAG 품질 판단, persistence 판단 | `call_ollama_stream_async()`, `_GENERATION_SEMAPHORE` |
| Runtime Stores | answer cache, candidate queue, vectorstore | source-of-truth session state | `answer_cache.py`, `auto_candidates.py`, Chroma path |

경계 원칙은 단순하다. Spring은 "누구의 어떤 세션에 무엇을 저장할지"를 책임지고, Python은 "어떤 답을 어떻게 생성/검증/대체할지"를 책임져야 한다. 이 경계가 흐려지면 상태 중복과 장애 복구 중복이 생긴다.

---

## Streaming State Machine Table

| Current | Event | Next | Action | Persistence Rule |
|:---|:---|:---|:---|:---|
| INIT | stream accepted | ACTIVE | Python upstream subscription 생성 | 저장 없음 |
| INIT | sync fallback required | COMPLETED | `submitAnswer()` sync path 호출 | sync response 저장 정책 사용 |
| ACTIVE | start event | ACTIVE | client에 stream 시작 신호 전달 | 저장 없음 |
| ACTIVE | first chunk | ACTIVE | first token metric 기록, user message 저장 | user message는 request당 1회 |
| ACTIVE | chunk | ACTIVE | accumulated text append, SSE send | AI message 저장 없음 |
| ACTIVE | done | COMPLETED | subscription dispose, completed AI message 저장, done response 전송 | completed AI message 1회 |
| ACTIVE | upstream error event | ERROR | partial failed metric, partial save, error event 전송 | accumulated가 있을 때만 partial failed 저장 |
| ACTIVE | downstream send IOException | DISCONNECTED | subscription dispose, disconnected metric, partial save | accumulated가 있을 때만 disconnected 저장 |
| ACTIVE | SseEmitter timeout | DISCONNECTED | subscription dispose, timeout metric, emitter complete | accumulated가 있을 때만 disconnected 저장 |
| ACTIVE | SseEmitter onError | DISCONNECTED | subscription dispose, error metric, emitter completeWithError | accumulated가 있을 때만 disconnected 저장 |
| ACTIVE | SseEmitter onCompletion before done | DISCONNECTED | subscription dispose, completion metric, partial save | accumulated가 있을 때만 disconnected 저장 |
| ACTIVE | upstream completes without done but accumulated exists | COMPLETED | implicit done response 생성 | completed AI message 1회 |
| ACTIVE | upstream completes empty | ERROR | empty completion error event | AI message 저장 없음 |
| COMPLETED | any later event | COMPLETED | ignore | 추가 저장 금지 |
| DISCONNECTED | any later event | DISCONNECTED | ignore | 추가 저장 금지 |
| ERROR | any later event | ERROR | ignore | 추가 저장 금지 |

이 표가 필요한 이유는 race condition을 "느낌"이 아니라 terminal state 위반으로 판정하기 위해서다. 예를 들어 `DISCONNECTED` 이후 `COMPLETED` 저장이 발생하면 명백한 invariant 위반이다.

---

## Invariants

- 하나의 stream request는 하나의 terminal state만 가져야 한다: `COMPLETED`, `DISCONNECTED`, `ERROR` 중 하나다.
- `COMPLETED`와 `DISCONNECTED` AI message가 같은 request에서 동시에 저장되면 안 된다.
- `COMPLETED`와 `PARTIAL_FAILED` AI message가 같은 request에서 동시에 저장되면 안 된다.
- user message는 first chunk 또는 done 처리 시 request당 최대 1회만 저장되어야 한다.
- first chunk 이후에는 자동 sync fallback을 실행하면 안 된다.
- first chunk 이전 실패만 sync fallback 후보가 될 수 있다.
- accumulated text가 비어 있으면 disconnected/partial failed AI message를 저장하지 않는다.
- done event의 response는 client-facing contract상 `AiReviewSubmitResponse` 형태여야 한다.
- Python workflow가 fallback/template 답변으로 내려간 경우 cache 저장은 보수적으로 제한해야 한다.
- low confidence, validation failure, contradiction suspected answer는 candidate promotion 또는 cache 저장 대상이 되면 안 된다.
- Spring은 session ownership과 persistence를 판단하고, Python은 generation/validation/fallback을 판단한다.
- runtime candidate queue는 approved knowledge와 분리되어야 한다.
- service token이 비어 있는 운영 배포는 허용하면 안 된다.
- `OLLAMA_REQUEST_TIMEOUT_SECONDS=0`은 운영 profile에서 허용하면 안 된다.
- 구조화 로그만으로 "운영 관측성 완료"라고 주장하면 안 된다. metrics backend 연결 전에는 "log-based observability"가 정확하다.

---

## Recovery Policy

| Failure | Recovery Rule | Retry Policy | Persistence | Required Metric |
|:---|:---|:---|:---|:---|
| stream disabled | sync path로 즉시 fallback | 허용 | sync submit 저장 정책 | `fallback_to_sync_count{reason=streaming_disabled}` |
| non-streamable mode | sync path로 fallback | 허용 | sync submit 저장 정책 | `fallback_to_sync_count{reason=mode_not_streamable}` |
| failure before first chunk | sync fallback 후보 | 제한적 허용 | sync submit 저장 정책 | `fallback_to_sync_count{reason=before_first_chunk}` |
| failure after first chunk | sync fallback 금지 | 금지 | partial failed 또는 disconnected | `stream_partial_failed` 또는 `stream_disconnected` |
| client disconnect | upstream cancellation attempt, subscription dispose | 금지 | accumulated 있으면 disconnected 저장 | `stream_disconnected{reason=client_disconnect}` |
| send chunk failed | subscription dispose, emitter error close | 금지 | accumulated 있으면 disconnected 저장 | `stream_disconnected{reason=send_chunk_failed}` |
| Python error event | stream error 전달 후 close | 금지 | accumulated 있으면 partial failed 저장 | `stream_partial_failed{reason=python_error_event}` |
| upstream timeout | stream close, fallback은 first chunk 여부에 따라 분기 | first chunk 전만 후보 | empty면 저장 없음, partial 있으면 partial failed | `stream_partial_failed{reason=upstream_timeout}` |
| Ollama hang | request timeout, semaphore release 검증 | 자동 무한 retry 금지 | 상태에 따라 error/disconnected | `ollama_generation_timeout`, `semaphore_wait_ms` |
| done persistence failure | client에는 error 또는 degraded done 명시 | 재시도는 idempotency key 필요 | 중복 저장 금지 | `stream_persistence_failed` |
| candidate write failure | answer response는 유지, candidate capture만 실패 처리 | retry는 async queue 권장 | answer 저장과 분리 | `candidate_capture_failed` |

복구 정책의 핵심은 "first chunk 이후 자동 재제출 금지"다. 사용자가 이미 partial answer를 본 뒤 sync fallback을 자동 실행하면 user message와 AI message가 중복 저장될 가능성이 커진다.

---

## Observability Event Spec

| Event | Meaning | Required Fields | Current Status |
|:---|:---|:---|:---|
| `stream_started` | Spring이 stream request를 수락함 | `correlation_id`, `session_id`, `question_id`, `mode` | 근거 부족. 명시 metric 없음 |
| `stream_first_token` | 첫 chunk가 client로 전송되기 직전/직후 | `correlation_id`, `session_id`, `question_id`, `mode`, `first_token_latency_ms` | `AiReviewMetricSink.streamFirstToken` 로그 있음 |
| `stream_completed` | terminal state가 COMPLETED로 닫힘 | `correlation_id`, `session_id`, `question_id`, `mode`, `chunk_count`, `response_chars`, `first_token_latency_ms` | 구조화 로그 있음 |
| `stream_disconnected` | client disconnect/timeout/send failure로 닫힘 | `correlation_id`, `session_id`, `question_id`, `mode`, `reason`, `response_chars` | 구조화 로그 있음 |
| `stream_partial_failed` | partial answer 이후 upstream/error로 실패 | `correlation_id`, `session_id`, `question_id`, `mode`, `reason`, `chunk_count`, `response_chars` | 구조화 로그 있음 |
| `fallback_to_sync` | streaming 대신 sync path 사용 | `session_id`, `question_id`, `mode`, `reason` | 구조화 로그 있음 |
| `ollama_generation_started` | Ollama generation 시작 | `model`, `route`, `queue_wait_ms`, `num_ctx`, `num_predict` | 근거 부족 |
| `ollama_generation_completed` | Ollama generation 완료 | `model`, `duration_ms`, `tokens_per_second`, `success` | 일부 `ollamaGeneration` 로그만 있음 |
| `ollama_generation_cancelled` | disconnect/timeout으로 upstream 취소 확인 | `model`, `reason`, `elapsed_ms`, `semaphore_released` | 근거 부족. 반드시 추가 필요 |
| `cache_hit` | answer cache hit | `cache_key_hash`, `mode`, `model`, `prompt_version`, `knowledge_index_version` | Python observability 일부 가능, 표준 event spec 부족 |
| `candidate_captured` | runtime candidate 저장 | `candidate_id`, `reason`, `route`, `confidence_score` | Python response/log 일부 가능 |
| `candidate_capture_failed` | candidate 저장 실패 | `reason`, `path_or_store`, `exception_class` | 근거 부족 |
| `validation_failed` | validation gate 실패 | `quality_flags`, `route`, `model` | response metadata에는 있으나 metric spec 부족 |

현재 상태는 "구조화 로그 기반 observability"다. Prometheus/OpenTelemetry/Grafana로 가려면 위 event를 counter, histogram, gauge로 나누어야 한다. 특히 `first_token_latency_ms`, `stream_duration_ms`, `ollama_queue_wait_ms`, `semaphore_wait_ms`는 histogram이어야 한다.

---

## Admission Control Policy

현재 코드에는 Python `ai_request_admission()`과 Ollama `_GENERATION_SEMAPHORE`가 있지만, end-to-end admission policy는 아직 명확하지 않다. 운영에서는 "언제 새 stream을 거부할 것인가"를 명시해야 한다.

| Condition | Decision | Response | Metric |
|:---|:---|:---|:---|
| open stream count >= limit | reject new streaming request | 429 + `Retry-After` 또는 sync degraded response | `admission_rejected{reason=max_open_streams}` |
| estimated queue wait > threshold | reject or cache-only response | 429 또는 cached/lightweight answer only | `admission_rejected{reason=queue_wait}` |
| Ollama semaphore unavailable beyond wait budget | reject generation path | fallback template 또는 503/429 | `ollama_queue_rejected` |
| per-user concurrent stream >= limit | reject user request | 429 + user-facing retry message | `admission_rejected{reason=per_user_limit}` |
| same request key already in flight | join existing if safe, otherwise reject duplicate | single-flight join or 409/429 | `single_flight_joined`, `duplicate_rejected` |
| system degraded mode active | disable expensive path | cache/lightweight/template only | `degraded_mode_served` |
| candidate queue write backlog high | keep answer, disable candidate capture | normal answer + no candidate capture | `candidate_capture_shed` |

권장 기본값은 운영 환경에서 다시 측정해야 한다. 노트북 개발 환경 기준으로는 보수적으로 `max_open_streams=2~4`, `max_generation_queue=1`, `queue_wait_budget_ms=1000~3000`, `max_stream_duration_seconds=45` 정도가 현실적이다. 단, 이 값은 dev-laptop safety 값이지 운영 capacity 값이 아니다.

---

## Backpressure Contract

Backpressure는 Spring, Reactor, Python, Ollama가 각자 알아서 처리하면 안 된다. 전체 계약이 있어야 한다.

| Layer | Limit | Required Behavior | Current Risk |
|:---|:---|:---|:---|
| Frontend | one active stream per review session | 새 요청 전 이전 stream abort | UI 중복 submit 방어가 약하면 중복 저장 위험 |
| Spring SseEmitter | `max_open_streams`, `stream_timeout_seconds` | timeout 시 terminal state 저장 후 close | timeout 후 upstream cancellation 근거 부족 |
| Reactor WebClient | `max_buffer_size`, `onBackpressureBuffer` bound | buffer 초과 시 stream fail fast | buffer가 크면 memory pressure, 작으면 premature fail |
| Python FastAPI | admission gate limit | busy 시 429 + Retry-After | 전체 queue wait metric 부족 |
| Python workflow | max generation duration | generation 오래 걸리면 abort/fallback | workflow-level timeout spec 부족 |
| Ollama client | semaphore + request timeout | queue wait 초과 시 reject, timeout 시 release 검증 | `OLLAMA_REQUEST_TIMEOUT_SECONDS=0` 운영 위험 |
| Runtime writes | write timeout / queue depth | candidate/cache write 실패가 answer path를 막지 않음 | JSONL write race와 silent failure 위험 |

필수 contract 값:

- `max_open_streams`: 전체 동시 SSE stream 상한
- `max_open_streams_per_user`: 사용자별 동시 stream 상한
- `max_reactor_buffer_events`: Spring WebClient/SSE buffer event 상한
- `max_generation_queue`: Ollama generation 대기열 상한
- `queue_wait_budget_ms`: semaphore 대기 허용 시간
- `max_stream_duration_seconds`: stream 전체 TTL
- `max_first_token_latency_ms`: 첫 chunk 전 대기 허용 시간
- `max_prompt_chars`: prompt/input 최대 길이
- `max_response_chars`: DB 저장 답변 최대 길이

이 값들이 없으면 장애는 한 계층에서 멈추지 않고 다음 계층으로 밀려난다.

---

## Capacity Planning

아래 수치는 운영 확정값이 아니라 `dev-laptop` 안전 예산 초안이다. i5-10210U/16GB/Intel UHD/로컬 Ollama 기준에서는 공격적으로 잡으면 시스템이 쉽게 포화된다. 운영 환경에서는 별도 부하 테스트로 다시 산정해야 한다.

| Resource / Metric | Dev-Laptop Budget | Production Target 후보 | Notes |
|:---|:---:|:---:|:---|
| max concurrent streams | 2~4 | capacity test 기반 | Spring SSE open connection 상한 |
| max concurrent Ollama generations | 1 | model pool 기준 | `_GENERATION_SEMAPHORE` 기본값과 일치 |
| max generation queue | 1 | 1~N per model | queue가 길어지면 stream timeout으로 전파 |
| avg queue wait | < 1s | < 500ms | 초과 시 reject/degraded 후보 |
| p95 queue wait | < 3s | < 1s | admission control 기준 |
| p95 first token latency | < 5s | < 2s~5s | 860ms 주장은 현재 근거 부족 |
| p95 total stream duration | < 30s | < 15s~30s | 모델/답변 길이에 따라 분리 |
| max stream duration | 45s | 30s~45s | Spring/Python/Ollama timeout 정렬 필요 |
| process memory ceiling | < 12GB | container limit 기반 | 16GB RAM 노트북에서 OS/IDE/DB 여유 필요 |
| candidate queue write latency | < 100ms | < 50ms | answer path를 막으면 안 됨 |
| cache lookup latency | < 10ms | < 5ms | local memory/Redis 기준 |
| vector retrieval latency | < 500ms | < 200ms | Chroma/SentenceTransformer profile 별도 |

Capacity planning에서 가장 중요한 것은 stream 수가 아니라 generation 수다. stream은 많아 보여도 실제 병목은 Ollama generation semaphore와 model load/generation time이다. 따라서 `max_open_streams`는 `max_concurrent_generations + 허용 대기열`보다 크게 잡으면 장애를 숨기는 buffer가 된다.

---

## SLO / SLA

현재 시스템에 외부 고객 SLA를 걸기에는 근거 부족이다. 먼저 내부 SLO로 시작해야 한다.

| SLO | Initial Target | Measurement |
|:---|:---:|:---|
| stream success rate | >= 95% | `stream_completed / stream_started` |
| p95 first token latency | < 5s on dev-laptop, 재측정 필요 | histogram `first_token_latency_ms` |
| p95 total stream duration | < 30s | histogram `stream_duration_ms` |
| stream disconnect rate | < 2% | `stream_disconnected / stream_started` |
| stream partial failure rate | < 1% | `stream_partial_failed / stream_started` |
| fallback to sync rate | < 5% | `fallback_to_sync_count / stream_requests` |
| Ollama timeout rate | < 1% | `ollama_generation_timeout / ollama_generation_started` |
| cache hit correctness incident | 0 tolerated | manual incident / eval regression |
| candidate poisoning incident | 0 tolerated | approved bad candidate count |
| observability event coverage | >= 99% | requests with correlation id + terminal metric |

SLA는 아직 쓰면 안 된다. Prometheus/OpenTelemetry 같은 metrics backend, alert, incident review, load test artifact가 없으면 SLA는 문서상 약속일 뿐 운영 근거가 없다.

---

## Failure Budget

Failure budget은 "얼마나 망가져도 되는가"를 정하는 장치다. 이 시스템은 사용자 학습 품질에 직접 영향을 주므로 cache/candidate 오염은 예산을 거의 0으로 둬야 한다.

| Failure Type | Budget | Action When Exceeded |
|:---|:---:|:---|
| fallback rate | < 5% per day | provider health check, prompt/RAG regression 확인 |
| degraded mode active time | < 1h/day | capacity 증설 또는 expensive path 차단 원인 제거 |
| stream disconnect rate | < 2% | network/client/server timeout 분석 |
| partial failed rate | < 1% | Python/Ollama error 원인 분석 |
| p95 first token SLO violation | < 5% of windows | admission threshold 조정 또는 model pool 증설 |
| Ollama timeout | < 1% | model size/timeout/keep_alive 조정 |
| cache poisoning incident | 0 tolerated | cache namespace purge, offending key quarantine |
| candidate poisoning incident | 0 tolerated | candidate approval rollback, vector reindex |
| duplicate terminal persistence | 0 tolerated | stream state machine/idempotency bug로 P0 처리 |
| missing terminal metric | < 1% | observability pipeline bug로 처리 |

Failure budget이 소진되면 새 기능 개발보다 안정화 작업이 우선이다. 특히 `duplicate terminal persistence`, `cache poisoning`, `candidate poisoning`은 단순 오류율 문제가 아니라 신뢰도 사고다.

---

## Reliability Model

이 시스템은 모든 컴포넌트를 같은 수준의 신뢰성으로 보면 안 된다. 어떤 것은 strong reliability가 필요하고, 어떤 것은 eventual consistency로 충분하며, 어떤 것은 best effort로 격리해야 한다.

### Strongly Reliable

- session ownership check
- current question resolution
- user/AI message persistence
- terminal state uniqueness
- idempotent save
- service-to-service authentication
- request size and stream TTL enforcement

이 영역은 실패하면 사용자 데이터 정합성이 깨진다. 특히 terminal state uniqueness와 idempotent save는 운영 필수 조건이다.

### Eventually Consistent

- answer cache propagation
- candidate queue processing
- candidate approval to knowledge card promotion
- vector index rollout
- observability aggregation
- dashboard refresh

이 영역은 지연이 허용된다. 대신 versioning, replay, rollback, audit log가 있어야 한다.

### Best Effort

- optional candidate capture
- lightweight debug metadata
- non-critical generated draft
- auxiliary observability fields
- high-performance retriever reranking

이 영역은 실패해도 core answer path를 막으면 안 된다. candidate capture 실패 때문에 사용자의 답변 응답이 실패하면 설계가 잘못된 것이다.

---

## Consistency Model

| Component | Consistency Model | Source of Truth | Risk |
|:---|:---|:---|:---|
| MySQL session/message | strong | MySQL | 중복 terminal save는 P0 |
| Spring in-memory stream state | strong per request | `AtomicReference<StreamingState>` | process crash 시 복구 근거 부족 |
| Python answer cache | eventual / process-local | LRU + optional JSONL | replica 간 divergence |
| Redis cache 후보 | eventual with TTL | Redis | stale namespace 관리 필요 |
| Runtime candidate queue | eventual | DB/queue 권장, 현재 JSONL | contamination/write race |
| Vector index rollout | versioned immutable | index manifest | runtime write 금지 필요 |
| Eval dataset | versioned immutable | dataset artifact | leakage check 필요 |
| Observability aggregation | eventual | metrics backend | log-only면 alert 지연 |
| Frontend optimistic UI | temporary divergent | server response reconciles | stream 실패 시 UI/DB 불일치 위험 |

Consistency 원칙은 간단하다. 사용자 세션과 메시지는 strong consistency가 필요하다. cache, candidate, vector index는 eventual consistency로 두되 version과 rollback을 강제해야 한다. Frontend optimistic UI는 임시 divergence만 허용된다.

---

## Incident Severity

| Severity | Incident | Criteria | Immediate Action |
|:---|:---|:---|:---|
| P0 | duplicate terminal persistence | 같은 request에서 completed/disconnected/error가 중복 저장 | streaming disable, write path freeze, hotfix |
| P0 | cache poisoning | 틀린 답이 cache hit로 반복 제공 | cache namespace purge, cache write disable |
| P0 | candidate contamination | 미검수/오염 candidate가 approved/vector index에 반영 | candidate quarantine, vector rollback |
| P0 | unauthenticated AI boundary | service token 없이 Python AI 접근 가능 | endpoint block, token rotate |
| P1 | stream disconnect spike | disconnect rate SLO 초과 | streaming_off 또는 degraded mode |
| P1 | Ollama timeout increase | timeout/fallback 급증 | cache_only, inference restart/scale |
| P1 | fallback rate spike | fallback rate budget 초과 | provider/RAG/prompt regression 조사 |
| P1 | observability gap | terminal metric 누락 증가 | metric pipeline 복구 |
| P2 | degraded mode activation | degraded mode 진입 | 원인 분석, capacity plan 수정 |
| P2 | candidate backlog high | review queue 처리 지연 | capture shed 또는 review capacity 증설 |

P0는 사용자 데이터 정합성 또는 지식 오염 문제다. P1은 서비스 품질 저하 문제다. P2는 운영 효율과 backlog 문제다.

---

## Incident Response Flow

```text
Detect
  ↓
Classify severity
  ↓
Mitigate user impact
  ↓
Isolate faulty path
  ↓
Rollback or degrade
  ↓
Recover state
  ↓
Verify invariants
  ↓
Postmortem
```

### Required Steps

1. Detect: metric, log, user report, synthetic test 중 어떤 경로로 감지됐는지 기록한다.
2. Classify: P0/P1/P2로 분류한다.
3. Mitigate: streaming off, cache_only, candidate capture off, provider fallback 중 하나를 선택한다.
4. Isolate: Spring persistence, Python workflow, Ollama, cache, candidate, vectorstore 중 어느 경계인지 좁힌다.
5. Rollback: feature flag, cache namespace, vector index, candidate promotion을 되돌린다.
6. Recover: stuck stream, semaphore wait, queue backlog, partial records를 정리한다.
7. Verify: terminal state uniqueness, cache correctness, candidate quarantine, metric recovery를 확인한다.
8. Postmortem: invariant 위반과 재발 방지 action을 문서화한다.

---

## Rollback Policy

| Target | Rollback Action | When |
|:---|:---|:---|
| streaming | `streaming-enabled=false` | disconnect/partial failure spike |
| Ollama generation | force `cache_only` or `template_fallback_only` | timeout/hang/queue saturation |
| cache | namespace purge or disable cache write | cache poisoning/stale answer |
| candidate queue | quarantine new candidates | candidate contamination/backlog |
| vector index | rollback to previous immutable index version | retrieval regression/bad approved card |
| prompt | revert `prompt_version` | answer quality regression |
| model route | revert model/provider route | model-specific failures |
| RAG high-performance profile | fallback to lexical/BM25 | Chroma/SentenceTransformer failure |
| frontend streaming UI | hide streaming path, use sync UI | SSE client regression |

Rollback은 데이터와 코드가 함께 움직여야 한다. 예를 들어 vector index를 rollback했으면 cache namespace도 같이 바꿔야 한다. prompt를 rollback했으면 eval artifact를 다시 생성해야 한다.

---

## Security Boundary

현재 문서와 코드에서 service token hook은 보이지만, 운영 security boundary로는 부족하다. 로컬 개발에서는 괜찮아도 public/internal production network에서는 SSE abuse와 oversized prompt가 실제 위험이다.

| Risk | Scenario | Required Guard |
|:---|:---|:---|
| missing service token | Spring 외부에서 Python FastAPI 직접 호출 | prod에서 `AI_REVIEW_SERVICE_TOKEN` 필수, 빈 값 startup fail |
| replay attack | 과거 SSE/generate 요청 재전송 | timestamp/nonce/request id, short TTL, idempotency key |
| SSE abuse | 클라이언트가 stream을 다수 열고 유지 | per-user/IP stream limit, max stream duration |
| infinite stream | upstream이 done 없이 계속 chunk 전송 | max stream duration, max chunk count, max response chars |
| oversized prompt | 긴 user answer/options로 prompt 폭증 | request body limit, field length validation, max prompt chars |
| prompt injection | user text가 system rule 우회 시도 | `guardrails.py` 강화, prompt boundary, answer validation |
| candidate poisoning | 악의적 질문이 candidate queue에 저장 | candidate capture rate limit, reviewer approval, source audit |
| cache poisoning | 부정확/악성 답변이 cache hit로 반복 | low-confidence/no-evidence answer cache 금지 |
| metadata leakage | observability log에 민감정보 포함 | PII masking, log field allowlist |

보안 경계도 역할 분리가 필요하다. Spring은 user authentication/session authorization을 책임지고, Python은 service-to-service token과 input sanitization을 책임진다. Python이 사용자 권한을 추론하면 안 되고, Spring이 prompt security를 전적으로 Python에 떠넘겨도 안 된다.

---

## Degraded Mode

운영에서는 "정상/장애" 이분법보다 degraded mode가 중요하다. AI가 느리거나 불안정할 때 완전히 죽이지 않고 비용이 싼 경로만 살리는 정책이다.

| Mode | Trigger | Enabled | Disabled | User Impact |
|:---|:---|:---|:---|:---|
| `normal` | 모든 지표 정상 | streaming, RAG, Ollama, cache, candidate capture | 없음 | 정상 AI 답변 |
| `streaming_off` | stream disconnect/error 급증 | sync response, cache, lightweight fast-path | SSE streaming | 느리지만 안정적인 응답 |
| `cache_only` | Ollama unavailable 또는 queue saturation | cache, static fast-path, generated card fast-path | Ollama generation, candidate capture | 일부 질문만 즉시 답변 |
| `lightweight_only` | CPU/RAM pressure 높음 | static templates, concept cards | RAG high-performance, Ollama | 답변 범위 축소 |
| `no_candidate_capture` | candidate queue contamination/backlog | answer generation | auto candidate write | 학습 루프 일시 중단 |
| `template_fallback_only` | provider outage | rule/template fallback | Python/Ollama generation | 품질 낮은 안전 응답 |
| `maintenance` | deploy/migration/reindex | read-only cache/static | writes, candidate capture, vector reindex | 제한적 사용 |

Degraded mode 진입과 해제는 수동 flag와 자동 alert 기반을 모두 지원해야 한다. 단, 자동 전환은 보수적으로 해야 한다. 특히 `cache_only`는 stale answer를 확산시킬 수 있으므로 cache namespace와 TTL이 필수다.

---

## Data Lifecycle

이 시스템의 위험은 데이터가 섞이는 순간 커진다. cache, candidate, vectorstore, eval dataset은 lifecycle이 달라야 한다.

### Answer Cache Lifecycle

| State | Meaning | Allowed Transition | Guard |
|:---|:---|:---|:---|
| created | answer generated and considered cacheable | validated | fallback/template/low confidence 제외 |
| validated | validation/fallback gate 통과 | stored | prompt/model/knowledge version key 필요 |
| stored | LRU/Redis/JSONL cache에 저장 | served/expired/purged | TTL 또는 namespace 필요 |
| served | cache hit로 응답 | expired/purged | stale metric 필요 |
| expired | TTL 또는 namespace 변경 | purged | 자동 삭제 |
| purged | 운영자가 제거 | none | incident 기록 |

### Candidate Lifecycle

| State | Meaning | Allowed Transition | Guard |
|:---|:---|:---|:---|
| captured | runtime candidate queue에 저장 | drafted/rejected | source question, route, confidence 필요 |
| drafted | draft definition 생성 | human_review | retrieval 대상 제외 |
| human_review | reviewer 검수 중 | approved/rejected | evidence 필수 |
| approved | 지식카드로 승격 가능 | indexed | reviewer/audit log 필요 |
| indexed | vectorstore/retriever에 반영 | monitored/rolled_back | eval before/after 필요 |
| rejected | 폐기 | archived | 재승격 방지 reason |
| rolled_back | 문제 발견 후 제거 | archived/reindexed | cache purge 동반 |

### Vectorstore Lifecycle

| State | Meaning | Allowed Transition | Guard |
|:---|:---|:---|:---|
| built | approved cards로 index 생성 | validated | manifest hash 필요 |
| validated | retrieval eval 통과 | deployed | golden eval 통과 |
| deployed | runtime query 대상 | superseded/rolled_back | immutable version |
| superseded | 새 index로 교체 | archived | old cache namespace 만료 |
| rolled_back | 문제 index 제거 | archived | incident 기록 |

### Eval Dataset Lifecycle

| State | Meaning | Allowed Transition | Guard |
|:---|:---|:---|:---|
| proposed | 새 eval row 후보 | reviewed | source/labeler 필요 |
| reviewed | 기대 route/concept/answer 검토 | accepted/rejected | leakage check |
| accepted | benchmark에 포함 | versioned | dataset version bump |
| versioned | fixed eval artifact | used | result artifact 저장 |
| deprecated | 더 이상 쓰지 않음 | archived | reason 기록 |

가장 중요한 규칙은 runtime candidate가 곧바로 vectorstore나 eval dataset으로 들어가면 안 된다는 점이다. 반드시 human review와 eval before/after를 거쳐야 한다.

---

## Deployment Topology

현재 local-first topology는 다음에 가깝다.

```text
Developer Laptop
  ├─ Frontend Next.js
  ├─ Spring Boot API
  ├─ FastAPI AI Service
  ├─ Ollama local process
  ├─ MySQL / Redis local
  ├─ JSONL candidate/cache files
  └─ local Chroma/vectorstore files
```

운영 유사 topology는 최소한 다음처럼 분리되어야 한다.

```text
Browser / Frontend
  ↓
Spring Boot API
  ├─ session ownership
  ├─ persistence
  ├─ SSE lifecycle
  └─ auth/rate limit
  ↓
Python AI Gateway / FastAPI
  ├─ admission control
  ├─ workflow runner
  ├─ RAG retrieval
  ├─ validation/fallback
  └─ candidate capture producer
  ↓
Inference Layer
  ├─ Ollama instance A
  ├─ Ollama instance B
  └─ future model pool / gateway

Shared Stores
  ├─ MySQL: session/message/source of truth
  ├─ Redis: answer cache / rate limit / single-flight coordination
  ├─ Durable Queue or DB: candidate queue
  ├─ Vector Store: versioned approved knowledge index
  └─ Metrics Backend: Prometheus/OpenTelemetry/Grafana
```

| Unit | Scale Point | Should Be Stateful? | Notes |
|:---|:---|:---:|:---|
| Frontend | horizontal | no | stream reconnect 정책 필요 |
| Spring Boot | horizontal | no, DB-backed | session/message는 DB가 source of truth |
| Python AI Gateway | horizontal | no, shared cache/queue 필요 | local memory cache만 쓰면 replica divergence |
| Ollama Pool | model capacity 기준 | yes per model instance | health/queue/cancel metric 필수 |
| Redis | managed/shared | yes | cache, rate limit, distributed single-flight |
| Candidate Queue | DB/queue | yes | human review workflow와 연결 |
| Vector Store | versioned deployment | yes but immutable per version | runtime write 금지 |
| Metrics Backend | managed/shared | yes | alert/SLO 기반 |

이 topology에서 scaling point는 Spring이 아니라 Ollama pool과 Python AI Gateway다. Spring SSE connection 수를 늘려도 Ollama generation capacity가 늘지 않으면 병목은 그대로다.

---

## Config Matrix

| Config | Dev Laptop | Stage | Prod |
|:---|:---|:---|:---|
| streaming | on/off both tested | on | on with kill switch |
| stream timeout | relaxed, 45s | medium, 30~45s | strict, SLO aligned |
| Ollama timeout | may be relaxed for debug | required | required, no zero timeout |
| candidate_capture | on for testing | guarded/limited | guarded, auditable |
| cache write | on | on with namespace | on with versioned namespace |
| cache backend | memory/JSONL allowed | Redis preferred | Redis/shared only |
| vectorstore | local optional | versioned test index | immutable versioned index |
| RAG profile | lexical or hybrid local | production-like | production profile only |
| Ollama local | yes | optional | no, use inference host/pool |
| service token | optional only for local | required | required, startup fail if missing |
| metrics backend | optional logs | required | required |
| degraded mode flag | manual | manual + alert | manual + alert |
| request size limit | relaxed | production-like | strict |
| per-user rate limit | optional | enabled | enabled |
| prompt/eval artifact | optional | required | required |

Prod에서 허용하면 안 되는 값:

- empty `AI_REVIEW_SERVICE_TOKEN`
- `OLLAMA_REQUEST_TIMEOUT_SECONDS=0`
- local JSONL as source of truth
- unversioned vector index
- unbounded stream duration
- unbounded prompt/input size
- metrics backend disabled

---

## Versioning Strategy

이 시스템은 prompt, model, knowledge, cache, eval이 서로 맞물린다. 하나라도 버전이 빠지면 재현성과 rollback이 깨진다.

| Version | Meaning | Must Be Included In |
|:---|:---|:---|
| `prompt_version` | prompt template version | response metadata, cache key, eval artifact |
| `knowledge_index_version` | approved knowledge/vector index version | cache key, retrieval metadata, deployment manifest |
| `cache_namespace_version` | cache invalidation namespace | cache key, purge operation |
| `eval_dataset_version` | golden/regression dataset version | eval report, release note |
| `model_route_version` | provider/model routing policy | response metadata, benchmark |
| `candidate_schema_version` | candidate queue payload schema | candidate row, importer |
| `workflow_version` | Python workflow graph/runner version | response metadata, observability |
| `degraded_policy_version` | degraded mode decision rules | incident record, config |

권장 cache key 구성:

```text
cache_namespace_version
| mode
| model_route_version
| model
| prompt_version
| knowledge_index_version
| normalized_question
| normalized_context_fields
```

Versioning 원칙은 "답변을 만든 조건을 나중에 재현할 수 있어야 한다"다. 현재 문서 수치나 포트폴리오 수치도 versioned eval artifact 없이는 근거 부족으로 봐야 한다.

---

## Cost Model

여기서 cost는 돈만이 아니라 CPU time, memory, latency, operational risk를 포함한다.

### Expensive Path

| Path | Cost Driver | Risk |
|:---|:---|:---|
| Ollama generation | CPU/GPU time, model load, semaphore wait | p95/p99 latency 상승 |
| streaming connection | long-lived HTTP/SSE, buffer, lifecycle state | disconnect/race 증가 |
| vector retrieval | embedding model load, Chroma query | dependency/memory pressure |
| embedding generation/reindex | sentence-transformers compute | 노트북 환경에서 무거움 |
| candidate promotion/reindex | review + index rebuild | contamination risk |

### Cheap Path

| Path | Cost Driver | Risk |
|:---|:---|:---|
| static fast-path | dictionary/template lookup | coverage 제한 |
| generated card fast-path | approved card lookup | stale card risk |
| memory/Redis cache hit | key lookup | cache poisoning/staleness |
| rule/template fallback | string generation | quality 낮음 |

운영 목표는 expensive path를 무조건 없애는 것이 아니라, cheap path hit rate를 높이고 expensive path를 capacity 안에서 예측 가능하게 제한하는 것이다.

권장 지표:

- `cheap_path_hit_rate = (fast_path + cache_hit) / total_requests`
- `expensive_generation_rate = ollama_generation_started / total_requests`
- `cost_per_successful_answer = generation_time_ms / completed_answers`
- `candidate_cost = candidate_captured / approved_candidates`

---

## Threat Model

| Threat | Attack / Failure Mode | Impact | Mitigation |
|:---|:---|:---|:---|
| malicious prompt flooding | 긴 free-form 질문을 대량 전송 | Ollama queue saturation | rate limit, request size limit |
| SSE abuse | stream 연결을 열고 유지 | connection exhaustion | max stream TTL, per-user stream limit |
| replayed stream requests | 같은 request 재전송 | duplicate persistence | nonce/request id/idempotency |
| candidate poisoning | 악의적 질문이 candidate queue로 저장 | knowledge contamination | human review, capture rate limit |
| vector contamination | 오염 candidate가 index에 반영 | repeated wrong RAG answer | immutable index, rollback |
| oversized context injection | options/user answer로 prompt 폭증 | latency/OOM | max prompt chars, truncation policy |
| cache poisoning | 틀린 답이 cache로 반복 제공 | systematic wrong answer | confidence/evidence gate, purge |
| observability leakage | PII가 logs/metrics에 남음 | privacy incident | PII masking, log allowlist |
| service token leakage | Python AI direct access | unauthorized generation | token rotation, network ACL |
| dependency compromise | optional RAG package risk | supply chain risk | lockfile, image scanning |

Threat model에서 가장 특이한 축은 candidate/vector contamination이다. 일반 웹 보안보다 AI knowledge supply chain 보안에 가깝다.

---

## Operational Runbook

### Ollama Timeout Spike

1. `cache_only` 또는 `template_fallback_only` degraded mode를 켠다.
2. candidate capture를 끈다.
3. `semaphore_wait_ms`, `ollama_generation_timeout`, `first_token_latency_ms`를 확인한다.
4. stuck generation이 있는지 확인하고 inference process/pool을 재시작한다.
5. timeout 값을 임시로 늘리는 대신 admission threshold를 낮춘다.
6. 복구 후 degraded mode를 해제하고 p95 latency를 재확인한다.

### Stream Disconnect Spike

1. streaming kill switch를 검토한다.
2. `stream_disconnected{reason=*}` 분포를 확인한다.
3. client disconnect인지 send failure인지 upstream timeout인지 분리한다.
4. first chunk 전/후 비율을 확인한다.
5. first chunk 후 자동 sync fallback이 발생하지 않았는지 invariant를 확인한다.
6. 필요 시 `streaming_off` 모드로 전환한다.

### Cache Poisoning

1. cache write를 즉시 중단한다.
2. 문제 cache namespace를 purge한다.
3. offending answer의 prompt/model/knowledge version을 기록한다.
4. related candidate/vector index 오염 여부를 확인한다.
5. eval dataset에 regression case를 추가한다.
6. cacheability rule을 수정한다.

### Candidate Contamination

1. candidate capture를 끈다.
2. suspect candidate를 quarantine한다.
3. 이미 approved/indexed 되었는지 확인한다.
4. vector index를 이전 immutable version으로 rollback한다.
5. 관련 cache namespace를 purge한다.
6. reviewer/evidence/audit gap을 postmortem에 기록한다.

### Metrics Pipeline Failure

1. 신규 배포/설정 변경 여부를 확인한다.
2. log-based metric이 남는지 확인한다.
3. terminal state metric coverage를 계산한다.
4. dashboard가 비어 있으면 production readiness를 degraded로 표시한다.
5. metric 복구 전에는 SLO 정상이라고 판단하지 않는다.

---

## Incident Postmortem Template

```markdown
# Incident Postmortem

## Summary
- Incident ID:
- Date/Time:
- Severity:
- Owner:
- Status:

## Timeline
- T+0:
- T+N:

## Impact / Blast Radius
- Affected users:
- Affected sessions:
- Affected routes:
- Data consistency impact:
- Knowledge/cache/candidate impact:

## Root Cause
- Technical root cause:
- Trigger:
- Why detection was delayed:

## Invariant Violated
- [ ] terminal state uniqueness
- [ ] idempotent save
- [ ] first chunk 이후 sync fallback 금지
- [ ] cache only validated/evidence-backed answers
- [ ] candidate requires human review before indexing
- [ ] service token required in prod

## Recovery / Rollback
- Feature flag changed:
- Cache purged:
- Candidate quarantined:
- Vector index rolled back:
- Provider/model route changed:

## Verification
- Metrics recovered:
- Duplicate records checked:
- Cache namespace checked:
- Candidate/vector contamination checked:
- Regression test added:

## Prevention Actions
- Code change:
- Test added:
- Monitoring added:
- Runbook updated:
- Owner / due date:
```

Postmortem의 핵심은 "무슨 일이 있었나"가 아니라 "어떤 invariant가 깨졌나"다. invariant 없이 쓰는 postmortem은 재발 방지력이 약하다.

---

## Testing Matrix

| Scenario | Setup | Expected Result | Must Assert |
|:---|:---|:---|:---|
| normal streaming success | chunk -> done | `COMPLETED` terminal state | user 1회 저장, AI completed 1회 저장, done DTO 정규화 |
| disconnect before first chunk | client abort before chunk | sync fallback 후보 또는 clean close | AI partial 저장 없음, fallback 정책 metric |
| disconnect after first chunk | chunk 후 abort | `DISCONNECTED` | completed 저장 없음, partial disconnected 1회 |
| Python error before chunk | upstream error immediately | sync fallback 후보 | duplicate user message 없음 |
| Python error after chunk | chunk 후 error event | `ERROR` / partial failed | sync fallback 금지, partial failed 1회 |
| Ollama timeout before chunk | semaphore acquired then timeout | error or pre-chunk fallback | semaphore release 확인 |
| Ollama timeout after chunk | chunk 후 timeout | partial failed | completed 저장 금지 |
| duplicate done event | done twice | first done only accepted | 두 번째 done ignored |
| done after disconnect | disconnect then late done | terminal state 유지 | disconnected 이후 completed 저장 금지 |
| send chunk IOException | emitter send throws | `DISCONNECTED` | subscription disposed, partial save |
| empty upstream completion | upstream complete without chunk/done | `ERROR` | AI message 저장 없음 |
| cache hit streaming | cached answer exists | chunk + done fast path | Ollama 호출 없음 |
| static fast-path | lightweight answer exists | immediate chunk + done | LLM call avoided metric |
| candidate write failure | candidate path unavailable | answer still returns | candidate failure metric, answer 저장 유지 |
| oversized input | answer exceeds limit | request rejected or truncated safely | prompt size bound enforced |
| per-user stream flood | same user opens many streams | 429/reject | max per-user stream enforced |
| global stream flood | many users open streams | admission reject | overload metric emitted |
| service token missing in prod | empty token prod profile | startup fail or request reject | insecure startup 금지 |
| vector retriever failure | Chroma unavailable | lexical fallback or explicit degraded mode | silent quality regression metric |
| cache invalidation | prompt/knowledge version changes | old cache not reused | versioned cache key |

이 matrix는 단위 테스트만이 아니라 통합 테스트와 부하 테스트로 나눠야 한다. 특히 disconnect/late done/duplicate done은 race condition 테스트로 별도 고정해야 한다.

---

## 코드 근거 맵

| 영역 | 실제 코드 근거 | 리스크 해석 |
|:---|:---|:---|
| Spring streaming entry | `backend/src/main/java/com/devmatch/service/ai/AiReviewStreamingService.java::streamAnswer` | session context, SSE lifecycle, persistence가 한 메서드에 집중됨 |
| Spring Python stream proxy | `backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java::streamReview` | WebClient stream proxy와 backpressure buffer가 핵심 경계 |
| Stream metrics | `backend/src/main/java/com/devmatch/service/ai/AiReviewMetricSink.java::streamFirstToken`, `streamDisconnected`, `streamPartialFailed`, `streamCompleted` | 현재는 구조화 로그이며 metrics backend 근거는 부족 |
| Python streaming endpoint | `ai/app/main.py::_generate`, `StreamingResponse`, `request.is_disconnected()` | disconnect 감지는 있으나 upstream cancellation 완료 여부는 별도 근거 부족 |
| Streaming workflow | `ai/app/workflow/runner.py::run_review_workflow_stream` | fast-path/cache/generation/validation/fallback/candidate save가 stream 경로에 구현됨 |
| Sync single-flight | `ai/app/workflow/runner.py::run_review_workflow`, `answer_cache.py::run_single_flight` | sync에는 single-flight가 있으나 streaming generation 병합 근거는 약함 |
| Cache | `ai/app/workflow/answer_cache.py::get_cached_answer`, `put_cached_answer`, `cache_key_for` | process-local LRU + optional JSONL이라 replica consistency 취약 |
| RAG retrieval | `ai/app/workflow/nodes.py::retrieve_context_node`, `ai/app/rag/retriever.py` | optional Chroma/SentenceTransformer 실패 시 조용한 fallback 가능 |
| Validation/fallback | `ai/app/workflow/nodes.py::validate_answer_node`, `confidence_gate_node`, `fallback_answer_node` | 휴리스틱 중심. semantic contradiction 검증은 근거 부족 |
| Runtime candidate | `ai/app/workflow/graph.py::candidate_save_node`, `ai/app/knowledge/auto_candidates.py::append_auto_candidate` | JSONL file-based runtime queue라 contamination/write race 위험 |
| Ollama semaphore | `ai/app/ollama/client.py::_GENERATION_SEMAPHORE` | local LLM generation이 사실상 단일 병목 |
| Ollama stream | `ai/app/ollama/client.py::call_ollama_stream_async` | async stream은 있으나 timeout 기본값 0이면 장기 hang 위험 |

---

## 1. Architecture Risk

- 문제점: AI orchestration 책임이 Spring `RuleBasedAiReviewService`, `AiReviewStreamingService`, Python `run_review_workflow`, `run_review_workflow_stream`, `graph.py`에 분산되어 있다. sync와 streaming이 같은 개념을 공유하지만 구현 경로는 다르다.
- 왜 위험한지: workflow, 저장, fallback, metric, session context가 여러 계층에 흩어져 있으면 한 경로에서 고친 불변식이 다른 경로에는 적용되지 않는다. 실제로 streaming context, 중복 저장, done DTO 정규화 같은 문제가 별도 패치로 이어졌다.
- 실제 운영 장애 시나리오: sync API는 정상인데 streaming API만 특정 mode/session에서 다른 question context로 답하거나 저장한다. 사용자는 AI가 엉뚱한 문제에 답했다고 느끼고, DB에는 정상 메시지처럼 남는다.
- 개선 방향: orchestration contract를 명시한다. Spring은 session/question/message persistence, Python은 generation/RAG/validation/fallback으로 책임을 고정한다. sync와 streaming은 같은 `ReviewWorkflowState` 단계표를 공유하고, stream-only 부분은 token relay로 제한한다.
- 우선순위: High

---

## 2. Runtime Stability

- 문제점: local Ollama serving은 단일 프로세스/단일 노드 의존도가 높다. Python `ai/app/ollama/client.py`는 `_GENERATION_SEMAPHORE`로 generation 동시성을 제한하고, `OLLAMA_REQUEST_TIMEOUT_SECONDS` 기본값이 0이면 timeout이 없다.
- 왜 위험한지: 노트북 로컬 LLM은 CPU, RAM, 발열, Ollama warm/cold state에 민감하다. timeout 없는 generation은 장애를 "느린 요청"으로 감추고 worker를 오래 점유한다.
- 실제 운영 장애 시나리오: Ollama가 모델 로딩 또는 generation 중 hang된다. Spring stream timeout은 만료되지만 Python/Ollama 작업이 즉시 정리되지 않아 다음 요청까지 밀린다. 사용자는 전체 AI 기능이 멈춘 것처럼 본다.
- 개선 방향: 운영에서는 Ollama request timeout을 필수화한다. model warmup, keep_alive, queue wait, generation duration, cancellation success를 별도 metric으로 남긴다. local Ollama는 개발 profile로 제한하고 운영은 별도 inference host/gateway를 둔다.
- 우선순위: Critical

---

## 3. Streaming Lifecycle

- 문제점: Spring `AiReviewStreamingService`는 `SseEmitter` lifecycle에서 `onCompletion`, `onTimeout`, `onError`, chunk send failure, Python error event, implicit completion을 각각 처리한다. 구조는 진전됐지만 lifecycle 상태 전이가 복잡하고 edge case가 많다.
- 왜 위험한지: SSE는 client disconnect, upstream error, downstream send failure, timeout, normal done이 서로 다른 순서로 발생할 수 있다. 상태 전이와 DB 저장이 조금만 어긋나도 partial message 중복, 누락, 잘못된 완료 처리가 생긴다.
- 실제 운영 장애 시나리오: 클라이언트가 첫 chunk 후 브라우저를 닫는다. 서버는 partial을 `STATUS:DISCONNECTED`로 저장하려 하지만 동시에 upstream completion callback이 도착해 completed message도 저장될 수 있다. 최근 atomic state 처리로 방어했지만, race가 완전히 없다는 운영 근거는 부족하다.
- 개선 방향: stream lifecycle state machine을 문서와 테스트로 고정한다. 각 이벤트 순서 조합에 대한 테스트를 만든다. `ACTIVE -> COMPLETED/DISCONNECTED/ERROR` 단일 전이를 DB unique/idempotency key와 함께 보장한다.
- 우선순위: Critical

---

## 4. RAG Leakage

- 문제점: 지식카드, generated concept cards, `auto_candidates.jsonl`, Chroma vectorstore가 repo 내부에 섞여 있다. Python `candidate_save_node`는 runtime candidate를 파일 queue에 저장한다.
- 왜 위험한지: 사용자 질문에서 생성된 draft가 검수 없이 지식 베이스에 섞이면 retrieval/evaluation contamination이 발생한다. 평가셋이 실제 일반화 성능이 아니라 자기 자신이 만든 지식을 다시 맞히는 구조가 될 수 있다.
- 실제 운영 장애 시나리오: 잘못 생성된 candidate가 승인되거나 vector index에 들어간다. 이후 같은 topic 질문은 계속 잘못된 답을 자신 있게 반환하고, cache까지 타면 정정이 더 늦어진다.
- 개선 방향: approved knowledge, generated draft, runtime candidate queue, eval dataset을 물리적으로 분리한다. promotion 시 reviewer, evidence, before/after eval, knowledge snapshot id를 기록한다. 승인 전 candidate는 retrieval 대상에서 제외한다.
- 우선순위: Critical

---

## 5. State Consistency

- 문제점: 상태가 DB, Python memory LRU, JSONL persistent cache, runtime candidate JSONL, Chroma persist directory, frontend optimistic state에 분산되어 있다.
- 왜 위험한지: 단일 노트북에서는 한 프로세스라 버티지만, 운영 replica가 생기면 state consistency가 깨진다. file append/update 기반 state는 동시 writer에 취약하다.
- 실제 운영 장애 시나리오: Python replica A는 cache hit를 내고 replica B는 miss를 낸다. candidate file은 A에서만 갱신된다. 프론트는 optimistic user message를 보여주지만 서버 저장은 partial failed로 남아 UI와 DB가 어긋난다.
- 개선 방향: 운영 상태를 중앙화한다. answer cache는 Redis/DB TTL cache, candidate queue는 DB/durable queue, vector index는 immutable artifact + versioned deployment로 분리한다. message 저장에는 request id/idempotency key를 넣는다.
- 우선순위: Critical

---

## 6. Failure Recovery

- 문제점: retry/fallback은 존재하지만 정식 circuit breaker는 아니다. `AiRetrySupport`는 retry/backoff/fallback 성격이고, Python workflow도 fallback model/template로 내려간다. Open/Half-Open/Closed 상태 전이 근거는 없다.
- 왜 위험한지: 반복 실패 시 시스템이 "빠르게 차단"하지 않고 계속 느린 fallback/retry를 수행하면 장애가 확산된다. fallback이 200 응답으로 포장되면 운영자는 실제 AI 장애를 늦게 감지한다.
- 실제 운영 장애 시나리오: Python AI가 느려지거나 Ollama가 죽는다. Spring은 retry 후 rule/template fallback으로 사용자는 답을 받지만, 품질은 낮아지고 AI 장애율은 dashboard 없이 로그에만 묻힌다.
- 개선 방향: provider별 circuit breaker 또는 failure budget을 둔다. fallback response에는 route/fallback reason을 저장하고, fallback rate 급증 시 alert를 건다. 자동 fallback은 first chunk 이전/이후 정책을 엄격히 분리한다.
- 우선순위: High

---

## 7. Observability

- 문제점: `AiReviewMetricSink`는 `metric.ai_review.*` 구조화 로그를 남기지만 Micrometer counter, Prometheus exporter, Grafana dashboard wiring은 없다. Python `observability_events`도 응답/로그 기반이다.
- 왜 위험한지: 로그는 사후 분석에는 도움이 되지만 SLO/alert에는 약하다. streaming disconnect, first token latency, fallback rate, queue saturation이 실시간으로 보이지 않으면 장애 대응이 늦어진다.
- 실제 운영 장애 시나리오: `stream_partial_failed`가 급증해도 운영자는 로그를 직접 검색하기 전까지 모른다. 사용자는 AI가 중간에 끊긴다고 신고하고 나서야 문제를 인지한다.
- 개선 방향: 구조화 로그를 유지하되 metrics backend로 승격한다. 필수 지표는 `first_token_latency_ms`, `stream_completed`, `stream_disconnected`, `stream_partial_failed`, `fallback_to_sync_count`, `cache_hit`, `llm_call_avoided`, `candidate_captured`, `ollama_queue_wait_ms`다.
- 우선순위: High

---

## 8. Scalability

- 문제점: 단일 FastAPI, 단일 Ollama endpoint, 파일 기반 cache/candidate/vectorstore에 가깝다. multi-GPU, distributed inference, model pool, shard/replica strategy는 없다.
- 왜 위험한지: local-first architecture는 개발에는 빠르지만 수평 확장 시 공유 상태와 capacity planning이 무너진다. Ollama single endpoint가 전체 AI 기능의 choke point가 된다.
- 실제 운영 장애 시나리오: 사용자 수가 늘자 Ollama queue가 길어지고, Spring SSE 연결이 오래 유지되며, Tomcat/WebClient/Python/Ollama 모두 연결과 buffer를 점유한다.
- 개선 방향: inference gateway를 도입하고 model pool/capacity를 분리한다. read-heavy knowledge index는 immutable artifact로 배포하고, write-heavy runtime candidate는 DB/queue로 이동한다. admission control은 queue length와 model capacity 기반으로 조정한다.
- 우선순위: Critical

---

## 9. Technical Debt

- 문제점: 일부 문서와 코드 문자열에서 한글 인코딩 깨짐이 발견된다. 문서에는 과거 상태와 현재 구현 상태가 섞여 있고, 일부 문서는 Prometheus/Grafana 또는 streaming context 관련 상태가 오래됐다.
- 왜 위험한지: 깨진 fallback/error 메시지는 사용자 품질을 직접 망친다. RAG tokenizer/section key가 깨지면 retrieval 품질도 저하될 수 있다. 오래된 문서는 잘못된 우선순위로 개발을 유도한다.
- 실제 운영 장애 시나리오: 장애 상황에서 사용자에게 깨진 한글 메시지가 노출된다. 운영 문서에는 "구현됨"이라고 되어 있지만 실제 코드는 log-only라 alert가 울리지 않는다.
- 개선 방향: UTF-8 검사를 CI에 넣는다. Windows PowerShell 파일 쓰기는 `-Encoding utf8`을 강제하거나 금지한다. 문서는 `구현됨/부분 구현/미구현/근거 부족/실측 미확인` 상태 라벨을 표준화한다.
- 우선순위: High

---

## 10. Production Readiness

- 문제점: 운영 준비도는 아직 제한적이다. feature flag, fallback, structured logging, service token hook은 있지만 SLO, alert, metrics backend, load test artifact, deployment profile hardening은 부족하다.
- 왜 위험한지: "동작한다"와 "운영 가능하다"는 다르다. local laptop에서 동작하는 구성은 운영 장애 전파, 보안 boundary, replica consistency, observability, rollback까지 증명하지 않는다.
- 실제 운영 장애 시나리오: 운영 배포 후 AI provider env가 빠져 rule fallback으로 조용히 내려간다. 사용자 품질은 낮아졌지만 API는 200을 반환해 장애로 인식되지 않는다.
- 개선 방향: production readiness checklist를 별도 문서로 만들고 gate로 강제한다. 최소 조건은 service token required, timeout required, metrics backend, load test, fallback alert, cache invalidation, candidate approval isolation이다.
- 우선순위: Critical

---

## 11. Prompt/RAG Quality

- 문제점: prompt/RAG 품질 평가는 아직 제한적이다. `golden_dataset.jsonl`은 작은 회귀셋이고, hallucination/evidence-grounding 평가 근거는 부족하다. validation/fallback은 휴리스틱 중심이다.
- 왜 위험한지: RAG 시스템은 검색된 context가 틀리거나 stale하면 LLM이 더 자신 있게 틀린 답을 만든다. 휴리스틱 validation은 한국어 여부나 키워드는 잡아도 의미적 모순은 놓칠 수 있다.
- 실제 운영 장애 시나리오: 사용자가 "TextInput" 같은 topic을 질문했는데 auto candidate draft가 잘못된 일반론을 만든다. 이후 candidate/knowledge에 섞이면 같은 오답이 반복된다.
- 개선 방향: golden set을 topic별/난이도별로 확장한다. answer에는 source/evidence id를 붙이고, RAG answer가 evidence와 모순되는지 judge한다. retrieval miss와 hallucination suspected case는 cache/candidate promotion에서 제외한다.
- 우선순위: High

---

## 12. Cache Consistency

- 문제점: sync workflow는 `run_single_flight`와 answer cache를 사용하지만 streaming generation 구간은 동일한 single-flight 병합 근거가 없다. cache는 process-local LRU + optional JSONL persistent cache다.
- 왜 위험한지: cache key/version/invalidation이 약하면 prompt, model, knowledge index가 바뀐 뒤에도 stale answer가 남을 수 있다. replica가 늘어나면 cache consistency가 더 깨진다.
- 실제 운영 장애 시나리오: 잘못된 답이 cache에 들어간다. 사용자는 같은 질문을 반복할 때 계속 같은 오답을 받는다. 운영자는 어떤 key를 지워야 하는지 알기 어렵다.
- 개선 방향: cache key에 `prompt_version`, `knowledge_index_version`, `model`, `route`, `validation_status`를 명시한다. low confidence, fallback, contradiction suspected answer는 cache하지 않는다. 운영 cache purge API 또는 namespace invalidation을 둔다.
- 우선순위: High

---

## 13. Concurrency Risk

- 문제점: Python Ollama generation은 semaphore로 제한되지만, Spring SSE 연결, WebClient backpressure buffer, Python stream generator, file-based candidate/cache write가 전체적으로 하나의 concurrency model로 묶여 있지 않다.
- 왜 위험한지: 각 계층은 자기 방식으로 제한하지만 end-to-end backpressure가 없다. 한 계층의 queue가 다른 계층의 timeout보다 길어지면 장애가 누적된다.
- 실제 운영 장애 시나리오: 여러 사용자가 동시에 free question을 보낸다. Ollama generation은 직렬화되고, Spring SSE emitter는 계속 열린다. 일부 클라이언트는 timeout으로 끊기지만 upstream 작업은 늦게 정리된다.
- 개선 방향: admission control을 model capacity와 연결한다. queue wait timeout, max stream duration, max open streams, per-user concurrency limit을 둔다. file write는 lock/DB transaction으로 대체한다.
- 우선순위: Critical

---

## 14. Deployment Risk

- 문제점: `application.yml`은 로컬 개발 기본값이 많다. `application-prod.yml`은 DB/JPA 일부만 다루고 AI 설정의 prod guardrail은 부족하다. Python dependency도 base/dev/RAG가 나뉘어 있지만 lockfile 수준의 재현성은 약하다.
- 왜 위험한지: 노트북 개발 환경에서 잘 돌아가던 설정이 운영에 그대로 섞이면 보안, schema, timeout, provider fallback 사고가 생긴다. optional dependency 실패가 조용한 fallback으로 숨겨질 수 있다.
- 실제 운영 장애 시나리오: `AI_REVIEW_SERVICE_TOKEN`이 비어 있거나 Python AI URL이 로컬 기본값으로 남는다. 배포는 성공하지만 실제 AI 호출은 실패하거나 무인증 boundary가 열린다.
- 개선 방향: prod startup validation을 추가한다. prod에서는 service token, provider, timeout, streaming flag, candidate path, cache path, vectorstore path를 명시적으로 요구한다. Python은 constraints/lock과 Docker image profile을 분리한다.
- 우선순위: High

---

## 15. Performance Bottleneck

- 문제점: 핵심 병목은 local Ollama generation이다. 개발 환경은 i5-10210U/16GB/Intel UHD라 CPU-bound LLM inference에 매우 빡빡하다. streaming은 perceived latency를 줄일 뿐 total compute를 줄이지 않는다.
- 왜 위험한지: first token latency 개선과 throughput 개선을 혼동하면 capacity planning이 틀어진다. fast-path/cache hit가 낮아지는 순간 전체 성능은 Ollama 단일 병목으로 회귀한다.
- 실제 운영 장애 시나리오: 포트폴리오/문서의 latency 수치를 믿고 운영 traffic을 받는다. 실제로는 cache miss/free-form 질문이 몰려 p95/p99가 급등하고 stream disconnect가 증가한다.
- 개선 방향: 성능 지표를 route별로 쪼갠다. `fast_path_latency`, `cache_latency`, `queue_wait_ms`, `first_token_latency_ms`, `generation_duration_ms`, `total_stream_duration_ms`, `tokens_per_second`를 분리한다. 노트북 수치는 `dev-laptop`으로만 표기하고 운영 주장은 GPU/서버급 환경에서 재측정한다.
- 우선순위: Critical

---

## Failure Timeline 예시

### A. Client Disconnect After First Chunk

```text
T+0.0s  Frontend sends streaming request
T+0.1s  AiReviewStreamingService creates SseEmitter
T+0.2s  PythonAiReviewClient.streamReview() opens upstream SSE
T+0.5s  run_review_workflow_stream() enters cache/RAG/generation path
T+1.2s  call_ollama_stream_async() emits first chunk
T+1.3s  Spring sends chunk and saves user message
T+2.0s  Browser tab closes or network drops
T+2.1s  SseEmitter onError/onCompletion fires
T+2.2s  Spring marks stream DISCONNECTED and attempts partial save
T+2.3s  Reactor subscription is disposed
T+?    근거 부족: Ollama generation이 즉시 중단됐는지 확인할 metric/test가 부족함
T+?    Risk: semaphore release가 늦어지면 다음 generation이 queue에서 대기
```

핵심은 T+2.3s 이후다. Spring subscription dispose가 Python/Ollama까지 확실히 cancellation을 전파하는지 운영 근거가 부족하다. 이 지점이 local LLM serving 안정성의 가장 큰 blind spot이다.

### B. Upstream Ollama Slow/Hang

```text
T+0.0s   request start
T+0.2s   Python stream path accepted
T+0.4s   _GENERATION_SEMAPHORE acquired
T+1~30s  Ollama model load or generation stalls
T+45s    Spring SseEmitter timeout fires
T+45.1s  streamDisconnected metric logged
T+45.2s  partial content is empty, DB save skipped
T+?      근거 부족: Python httpx stream timeout이 0/default이면 upstream hang이 언제 풀리는지 불명확
T+?      Risk: semaphore remains occupied until upstream exits
```

이 시나리오는 사용자가 보는 장애보다 서버 내부 장애가 더 오래 지속될 수 있다는 점이 핵심이다. timeout은 계층별로 맞물려야 하며, Spring timeout만으로는 충분하지 않다.

### C. Cache/Candidate Contamination

```text
T+0s   user asks ambiguous free-form question
T+1s   RAG retrieval miss
T+5s   LLM generates plausible but weak answer
T+5.2s validation does not detect semantic issue
T+5.3s answer is cached or candidate draft is saved
T+N    same/similar question hits stale cache or promoted candidate
T+N+1  wrong answer becomes repeatable system behavior
```

이 문제는 장애처럼 터지지 않고 품질 부채로 축적된다. 그래서 더 위험하다. cache write와 candidate promotion은 validation보다 더 보수적으로 닫아야 한다.

---

## Immediate Priorities

### P0

- cancellation propagation 검증: Spring `Disposable.dispose()` 이후 Python generator와 Ollama request가 실제로 멈추는지 테스트와 metric으로 확인한다.
- timeout hardening: Spring stream timeout, Python httpx timeout, Ollama request timeout, queue wait timeout을 계층별로 명시한다.
- stream idempotency: completed/disconnected/partial_failed 저장이 하나의 request id에서 한 번만 일어나도록 DB-level idempotency를 둔다.
- runtime state 격리: 승인 지식, generated draft, runtime candidate, eval dataset을 분리한다.

### P1

- observability backend: `AiReviewMetricSink` 로그를 Micrometer/OpenTelemetry/Prometheus 중 하나로 승격한다.
- cache consistency: cache key에 prompt/model/knowledge index version을 포함하고 purge/namespace invalidation을 제공한다.
- golden/eval 확장: hallucination/evidence-grounding 평가를 추가하고 `dev-laptop` benchmark와 운영 benchmark를 분리한다.
- encoding debt cleanup: 깨진 한글 문자열과 오래된 문서를 정리한다.

### P2

- distributed inference 준비: Ollama 단일 endpoint를 inference gateway/model pool 뒤로 이동한다.
- vectorstore 배포 전략: Chroma/local index를 immutable artifact와 runtime query store로 나눈다.
- candidate approval workflow 강화: promotion 전 reviewer/evidence/eval before-after를 필수화한다.
- load test: stream disconnect, slow upstream, cache miss storm, candidate write race를 포함한 부하 테스트를 만든다.

---

## 최종 판단

현재 시스템은 로컬 개발 환경에서 AI review 경험을 빠르게 만들기 위한 구조로는 의미가 있다. 그러나 운영 기준으로는 아직 local-first prototype과 production-grade serving 사이에 있다. 가장 위험한 축은 다음 다섯 가지다.

1. local Ollama single bottleneck
2. streaming lifecycle race와 cancellation 전파 근거 부족
3. file-based runtime state로 인한 cache/candidate contamination
4. log-only observability
5. 노트북 벤치마크를 운영 수치처럼 해석할 위험

이 상태에서 운영 주장을 하려면 "로컬 LLM 기반 inference orchestration을 설계하고, fast-path/cache/fallback/streaming을 통해 제한된 노트북 환경에서 체감 성능을 개선했다" 정도가 정확하다. "프로덕션급 LLM serving", "Grafana 연동", "860ms first-token 달성", "정식 circuit breaker 구현", "분산 확장 가능"은 현재 근거 부족 또는 과장이다.
