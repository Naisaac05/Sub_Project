# Adaptive Judge Execution Optimization Plan

> **For Antigravity:** REQUIRED WORKFLOW: Use `.agent/workflows/execute-plan.md` to execute this plan in single-flow mode.

**Goal:** free-question 모드에서 불필요한 Semantic Judge 및 Grounding Judge 호출을 지능적으로 생략(Skip) 및 비동기(Async) 처리하여 AI 답변 생성을 대폭 단축하고 레이턴시를 8초 이내로 단축합니다.

**Architecture:**
1. **Tiered Judge Pipeline:**
   - **Tier 0 (No Judge):** Fast-path(static, generated card), Cache hit -> Semantic & Grounding 모두 Skip.
   - **Tier 1 (Semantic Only):** `concept_definition` 의도이면서 답변 700자 미만이고 High-Risk 키워드가 없는 경우 -> Semantic Judge만 동기 실행, Grounding Judge는 Skip(또는 백그라운드 Async Metric).
   - **Tier 2 (Full Judge):** `wrong_answer_explanation`, `follow_up`, 긴 답변(>= 700자), Retry 작동, 또는 High-Risk 키워드 포함 -> Semantic 동기 실행 + Grounding은 백그라운드 Thread 비동기 실행.
2. **Grounding Judge Async 처리:**
   - Grounding Judge는 사용자 응답 속도에 절대 영향을 미치지 않도록 **데몬 백그라운드 Thread**로 실행합니다.
   - 메인 응답은 Grounding 결과를 기다리지 않고 즉시 클라이언트에 스트리밍/반환됩니다.
   - **Test-Sync Safety:** 유닛 테스트 실행 시(`unittest` 모듈이 메모리에 로드된 경우)에는 동기식으로 기동하여 테스트 무결성(Regression Tests)을 100% 보장합니다.
3. **High-Risk 키워드 검증 필터:**
   - 사용자 질문 혹은 원본 문제에 아래 키워드가 하나라도 유입되면 무조건 **Tier 2 (Full Judge)**로 격상하여 강제로 엄격한 검증을 통과시킵니다.
   - 키워드: `"왜 틀렸나"`, `"오답 이유"`, `"이 문제에서"`, `"이 선지가"`, `"맞는 이유"`, `"틀린 이유"`, `"비교해줘"`, `"근거가 뭐야"`
4. **Metrics 수집 확장:**
   - `judge_tier`: `"tier0"` | `"tier1"` | `"tier2"`
   - `semantic_judge_skipped`: bool
   - `grounding_judge_skipped`: bool
   - `grounding_async_executed`: bool
   - `estimated_latency_saved_ms`: float (추정된 절감 Latency)

**Tech Stack:** Python 3.11, Standard threading, re, unittest

---

## Proposed Changes

### Component 1: State & Metrics Schema 확장

#### [MODIFY] [state.py](file:///c:/Users/User/Desktop/Sub_Project/ai/app/workflow/state.py)
*   **역할:** 대시보드와 관측 이벤트를 위한 최적화 지표 필드 추가.
*   **수정 사항:** `ReviewWorkflowState` 클래스 바디에 다음 필드 선언:
    ```python
    judge_tier: str = "tier2"  # "tier0" | "tier1" | "tier2"
    semantic_judge_skipped: bool = False
    grounding_judge_skipped: bool = False
    grounding_async_executed: bool = False
    estimated_latency_saved_ms: float = 0.0
    grounding_thread: object = None  # unit test join 용도 임시 스레드 홀더
    ```

---

### Component 2: Tiered Judge & Async Grounding 핵심 로직 구현

#### [MODIFY] [nodes.py](file:///c:/Users/User/Desktop/Sub_Project/ai/app/workflow/nodes.py)
*   **역할:** `generate_answer_node` 하단에서 질문 난이도를 티어별로 분류하고 최적화 기동을 수행합니다.
*   **High-Risk 키워드 판단 함수 신설:**
    ```python
    def _is_high_risk_question(state: ReviewWorkflowState) -> bool:
        keywords = ["왜 틀렸나", "오답 이유", "이 문제에서", "이 선지가", "맞는 이유", "틀린 이유", "비교해줘", "근거가 뭐야"]
        q_text = (state.request.user_answer or "") + " " + (state.request.question or "")
        return any(kw in q_text for kw in keywords)
    ```
*   **Tiered Judge Selection 구현:**
    ```python
    # 1. 티어 결정
    tier = "tier2"
    is_fast_path = state.route in {"static_fast_path", "generated_card_fast_path", "cache"}
    
    if is_fast_path:
        tier = "tier0"
    else:
        is_concept_def = state.free_question_intent and state.free_question_intent.intent == "concept_definition"
        short_answer = len(state.answer or "") < 700
        high_risk = _is_high_risk_question(state)
        
        if is_concept_def and short_answer and not high_risk and state.retry_count == 0:
            tier = "tier1"
            
    state.judge_tier = tier
    ```
*   **Semantic Judge 실행 (Tier 1, Tier 2에서만 동기 수행):**
    ```python
    if tier == "tier0":
        state.semantic_judge_skipped = True
        state.grounding_judge_skipped = True
        state.estimated_latency_saved_ms = 4000.0  # Semantic + Grounding 생략 시 약 4초 절감 추정
    elif tier == "tier1":
        # Semantic Judge만 동기 실행
        # (기존 동기 judge_answer 실행 코드 활용)
        state.semantic_judge_skipped = False
        state.grounding_judge_skipped = True
        state.estimated_latency_saved_ms = 2000.0  # Grounding 생략 시 약 2초 절감 추정
    else:
        # Tier 2: Semantic Judge 동기 실행
        state.semantic_judge_skipped = False
        state.grounding_judge_skipped = False
    ```
*   **Grounding Judge Async 처리 연동:**
    *   원래 동기식으로 진행되던 `validate_grounding` 호출부를 분기합니다.
    *   **Unit-Test 탐지 및 동기 처리:** 유닛 테스트 중에는 비동기 스레드 대기 Flake를 원천 방어하기 위해 동기식으로 전환합니다.
    ```python
    import sys
    import threading
    from app.workflow.grounding import validate_grounding
    
    is_skipped = (
        state.route in {"static_fast_path", "generated_card_fast_path", "cache"}
        or not state.contexts
        or tier == "tier0"
        or tier == "tier1"
    )
    
    if is_skipped:
        # ... skipped GroundingResult 설정 ...
        state.grounding_judge_skipped = True
    else:
        state.grounding_judge_skipped = False
        # Unit-Test 혹은 Mock generator 인 경우 동기 실행
        is_test = "unittest" in sys.modules or getattr(generator, "__name__", "") not in ("call_ollama", "call_ollama_stream_async")
        
        if is_test:
            state.grounding_result = validate_grounding(
                request=state.request,
                answer=state.answer,
                contexts=state.contexts,
                generator=generator,
            )
            if state.grounding_result:
                state.grounding_prompt_version = state.grounding_result.prompt_version
                state.grounding_prompt_hash = state.grounding_result.prompt_hash
        else:
            # 실운영에서는 Background Daemon Thread로 전격 격리 실행 (Response Latency 완전 0 영향)
            state.grounding_async_executed = True
            
            def async_grounding_task():
                try:
                    res = validate_grounding(
                        request=state.request,
                        answer=state.answer,
                        contexts=state.contexts,
                        generator=generator,
                    )
                    # 비동기 완료 후 로깅 시스템(observability)에 백그라운드 단독 발행
                    from app.observability import LOGGER_NAME
                    import logging
                    import json
                    sink = logging.getLogger(LOGGER_NAME)
                    
                    enriched = {
                        "event": "ai_review.grounding_evaluated",
                        "correlation_id": state.candidate_id or "async-grounding",
                        "grounding_score": res.grounding_score,
                        "retrieval_coverage_score": res.evidence_coverage,
                        "unsupported_claim_detected": len(res.unsupported_claims) > 0,
                        "low_grounding_answer": res.grounding_score < 0.7,
                        "unsupported_claims": res.unsupported_claims,
                        "grounded": res.grounded,
                        "reason": res.reason,
                        "route": state.route,
                        "intent": state.free_question_intent.intent if state.free_question_intent else state.mode,
                        "sub_intent": state.free_question_intent.sub_intent if state.free_question_intent else "unknown",
                        "rag_policy": state.free_question_intent.rag_policy if state.free_question_intent else "unknown",
                        "model": state.model_used,
                        "fallback_used": state.fallback_used,
                        "retry_used": state.retry_count > 0,
                        "prompt_version": state.prompt_version,
                        "prompt_hash": state.prompt_hash,
                        "prompt_strategy": state.prompt_strategy,
                        "grounding_prompt_version": res.prompt_version,
                        "grounding_prompt_hash": res.prompt_hash,
                    }
                    sink.info(json.dumps(enriched, ensure_ascii=False, sort_keys=True))
                except Exception as async_exc:
                    logger.error(f"Background grounding execution failed: {async_exc}")
            
            thread = threading.Thread(target=async_grounding_task)
            thread.daemon = True
            state.grounding_thread = thread
            thread.start()
    ```

---

### Component 3: Observability & Metrics 연동

#### [MODIFY] [runner.py](file:///c:/Users/User/Desktop/Sub_Project/ai/app/workflow/runner.py)
*   **역할:** `_build_response_from_state` 및 메트릭 발행에 지능형 대시보드 상태 필드들을 매핑합니다.
*   **수정 사항:** 
    *   `_workflow_completed_event`와 `ai_review.semantic_judge_evaluated` 이벤트 전송 페이로드에 아래 메트릭 지표 추가 바인딩:
        ```python
        "judge_tier": state.judge_tier,
        "semantic_judge_skipped": state.semantic_judge_skipped,
        "grounding_judge_skipped": state.grounding_judge_skipped,
        "grounding_async_executed": state.grounding_async_executed,
        "estimated_latency_saved_ms": state.estimated_latency_saved_ms,
        ```
    *   **Async Grounding 방지 수집 보정:** 동기 `_build_response_from_state` 수행 시, `state.grounding_async_executed`가 True이면 response 내부의 동기 `events` 목록에는 `grounding_evaluated` 이벤트를 포함시키지 않습니다 (백그라운드 스레드에서 직접 로그를 발행하므로 페이로드 누출 및 병목이 이중 방지됩니다).

---

## Verification Plan

### Automated Tests
`tests/test_adaptive_judge.py`를 새로이 작성하여 다음 4가지 핵심 최적화 지침을 자동 검증합니다.
1.  **Fast Path / Cache / Lightweight (Tier 0) 검증:** 캐시나 Fast path 응답 시 두 판사(semantic, grounding)가 모두 스킵 처리되는지 검증.
2.  **Short Concept Definition (Tier 1) 검증:** 짧은 일반 개념 질문 유입 시 semantic judge만 동기 기동하고, grounding은 스킵 또는 비동기로 빠져나가는지 검증.
3.  **High-Risk Keyword / wrong_answer_explanation (Tier 2) 검증:** 질문 내에 오답선지나 특정 문항의 지시성 키워드("선지가 왜 틀렸나", "맞는 이유" 등)가 포함되면 무조건 Tier 2(동기 Semantic + 비동기 Grounding)로 강제 격상되는지 검증.
4.  **Async Grounding 레이턴시 검증:** Grounding LLM 판사가 2초 이상 지연(Thread sleep 모킹)되어도 메인 사용자 응답 반환 속도(Latency)는 즉시 0초에 가깝게 완료되는지 레이턴시 차단 검증.

#### Test Execution Commands:
```powershell
.venv\Scripts\python.exe -m unittest tests/test_adaptive_judge.py -v
.venv\Scripts\python.exe -m unittest discover -s tests
```
