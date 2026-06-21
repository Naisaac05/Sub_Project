---
type: spec
category: evaluation
status: active
updated: 2026-06-18
description: "AI Review Streaming, Evaluation, and Summary Spikes 상세 요구사항 및 기능 동작 명세서"

---

# AI Review Streaming, Evaluation, and Summary Spikes

작성일: 2026-05-21
상태: design spike

## Spike 요약 (한 눈에 보기)

- **목적**: 사용자 체감 지연 감소 + 평가/요약 품질 향상
- **철학**: 기존 rule-based와 Spring Markdown builder를 강력한 fallback으로 유지하면서, 선택적으로 LLM 기능을 도입한다.
- **위험 관리**: 모든 신규 경로는 Feature Flag + Fallback + Golden Dataset 회귀 테스트를 필수로 적용한다.
- **우선순위**: Streaming (체감) -> Semantic Evaluation (정확도) -> Python Summary (품질)

## 목적

이번 확장성 작업은 비용 효율 순서대로 LLM 호출 수 감축, single-flight/cache, admission gate, observability까지 먼저 구현했다. 남은 사용자 체감 품질 과제는 streaming response와 semantic/LLM 기반 평가·요약 고도화다.

이 문서는 현재 코드 기준으로 다음 구현 경계를 정리한다.

## 1. Streaming Response Spike

현재 Python AI 경로는 `ai/app/ollama/client.py`에서 Ollama `/api/generate`를 `stream: False`로 호출한다. 이 구조는 전체 생성이 끝난 뒤에만 Spring으로 응답을 돌려주므로, 20~80초 지연 상황에서 사용자는 멈춘 것처럼 느낀다.

### Trade-off

Streaming은 first-token latency를 크게 개선하지만, 총 생성 연산량 자체를 줄이지는 않는다. Ollama stream 모드에서는 연결이 더 오래 유지되고 `keep_alive`와 GPU 메모리 유지 시간이 늘어날 수 있다. CPU 환경에서는 chunk 전송과 연결 유지 비용 때문에 전체 throughput이 약간 떨어질 가능성도 있다.

따라서 streaming은 "처리량 개선"이 아니라 "기다리는 느낌 개선" 레버로 분류하고, admission gate와 timeout/fallback 정책을 그대로 적용해야 한다.

### 권장 방향

- 단기: 기존 synchronous endpoint는 유지하고, 별도 streaming endpoint를 추가한다.
- Python FastAPI: `StreamingResponse`로 Ollama stream JSONL token을 받아 그대로 또는 normalized event로 흘린다.
- Spring: 기존 `PythonAiReviewClient`와 별도로 SSE client를 1순위로 둔다. job queue + polling은 브라우저/프록시/SSE 운영 제약이 확인될 때 2순위로 고려한다.
- Frontend: AI chat message를 placeholder로 먼저 만들고 token chunk를 append한다. 구현 방식은 `EventSource` 또는 `fetch` + `ReadableStream` 중 현재 인증/헤더 요구사항에 맞춰 선택한다.
- fallback: streaming 경로 실패 시 기존 non-streaming `/api/review/*` endpoint로 fallback한다.

### 구현 순서

1. Python `call_ollama_stream()` 추가: `stream: True`, chunk parser, timeout 적용.
2. Python `/api/review/follow-up/stream` 추가: free-question보다 사용 빈도가 높은 follow-up부터 제한적으로 적용한다.
3. Spring streaming adapter 추가: SSE를 그대로 relay하거나 job id polling으로 분리.
4. Frontend streaming renderer 추가: partial answer, cancel, retry 상태 처리.
5. metrics: `stream_started`, `first_token_latency_ms`, `stream_completed`, `stream_fallback_used`.

### 주의점

- streaming은 총 연산량을 줄이지 않는다. 체감 지연을 줄이는 기능이다.
- admission gate는 streaming endpoint에도 동일하게 적용해야 한다.
- candidate capture는 stream 완료 후 final answer 기준으로만 수행한다.
- partial answer 상태에서 사용자가 cancel 버튼을 누를 때 Ollama generation이 실제로 중단되는지 별도 검증이 필요하다. Ollama cancellation은 약할 수 있으므로, UI cancel은 우선 "클라이언트 표시 중단"으로 정의하고 서버 측 generation 중단 여부는 별도 metric으로 관측한다.
- streaming 중 fallback이 발생하면 이미 전달된 partial token을 어떻게 처리할지 정책이 필요하다. 권장 기본값은 partial message를 `interrupted` 상태로 닫고, fallback answer를 별도 final message로 append하는 것이다.

## 2. Semantic/LLM Evaluation Spike

현재 Spring `RuleBasedAiReviewService.evaluateAnswer()`는 길이와 keyword 포함 여부 중심이다. 사용자가 의미상 맞게 설명했지만 정답 keyword가 다르면 `NEEDS_REVIEW`로 흐를 수 있고, 반대로 keyword만 포함해도 이해한 것으로 판단될 수 있다.

### 권장 방향

- 기존 rule evaluator는 fallback으로 유지한다.
- `AI_REVIEW_SEMANTIC_EVALUATION_ENABLED`가 켜진 경우에만 semantic/LLM evaluator를 사용한다.
- 실패, timeout, low confidence일 때 rule evaluator로 fallback한다.
- 출력은 반드시 구조화한다: `{ evaluation, confidence, reasons, missing_concepts }`.
- 정량 목표: golden set 120개 기준 semantic evaluator 정확도 88% 이상, rule-based 대비 +15%p 개선을 목표로 한다.

### 평가 입력

- original question
- correct answer
- selected answer
- learner answer
- current concept card context when available
- previous evaluation state

### 판정 정책

- confidence가 낮으면 `PARTIAL` 또는 기존 rule fallback을 우선한다.
- LLM 판정이 `UNDERSTOOD`라도 answer relevance가 낮거나 contradiction이 있으면 승격하지 않는다.
- golden dataset에 `intent/topic/evaluation`을 같이 넣어 회귀 테스트한다.
- LLM timeout / error / low confidence (`confidence < 0.7`) -> rule-based fallback.
- LLM이 `UNDERSTOOD`를 줬으나 keyword relevance가 `0.4` 미만이면 `PARTIAL`로 downgrade한다.

## 3. AI Summary Spike

현재 문제별/전체 요약은 Spring Markdown builder 중심이다. 구조는 안정적이지만 특정 topic hard-coding과 rule 기반 문장 조합이 남아 있어, 요약 품질이 문제 유형에 따라 들쭉날쭉하다.

Summary는 학습 보조 자료 성격이 강하므로 latency보다 일관성과 hallucination 방지를 우선시한다. timeout은 8~12초 정도로 짧게 잡고, fallback 비율은 15% 이하로 유지하는 것을 목표로 한다.

### 권장 방향

- 기존 Spring Markdown builder는 최종 fallback으로 유지한다.
- 선택적 Python summary endpoint를 추가한다.
- Python summary는 RAG context와 세션 메시지를 받아 Markdown schema를 채우되, 섹션 이름은 Spring과 동일하게 유지한다.
- 실패 시 기존 `buildQuestionStudySummary()` / `buildOverallStudyReport()`로 fallback한다.

### 구현 순서

1. Summary request/response DTO를 Python에 추가한다.
2. Spring summary builder 앞에 optional Python call을 둔다.
3. timeout은 짧게 둔다. 요약은 학습 보조 기능이므로 긴 대기보다 fallback이 낫다.
4. golden summary set을 만들고 section coverage, hallucination flag, Korean ratio를 검사한다.

## 4. 운영 지표

이번 구현으로 `ai_review.observability` 이벤트에는 `cache_hit`, `llm_call_avoided`, `model_used`, `latency_ms`, `fallback_used`, `retrieval_miss`, `candidate_captured`를 남길 수 있다. 다음 단계에서는 아래 지표를 dashboard로 묶는다.

| 지표 | 목적 |
|---|---|
| p50/p95 latency | 사용자 체감 지연 확인 |
| first token latency | streaming 체감 개선 확인 |
| cache hit rate | LLM 호출 감축 효과 확인 |
| llm call avoided rate | fast-path/cache 효과 확인 |
| admission in-flight / available | backpressure 기준 확인 |
| fallback rate | timeout, 품질 gate, 모델 실패 추적 |
| retrieval miss rate | RAG coverage 부족 확인 |
| tokens/request | 비용과 latency 원인 추적 |
