# AI Inference Orchestration 운영 보강 TODO

> 작성일: 2026-05-26  
> 대상: 로컬 LLM 기반 AI Review inference orchestration 시스템  
> 기준 문서: `docs/2026-05-26-local-llm-inference-orchestration-risk-review.md`

---

## 목표

현재 시스템은 local-first prototype과 production-grade serving 사이에 있다. 아래 TODO는 기능 추가보다 운영 신뢰성, 상태 일관성, 장애 복구, 관측성, 배포 안전성을 먼저 닫기 위한 실행 순서다.

---

## P0 — 1주차

- [x] Baseline reproduction/metric 확보
  - [x] 현재 dev-laptop 환경 기준 baseline 기록
  - [x] `first_token_latency_ms`, `stream_duration_ms`, `stream_completed`, `stream_disconnected`, `stream_partial_failed`, `fallback_to_sync_count` 기준값 수집
  - [x] 측정 환경에 CPU/RAM/model/prompt/knowledge version 기록
  - 2026-05-26: Real Ollama baseline captured in `docs/smoke/2026-05-26-ai-ollama-streaming-baseline-qwen3-1.7b.md`; repeatable script added at `ai/scripts/measure_ollama_stream_baseline.py`.

- [x] Cancellation propagation 검증 및 수정
  - [x] Spring `Disposable.dispose()` 이후 Python generator 중단 여부 확인
  - [x] Python `request.is_disconnected()` 이후 Ollama stream request 중단 여부 확인
  - [x] Ollama semaphore release 검증 metric 추가
  - 2026-05-26: Spring dispose, FastAPI disconnect cancellation, httpx stream close, and Ollama semaphore release are covered by regression tests; `ai_review.ollama_stream_finished` now emits `semaphore_released`.

- [x] Terminal state idempotency 강화
  - [x] `COMPLETED`, `DISCONNECTED`, `ERROR` 중 하나만 저장되도록 request id 설계
  - [x] duplicate done / late done / disconnect 후 done 저장 방지
  - [x] DB-level idempotency 또는 unique guard 검토
  - 2026-05-26: Request-scoped atomic state regression tests cover duplicate terminal events, late done after disconnect, and disconnect-before-first-chunk late events.
  - 2026-05-26: Streaming terminal AI messages now persist `stream_request_id` plus `stream_terminal_status` (`COMPLETED`, `DISCONNECTED`, `ERROR`). `ai_review_messages.stream_request_id` has a unique JPA guard, and terminal save paths return the existing terminal on duplicate request id or DB race.

- [x] Timeout chain 정리
  - [x] Spring stream timeout 정의
  - [x] Python httpx/Ollama timeout 정의
  - [x] queue wait timeout 정의
  - [x] 운영 profile에서 `OLLAMA_REQUEST_TIMEOUT_SECONDS=0` 금지
  - 2026-05-26: Spring stream timeout remains `AI_REVIEW_STREAM_TIMEOUT_SECONDS=45`; Spring Python/Ollama read timeouts are bounded by `AiRetrySupport.boundedReadTimeout()`. Python Ollama request timeout now stays bounded at 30s even when env is 0/negative, and Ollama semaphore queue wait is bounded by `OLLAMA_QUEUE_WAIT_TIMEOUT_SECONDS=3`.

- [x] Stream race regression test suite 작성
  - [x] disconnect before first chunk
  - [x] disconnect after first chunk
  - [x] Python error before/after chunk
  - [x] duplicate done event
  - [x] late done after disconnect
  - [x] empty upstream completion
  - 2026-05-26: Spring `AiReviewStreamingServiceTest` regression suite now covers disconnect before/after first chunk, Python error before/after chunk, duplicate/late terminal events, and empty upstream completion.

---

## P1 — 2주차

- [x] Metrics backend 최소 연결
  - [x] `AiReviewMetricSink` 구조화 로그를 Micrometer 또는 OpenTelemetry로 승격
  - [x] stream lifecycle counter 추가
  - [x] first-token/stream-duration histogram 추가
  - [x] fallback/degraded mode dashboard 초안 구성
  - 2026-05-26: `AiReviewMetricSink` now keeps structured logs and records Micrometer counters/timers via Spring Boot Actuator. Added stream lifecycle counter, first-token latency timer, stream duration timer, fallback-to-sync counter, and dashboard draft at `docs/2026-05-26-ai-review-metrics-dashboard-draft.md`.

- [x] Load test 환경 구축
  - [x] local dev-laptop load profile 작성
  - [x] cache hit / cache miss / free-form storm 시나리오 작성
  - [x] stream disconnect load 시나리오 작성
  - [x] Ollama timeout/hang 시나리오 작성
  - 2026-05-26: Added repeatable load runner at `ai/scripts/run_stream_load_profile.py` with `cache-hit`, `cache-miss`, `free-form-storm`, `stream-disconnect`, and `ollama-timeout` scenarios. Local runbook documented at `docs/2026-05-26-ai-review-load-test-profile.md`.

- [x] Degraded mode / kill switch 구현 및 검증
  - [x] `streaming_off`
  - [x] `cache_only`
  - [x] `lightweight_only`
  - [x] `no_candidate_capture`
  - [x] `template_fallback_only`
  - 2026-05-26: Added `AI_REVIEW_STREAMING_OFF` in Spring and `AI_REVIEW_CACHE_ONLY` / `AI_REVIEW_TEMPLATE_FALLBACK_ONLY` in Python workflow. Runbook: `docs/2026-05-26-ai-review-degraded-mode-runbook.md`.
  - 2026-05-26: Added Python `AI_REVIEW_NO_CANDIDATE_CAPTURE` kill switch. It bypasses auto-candidate JSONL writes while keeping answer generation active and records `candidate_capture_disabled`.
  - 2026-05-26: Added Python `AI_REVIEW_LIGHTWEIGHT_ONLY` kill switch. It keeps lightweight static/generated-card answers available, skips cache/Ollama on misses, returns `route=lightweight_only_miss`, and marks the path as `llm_call_avoided`.

- [x] Candidate capture disable + write failure isolation
  - [x] candidate capture feature flag 추가
  - [x] candidate write 실패가 answer path를 막지 않도록 격리
  - [x] candidate capture failure metric 추가
  - 2026-05-26: `candidate_save_node` now honors `AI_REVIEW_NO_CANDIDATE_CAPTURE`, isolates `append_auto_candidate()` failures from the answer path, and exposes `candidate_capture_disabled` / `candidate_capture_failed` in structured observability events.

---

## P2 — 3~4주차

- [x] Redis 도입
  - [x] shared answer cache 후보 설계
  - [x] rate limit / admission state 저장 검토
  - [x] distributed single-flight 사전 설계
  - 2026-05-26: Added optional Python Redis-backed answer cache (`AI_REVIEW_ANSWER_CACHE_BACKEND=redis`) using existing cache namespace keys and TTL. Redis failures fall back to memory/JSONL cache. Admission state and distributed single-flight Redis key designs are documented for P3 follow-up. Runbook: `docs/2026-05-26-ai-review-redis-introduction.md`.

- [x] Candidate Queue DB/durable queue 이동
  - [x] JSONL runtime queue 제거 또는 dev-only로 제한
  - [x] candidate status lifecycle 정의
  - [x] audit field 추가
  - 2026-05-26: Runtime candidate capture now defaults to the Spring DB-backed ingest endpoint `POST /api/internal/ai-review/candidates/capture`; JSONL capture requires explicit `AI_REVIEW_CANDIDATE_SINK=jsonl`. Candidate lifecycle is `PENDING` → `APPROVED` / `REJECTED` / `MERGED`, and capture writes `AiReviewCandidateAudit(action=CAPTURE)`. Runbook: `docs/2026-05-26-ai-review-candidate-durable-queue.md`.

- [x] Candidate Approval Workflow 최소 구축
  - [x] captured → drafted → human_review → approved/rejected 상태 정의
  - [x] reviewer/evidence 기록
  - [x] 승인 전 retrieval 대상 제외 보장
  - 2026-05-26: Added `AiReviewCandidateWorkflowPhase` as a separate lifecycle axis (`CAPTURED`, `DRAFTED`, `HUMAN_REVIEW`, `APPROVED`, `REJECTED`, `MERGED`) while preserving decision `status` (`PENDING`, `APPROVED`, `REJECTED`, `MERGED`). `START_REVIEW` records reviewer/timestamp/audit, final review actions update both status and phase, and only `APPROVED` candidates call `AiReviewKnowledgeReindexer`. Runbook: `docs/2026-05-26-ai-review-candidate-approval-workflow.md`.

- [x] Immutable Vector Index 전략 적용
  - [x] `knowledge_index_version` 정의
  - [x] index manifest hash 기록
  - [x] rollback 가능한 previous index 보관
  - [x] vector index 변경 시 cache namespace 변경
  - 2026-05-26: Added v2 immutable index manifest fields (`knowledge_index_version`, `manifest_hash`, `cache_namespace_version`, `previous_versions`) for Python reindex and Spring approval reindex. Answer cache keys now include the active cache namespace, and previous manifests are snapshotted under `vectorstore/manifests/` for rollback. Runbook: `docs/2026-05-26-ai-review-immutable-vector-index.md`.

- [x] Production Config Hardening
  - [x] prod에서 service token 필수
  - [x] prod에서 timeout 필수
  - [x] prod에서 local JSONL source-of-truth 금지
  - [x] prod에서 unbounded stream/input 금지
  - 2026-05-26: Added Spring prod-profile startup validation and Python FastAPI startup validation. Production now fails fast when service token is blank, timeouts/limits are missing or non-positive, candidate capture uses JSONL, or local JSONL candidate paths are configured. Runbook: `docs/2026-05-26-ai-review-production-config-hardening.md`.

- [x] Production Readiness Checklist 작성
  - [x] SLO/SLA 항목 정리
  - [x] failure budget 정리
  - [x] rollback policy 정리
  - [x] incident runbook 연결
  - 2026-05-27: Added P2 closeout checklist with internal SLO/SLA boundaries, conservative failure budget, rollback policy by change type, and incident runbook links across metrics, degraded mode, Redis, candidate queue/approval, immutable vector index, and production config hardening. Runbook: `docs/2026-05-27-ai-review-production-readiness-checklist.md`.

---

## P3 — 5주차 이후

- [x] Inference Gateway + Model Pool
  - [x] Ollama 단일 endpoint 병목 제거
  - [x] model별 queue/capacity metric 추가
  - [x] health check / routing / draining 정책 설계
  - 2026-05-27: Added Python in-process Ollama gateway/model pool with `OLLAMA_MODEL_POOL`, per-model capacity gates, deterministic routing, explicit draining via `OLLAMA_DRAINING_ENDPOINTS`, and stream/sync metrics including endpoint/capacity/in-flight fields. Runbook: `docs/2026-05-27-ai-review-inference-gateway-model-pool.md`.

- [x] Semantic Evaluation
  - [x] evidence-grounding judge 설계
  - [x] contradiction check 추가
  - [x] hallucination suspected answer cache 금지
  - 2026-05-27: Added deterministic Python semantic judge with `evidence_missing`, `contradiction_suspected`, and `hallucination_suspected` flags. Suspicious generated answers are blocked from answer cache writes, and the lightweight evaluator now reports semantic grounding, contradiction absence, and hallucination cache-ban rates. Runbook: `docs/2026-05-27-ai-review-semantic-evaluation.md`.
  - 2026-05-27: Hardened rule-based/lightweight fallback against ambiguous keyword false positives. `network/네트워크` no longer matches network diagram answers by itself, unstable network UX gets a dedicated lightweight answer, and ambiguous keyword guidance is documented in `docs/ambiguous_keywords.md`.
  - 2026-05-30: Completed rule conflict audit for additional ambiguous terms. `entity`, `cache`, and Java `service` fallback conflicts now have explicit guards and regression tests; audit notes are in `docs/2026-05-30-ai-review-rule-conflict-audit.md`.
  - 2026-05-30: Split ambiguous keyword families at intent-classifier level with `api_response_format`, `http_status_code`, `react_state`, `loading_state`, `exception_handling`, and `connection_lifecycle` sub-intents. Regression coverage added in `ai/tests/test_intent_routing.py`.
  - 2026-05-30: Wired selected sub-intents into answer quality. `api_response_format`, `exception_handling`, and `loading_state` now return dedicated lightweight answers before generic static alias matching.
  - 2026-05-30: Completed remaining sub-intent lightweight answers for `http_status_code`, `react_state`, and `connection_lifecycle`, so all currently split ambiguous sub-intents have direct answer-quality wiring.
  - 2026-06-01: Adaptive Judge latency hardening applied. Tier 0 and Tier 1 now skip semantic/grounding judges entirely, Tier 2 uses `semantic_judge_lite_v1`, grounding judge is removed from the response path, and judge model selection is isolated via `PYTHON_AI_JUDGE_MODEL` defaulting to the fallback model such as EXAONE 2.4b.
  - 2026-06-01: Runtime model defaults are now unified on `exaone3.5:2.4b` for primary Python generation, follow-up/fallback generation, direct Ollama fallback, warmup, candidate review drafting, baseline smoke scripts, and Adaptive Judge defaults.
  - 2026-06-01: EXAONE prompt templates were tightened for short, direct Korean answers. Mojibake prompt rules were removed from concept-definition paths, and first-question/follow-up/free-question prompts now emphasize conclusion-first, maximum three-sentence answers without tables, section labels, or reasoning tags.
  - 2026-06-01: Follow-up prompt was replaced with a Korean teacher-tone template. It now branches on "모르겠어요" / partial understanding / wrong concept, forbids labels and markdown, limits output to feedback 1 sentence plus one trailing question, and keeps direct-answer leakage out of tail questions.

- [x] Distributed Single-Flight
  - [x] Redis 기반 request-key lock 검토
  - [x] timeout / stale lock release 정책 정의
  - [x] streaming join 가능 여부 별도 검토
  - 2026-05-27: Added optional Redis-backed distributed single-flight (`AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT=true`) on top of process-local single-flight. Followers wait for answer cache with bounded timeout and fall back to local generation on stale/slow locks; lock release is owner-token guarded. Streaming join remains design-only and is explicitly deferred. Runbook: `docs/2026-05-27-ai-review-distributed-single-flight.md`.

- [x] Full RAG Evaluation Pipeline
  - [x] golden dataset 200~500개로 확장
  - [x] retrieval recall / stale context absent / answer grounding metric 추가
  - [x] candidate promotion 전후 eval 자동화
  - 2026-05-27: Default deterministic golden evaluation now expands curated rows to 200 cases, reports `answer_grounding_rate` alongside retrieval recall/stale-context/semantic metrics, and candidate promotion now records `before_evaluation`, post-promotion `evaluation`, and `evaluation_delta`. Runbook: `docs/2026-05-27-ai-review-full-rag-evaluation-pipeline.md`.

---

## P4 — 운영 리허설 / 릴리즈 게이트

- [x] 운영 리허설 / 릴리즈 게이트 문서화
  - [x] pre-flight production config gate 정의
  - [x] metrics / degraded mode / Redis / gateway / eval smoke command set 정의
  - [x] GO / NO-GO / CONDITIONAL-GO 판정표 정의
  - [x] rollback / kill switch 우선순위 정의
  - 2026-05-27: Added pre-prod rehearsal and release gate runbook covering smoke commands, degraded-mode rehearsal, load/failure checks, metrics evidence, release decision table, rollback priority, and evidence template. Runbook: `docs/운영리허설/릴리즈 게이트.md`.

- [x] 자동화된 릴리즈 게이트 스크립트
  - [x] smoke command set 실행
  - [x] JSON 결과 artifact 생성
  - [x] GO / NO-GO / CONDITIONAL-GO 자동 판정
  - [x] RAG evaluator threshold 검증
  - 2026-05-27: Added `ai/scripts/run_release_gate.py`, which runs the release smoke checks, validates deterministic RAG evaluator thresholds, writes a JSON report, and exits non-zero on `NO-GO`. Dry-run mode is available for command wiring checks.

- [x] AI review next-question UI latency hardening
  - [x] `NEXT_QUESTION` path no longer waits for Python/Ollama first-question generation
  - [x] Regression test verifies next-question movement does not call AI generators
  - 2026-06-01: Fixed slow "next question" clicks in AI review. `NEXT_QUESTION` now saves the deterministic first-question prompt immediately instead of synchronously generating a new model answer, so moving to the next wrong question is decoupled from local model latency.

---

## 실행 원칙

- P0가 끝나기 전에는 production-ready 표현을 쓰지 않는다.
- P1이 끝나기 전에는 운영 관측성이 충분하다고 말하지 않는다.
- P2가 끝나기 전에는 distributed/scalable architecture라고 말하지 않는다.
- P3는 기능 고도화이며, P0~P2의 안정성 작업보다 앞서면 안 된다.
