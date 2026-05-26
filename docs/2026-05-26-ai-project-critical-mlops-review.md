# AI 프로젝트 냉정 분석 리포트

> 분석일: 2026-05-26  
> 관점: Senior AI Research Engineer / MLOps Architect  
> 범위: `ai/`, `backend/src/main/java/com/devmatch/service/ai/`, `backend/src/main/resources/application*.yml`, `frontend/src/app/tests/results/[id]/review/page.tsx`, `docs/*ai*`  
> 추가 고려: 개발 환경이 노트북 기반임. 제공된 작업 관리자 화면 기준 Intel Core i5-10210U, 4코어/8스레드, RAM 16GB급, Intel UHD 내장 GPU 환경으로 보인다.

---

## 전제

이 프로젝트는 전통적인 모델 학습 프로젝트가 아니다. 코드상 핵심은 로컬 Ollama LLM, Python FastAPI workflow, RAG/지식카드, 정적 fast-path, Spring Boot 세션/저장/중계, Frontend streaming UX가 결합된 AI Review orchestration 시스템이다. 따라서 train/validation/test split, loss, optimizer, multi-GPU 학습 같은 항목은 "구현이 없다"가 정확하다.

노트북 개발 환경이라는 제약은 현실적으로 이해된다. i5-10210U와 16GB RAM, 내장 GPU 조건에서는 4B 이상 모델, Chroma/sentence-transformers, LangGraph, SSE streaming, Spring/Next/MySQL까지 동시에 띄우면 CPU, 메모리, I/O가 쉽게 포화된다. 다만 이 제약은 "운영용 성능 검증"의 근거가 될 수 없다. 노트북에서 얻은 latency, concurrency, first-token 수치는 개발 검증값이지 프로덕션 SLO가 아니다.

문서와 코드 사이에는 불일치가 남아 있다. 과거 문서의 "streaming 컨텍스트가 stub 값에 의존한다"는 지적은 최근 `AiReviewStreamingService` 패치로 상당 부분 해소된 것으로 보인다. 반대로 Prometheus/Grafana, Micrometer counter, 실측 860ms first-token latency, 정식 Circuit Breaker 상태 전이는 여전히 코드 근거가 약하거나 없다.

---

## 1. 구조적 문제점

- 문제점: AI 로직의 책임 경계가 여전히 두껍다. `RuleBasedAiReviewService`는 세션 상태, 문제 이동, 평가, 메시지 저장, Python/Ollama 호출, fallback 문장 생성을 모두 처리한다. `AiReviewStreamingService`도 SSE lifecycle, DB 저장, Python stream parsing, metric emission, 세션 컨텍스트 해석을 동시에 들고 있다.
- 왜 문제인지: AI 기능은 실패 모드가 많다. 세션 상태와 모델 호출, 저장 정책, UI stream 계약이 한 서비스에 몰리면 한 기능을 고칠 때 다른 경로가 깨진다. 실제로 streaming/non-streaming 컨텍스트 불일치, 중복 저장, done DTO 정규화 같은 문제가 최근에 연쇄적으로 발생했다.
- 실제 운영에서 발생 가능한 문제: FREE_QUESTION만 streaming되고 다른 mode는 sync fallback되는 구조에서 운영자가 설정만 보고 "AI streaming이 켜졌다"고 판단하지만, 실제 사용자 경로 일부는 여전히 sync로 흘러간다. 저장 정책도 sync/streaming 양쪽에 있어 장애 시 user message 또는 AI partial message가 중복되거나 누락될 수 있다.
- 개선 방법: Spring 쪽은 `AiReviewContextSupport`를 더 확장해 세션/문제/선택지 해석을 단일 컴포넌트로 고정하고, message persistence는 별도 `AiReviewMessageWriter`로 분리한다. Python 쪽은 sync/stream runner의 공통 workflow 단계와 stream-only generation 단계를 명확히 나눈다.
- 우선순위: High

---

## 2. 데이터 누수 가능성

- 문제점: 전통적인 train/validation/test split은 없다. `ai/evals/golden_dataset.jsonl`은 69라인 규모의 수동 golden set이고, `ai/scripts/evaluate_lightweight_rag.py`는 deterministic generator 또는 실제 Ollama를 선택해 workflow를 점검한다. 이것은 학습 데이터 분할이 아니라 회귀 테스트/평가 샘플이다.
- 왜 문제인지: 현재 제공된 정보로는 모델 학습 데이터 누수는 판단하기 어렵다. 학습 자체가 없기 때문이다. 대신 RAG/평가 누수 가능성은 있다. 지식카드, generated concept card, auto candidate, vectorstore가 같은 repo 안에서 함께 관리되고, runtime auto candidate가 `ai/app/knowledge/candidates/auto_candidates.jsonl`에 쌓인다.
- 실제 운영에서 발생 가능한 문제: 사용자의 질문에서 생성된 draft가 검수 없이 generated card나 vectorstore에 편입되면, 이후 평가셋이 같은 지식을 맞히는 것처럼 보이는 자기참조 성능 착시가 생긴다. `auto_candidates.jsonl`에는 빈 질문, 깨진 질의, 과도하게 일반적인 term이 포함되어 있어 그대로 승인하면 retrieval 품질이 오염된다.
- 개선 방법: 평가셋, 승인 지식카드, runtime candidate queue를 저장소/권한/배포 단위로 분리한다. `golden_dataset.jsonl`에는 `source`, `created_at`, `leakage_risk`, `knowledge_snapshot_id`를 추가한다. candidate promotion은 reviewer, diff, source evidence, eval before/after를 반드시 남긴다.
- 우선순위: Critical

---

## 3. 모델 설계 문제

- 문제점: 자체 모델 architecture, training loop, loss, regularization은 없다. 실제 모델 설계라고 부를 만한 부분은 Ollama의 `qwen3:1.7b` / fallback `qwen3:4b-q4_K_M` 호출, prompt, RAG context, validation/fallback gate 조합이다.
- 왜 문제인지: 이것을 "모델을 설계했다"라고 말하면 부정확하다. 실무적으로는 모델 설계가 아니라 inference orchestration과 answer quality control이다. 또한 `validate_answer_node`, `confidence_gate_node`, `fallback_answer_node`는 한국어 포함 여부, 필수 키워드, 금칙 claim, relevance를 휴리스틱으로 판단한다. 의미적 정답성이나 hallucination을 충분히 검증하지 않는다.
- 실제 운영에서 발생 가능한 문제: 사용자는 그럴듯하지만 틀린 답을 받을 수 있다. fallback gate가 너무 강하면 LLM이 맞춘 답도 template 답변으로 덮어써져 품질이 낮아지고, 너무 약하면 hallucination이 cache와 candidate queue에 남는다.
- 개선 방법: 문서와 포트폴리오에서는 "모델 설계" 대신 "LLM/RAG inference workflow 설계"로 명명한다. Semantic evaluator를 실제로 활성화할 계획이면 structured judge output, contradiction check, evidence-grounded answer check를 별도 평가셋으로 검증한다.
- 우선순위: High

---

## 4. 추론 안정성 문제

- 문제점: sync와 streaming 추론 경로가 완전히 같은 runtime 특성을 갖지 않는다. `run_review_workflow()`는 `run_single_flight(cache_key, run_workflow)`를 사용하지만, `run_review_workflow_stream()`은 cache hit/fast-path 이후 실제 stream generation 구간에는 single-flight 병합이 없다. Ollama timeout도 Python 기본값 `OLLAMA_REQUEST_TIMEOUT_SECONDS=0`이면 무한 대기가 된다.
- 왜 문제인지: streaming은 사용자 체감 first-token을 개선하지만 총 생성 부하를 줄이지 않는다. 특히 노트북 CPU 환경에서는 모델 generation이 CPU-bound로 길게 잡히고, 같은 질문이 동시에 들어오면 sync는 병합되지만 stream은 개별 연결과 generation lifecycle을 유지할 수 있다.
- 실제 운영에서 발생 가능한 문제: 특정 모델이 hang되면 Spring stream timeout 45초가 끊더라도 Python/Ollama generation이 즉시 취소되지 않을 수 있다. 사용자는 partial answer를 보고 이탈하고, 서버는 뒤에서 생성 작업을 계속 들고 있어 capacity가 잠긴다. 노트북에서는 이 문제가 "느리다"로 보이지만, 운영에서는 worker starvation과 장애 전파로 보인다.
- 개선 방법: Python streaming에도 in-flight/queue 정책을 명시한다. request disconnect 시 upstream Ollama cancellation이 실제로 되는지 별도 테스트와 metric을 추가한다. `OLLAMA_REQUEST_TIMEOUT_SECONDS`는 운영에서 0 금지로 두고, stream timeout, Ollama timeout, Spring timeout의 관계를 문서화한다. FastAPI `@app.on_event`는 lifespan으로 교체한다.
- 우선순위: Critical

---

## 5. 배포 위험성

- 문제점: Python optional dependency가 무겁고 깨지기 쉽다. `requirements-rag.txt`는 Chroma, sentence-transformers, flashrank, kiwipiepy를 포함하고, 문서에도 Windows Build Tools 이슈가 적혀 있다. 운영 설정도 `application.yml` 기본값에 로컬 MySQL, 로컬 Ollama, 빈 service token, `ddl-auto=update`, `show-sql=true`가 남아 있다.
- 왜 문제인지: 노트북 개발에서는 로컬 MySQL, 로컬 Ollama, 빈 token, 파일 기반 JSONL이 빠르게 실험하기 좋다. 하지만 이 설정이 prod/profile 경계 없이 남으면 배포 사고로 이어진다. `application-prod.yml`은 DB/JPA 일부만 덮고 AI 설정을 명시적으로 잠그지 않는다.
- 실제 운영에서 발생 가능한 문제: prod profile 누락 또는 env 누락으로 schema가 자동 변경되거나, AI service boundary가 무인증으로 열리거나, RAG optional dependency 설치 실패로 high-performance retriever가 조용히 lexical fallback된다. Chroma persist directory가 repo 내부에 있으면 배포 산출물과 runtime state가 섞인다.
- 개선 방법: dev/prod 설정을 분리하고 prod에서는 service token 필수, AI provider 명시, streaming flag 명시, Ollama timeout 명시, `ddl-auto=validate` 강제, show-sql false를 검증하는 startup validation을 둔다. Python은 lockfile 또는 constraints를 도입하고, RAG heavy profile은 별도 image/profile로 분리한다.
- 우선순위: High

---

## 6. 실험 설계 및 재현성 부족점

- 문제점: 실험 추적 체계가 약하다. golden evaluator는 있으나 69개 row 수준이고, model version, prompt version, knowledge index version, retriever profile, seed, temperature, dependency hash가 하나의 experiment record로 묶이지 않는다.
- 왜 문제인지: 노트북 환경에서는 백그라운드 프로세스, 배터리/전원 모드, RAM 압박, 발열 throttling, Ollama warm/cold state에 따라 latency가 크게 흔들린다. 제공된 화면처럼 RAM이 75% 수준이면 같은 코드라도 실행 시점마다 결과가 달라질 수 있다. 이런 값은 재현 가능한 벤치마크가 아니다.
- 실제 운영에서 발생 가능한 문제: "latency가 좋아졌다", "cache hit가 올랐다", "first-token 860ms" 같은 주장을 재현하지 못한다. 버그가 생겨도 어느 knowledge snapshot, prompt version, model setting, hardware state에서 발생했는지 역추적이 어렵다.
- 개선 방법: evaluation run artifact를 JSON으로 남긴다. 필수 필드는 `git_sha`, `prompt_version`, `knowledge_manifest_hash`, `model`, `temperature`, `retriever_profile`, `dataset_version`, `hardware_profile`, `power_mode`, `run_started_at`, `metrics`다. 문서의 수치는 반드시 이 artifact 링크나 파일 경로를 근거로만 적는다.
- 우선순위: High

---

## 7. 성능 병목

- 문제점: 병목은 모델이 아니라 local Ollama generation과 장기 연결이다. Python `call_ollama`와 `call_ollama_stream_async`는 `_GENERATION_SEMAPHORE`로 기본 동시 generation을 1개로 제한한다. 제공된 노트북 사양은 i5-10210U 4코어/8스레드, 16GB RAM, 내장 GPU 수준이라 로컬 LLM에는 빡빡하다.
- 왜 문제인지: semaphore 1은 노트북 안정성에는 필요하지만 throughput에는 매우 불리하다. Fast-path/cache가 miss되는 질문이 몰리면 모든 사용자가 queue 뒤에 선다. streaming은 화면에 chunk를 보여줄 뿐 전체 CPU 점유 시간은 유지한다. 내장 GPU는 일반적으로 Ollama LLM 추론 가속에 큰 도움이 되지 않는다.
- 실제 운영에서 발생 가능한 문제: 노트북에서는 "P95 latency 개선"이 fast-path/cache 덕분인지, CPU 상태/열/메모리 상태 때문인지 분리하기 어렵다. 운영에서 동시 사용자 30명 중 fast-path가 안 맞는 질문이 몰리면 첫 토큰은 일부 빨라져도 전체 완료 시간은 급격히 길어진다.
- 개선 방법: route별 latency, queue wait time, generation time, first-token time을 분리해서 측정한다. 노트북 벤치마크는 `dev-laptop` profile로만 표기하고, 운영 주장은 별도 고정 서버 또는 containerized benchmark에서 다시 측정한다. Ollama 인스턴스 다중화, model pool, per-route max concurrency, admission queue metric을 추가한다.
- 우선순위: High

---

## 8. 확장성 문제

- 문제점: multi-GPU, distributed inference, large data indexing 전략은 없다. 현재 구조는 단일 Python AI 서버, 단일 Ollama endpoint, 로컬 파일 기반 knowledge/candidate/cache, optional local Chroma persist 경로에 가깝다.
- 왜 문제인지: 단일 노트북 환경에서는 단순하지만 scale-out 시 상태 공유가 깨진다. answer cache는 process-local LRU + optional JSONL이고, auto candidate append/update는 파일 기반이다. 여러 Python replica가 뜨면 cache hit, candidate update, vector index 상태가 replica마다 달라질 수 있다.
- 실제 운영에서 발생 가능한 문제: replica A가 생성한 candidate를 replica B가 모른다. 같은 질문이 replica별로 중복 생성된다. JSONL write 경쟁으로 candidate file이 깨지거나 update lost가 생긴다. Chroma auto-index 상태가 replica마다 달라져 retrieval 결과가 흔들린다.
- 개선 방법: production에서는 answer cache를 Redis 등 중앙 cache로 옮기고, candidate queue는 DB 또는 durable queue로 이동한다. vectorstore는 build artifact와 runtime query store를 분리하고, index version을 immutable하게 배포한다. Ollama는 routing 가능한 inference gateway 뒤에 두고 per-model capacity를 관리한다.
- 우선순위: Critical

---

## 9. 유지보수 및 기술 부채 위험

- 문제점: 인코딩 깨짐이 광범위하게 보인다. 일부 Java 예외 메시지, 문서, RAG tokenizer의 한국어 상수/정규식/section key가 깨진 형태로 남아 있다. 또한 `docs/26.05.21 AI시스템 코드 분석 자료.md`와 `docs/ai-review-local-rag-langgraph-architecture-final.md`는 과거 상태와 현재 패치 상태가 섞여 있다.
- 왜 문제인지: 깨진 한글은 단순 미관 문제가 아니다. fallback 메시지, 사용자 에러 메시지, token stopword, keyword section lookup이 깨지면 사용자가 보는 품질과 retrieval 점수가 직접 나빠진다. 노트북/Windows/PowerShell 환경에서 `Set-Content`나 콘솔 codepage가 섞인 흔적이 있고, 이는 재발 가능성이 높다.
- 실제 운영에서 발생 가능한 문제: 사용자가 오류 상황에서 깨진 문자를 본다. RAG tokenizer가 한국어 토큰을 제대로 못 잡아 검색 miss가 늘어난다. 문서는 "stub context 보강 필요"라고 말하는데 코드는 이미 보강되어 있어, 팀원이 잘못된 작업을 다시 한다.
- 개선 방법: 전체 repo에 UTF-8 인코딩 검사를 넣고, 깨진 문자열을 우선 복구한다. PowerShell 파일 쓰기 작업은 금지하거나 `-Encoding utf8`을 강제한다. 문서는 "구현됨", "부분 구현", "미구현", "실측 미확인" 상태를 날짜와 함께 표준화한다. 오래된 설계 문서는 archive 처리하거나 상단에 superseded 문서를 링크한다.
- 우선순위: High

---

## 10. 더 발전 가능한 방향

- 문제점: 현재 발전 방향은 기능 추가보다 운영 신뢰성 보강이 먼저다. Streaming, RAG, candidate loop, observability가 각각 존재하지만 SLO, alert, rollback, replay, offline eval, production sampling으로 닫힌 루프가 아직 약하다.
- 왜 문제인지: 프로덕션 AI 시스템은 "답이 나온다"에서 끝나지 않는다. 틀린 답이 cache에 들어가고, candidate로 승격되고, 문서 지표로 포장되는 순간 품질 부채가 누적된다. 노트북 제약을 극복하기 위해 fast-path/cache/경량 모델을 잘 쓴 점은 합리적이지만, 운영 품질 주장에는 별도 검증 체계가 필요하다.
- 실제 운영에서 발생 가능한 문제: 특정 topic에서 hallucination이 늘어나도 dashboard가 없어 늦게 발견한다. fallback이 증가해도 사용자 불만으로만 감지한다. candidate 승인 후 성능이 좋아졌는지 나빠졌는지 실험 근거가 남지 않는다.
- 개선 방법: 1순위는 metrics backend다. `AiReviewMetricSink`의 구조화 로그를 Micrometer/Prometheus 또는 OpenTelemetry metric으로 승격하고, `first_token_latency_ms`, `stream_completed`, `stream_disconnected`, `stream_partial_failed`, `fallback_to_sync_count`, `cache_hit`, `llm_call_avoided`, `candidate_captured`를 dashboard와 alert로 연결한다. 2순위는 eval pipeline이다. golden set을 최소 200~500개로 늘리고, hallucination/evidence-grounding judge와 production sampling review를 붙인다. 3순위는 runtime state 외부화다. cache, candidate queue, vector index를 파일에서 운영용 저장소로 분리한다.
- 우선순위: Critical

---

## 문서-코드 불일치 및 노트북 환경 리스크 요약

| 항목 | 현재 판단 | 근거 / 리스크 |
|:---|:---|:---|
| Streaming 구현 | 구현됨 | `ai/app/main.py`, `ai/app/workflow/runner.py`, `PythonAiReviewClient.streamReview`, `AiReviewStreamingService` |
| Streaming 컨텍스트 stub | 오래된 문서 표현 | 최신 `AiReviewStreamingService`는 session/wrong answer/current question 기반으로 question/correct/selected를 구성한다. 과거 문서의 stub 지적은 업데이트 필요 |
| First-token 860ms | 실측 근거 부족 | metric 로그는 추가됐지만, benchmark artifact는 확인되지 않음. 노트북 수치라면 운영 수치로 쓰면 안 됨 |
| Prometheus/Grafana | 미구현 | `AiReviewMetricSink`는 구조화 `log.info` 중심. Micrometer counter/exporter/dashboard wiring 없음 |
| Circuit Breaker | 부정확한 명칭 | retry + graceful fallback은 있으나 Open/Half-Open/Closed 상태 전이 없음 |
| Train/validation/test split | 해당 없음 | 자체 모델 학습 코드가 발견되지 않음 |
| LangGraph | optional | `LANGGRAPH_AVAILABLE`이 dependency 설치 여부에 좌우됨. 미설치 시 sequential fallback |
| High-performance RAG | optional/위험 | Chroma/SentenceTransformer는 optional이고 예외 발생 시 조용히 빈 결과/fallback 가능 |
| 노트북 성능 수치 | 개발 참고값 | i5-10210U/16GB/내장 GPU 환경에서는 발열, 전원 모드, RAM 압박에 따라 latency 변동이 큼 |
| 인코딩 안정성 | 위험 | 깨진 한글 문서/코드 문자열이 발견됨. Windows PowerShell 기반 작업에서 재발 가능 |

---

## 최우선 액션

1. `auto_candidates.jsonl`, generated cards, vectorstore를 운영 runtime state와 repo artifact로 분리한다.
2. Python streaming generation의 timeout/cancellation/single-flight 정책을 명확히 정하고 테스트한다.
3. 노트북 벤치마크와 운영 벤치마크를 분리한다. 포트폴리오 수치는 `dev-laptop` 기준인지 운영 유사 환경 기준인지 명시한다.
4. 깨진 한글 문자열과 오래된 문서를 정리한다.
5. log-only metric을 실제 metric backend와 dashboard로 연결한다.
6. golden dataset과 evaluation artifact를 늘려 모든 포트폴리오 수치를 재현 가능하게 만든다.

