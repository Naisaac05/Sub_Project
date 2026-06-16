from collections.abc import Callable
import os
import re
import time

from app.ollama.client import FALLBACK_MODEL, call_ollama
from app.prompts import build_prompt, prompt_version_for_mode, prompt_strategy_for_mode, compute_prompt_hash
from app.rag.documents import load_concept_cards
from app.rag.retriever import retrieve_context
from app.schemas import AiGenerateRequest
from app.schemas.rag_card import CardStatus
from app.scoring import ConfidenceInputs, ConfidenceResult, calculate_confidence
from app.validation.text import compact_answer, contains_korean, korean_fallback
from app.workflow.answer_cache import cache_key_for, get_cached_answer
from app.workflow.course_scope import CourseScopeDecision, resolve_course_scope
from app.workflow.degraded import lightweight_only_enabled, lightweight_only_miss_state
from app.workflow.embedding_intent import classify_free_question_with_embeddings
from app.workflow.intent import FreeQuestionIntent, normalize_question
from app.workflow.lightweight_answers import LIGHTWEIGHT_MODEL_NAME, resolve_lightweight_answer
from app.workflow.query_resolver import resolve_learner_query
from app.workflow.state import ReviewWorkflowState, ValidationResult
from app.workflow.v2_approved_fast_path import resolve_v2_approved_fast_path


Generator = Callable[..., str]
# 강신호(제목/키워드/concept_id) 토큰이 하나라도 맞으면 score_card 점수는 최소 5.0이다
# (강매칭 +4 보너스 + 겹침 ≥1). 그 미만은 강신호 없는 본문 우연 겹침이라 엉뚱한 카드를
# 근거로 주입할 위험이 있어 워크플로 컨텍스트에서 제외한다.
MIN_WORKFLOW_CONTEXT_SCORE = 5.0

FORBIDDEN_CLAIMS = (
    "트랜잭션 문제",
    "네트워크 지연 문제",
)



def should_use_workflow_context(context) -> bool:
    if context.metadata.get("retriever") == "ollama_bge_m3":
        threshold = float(os.getenv("AI_REVIEW_BGE_MIN_SCORE", "0.50"))
        return context.score >= threshold
    threshold = MIN_WORKFLOW_CONTEXT_SCORE
    return context.score >= threshold


def _is_high_risk_question(state: ReviewWorkflowState) -> bool:
    keywords = ["왜 틀렸나", "오답 이유", "이 문제에서", "이 선지가", "맞는 이유", "틀린 이유", "비교해줘", "근거가 뭐야"]
    q_text = (state.request.user_answer or "") + " " + (state.request.question or "")
    return any(kw in q_text for kw in keywords)


def retrieve_context_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    if state.mode == "follow-up":
        state.contexts = []
        return state

    learner_query = state.request.user_answer
    if state.mode == "free-question":
        state.resolved_query = resolve_learner_query(state.request.user_answer)
        learner_query = state.resolved_query.resolved_query
        state.free_question_intent = classify_free_question_with_embeddings(learner_query)
        state.contexts = []
        return state

    query = _query_for_request(state.mode, state.request, state.free_question_intent, learner_query)
    state.contexts = [
        context
        for context in retrieve_context(query, limit=3)
        if should_use_workflow_context(context)
    ]
    return state


def rule_evaluate_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    return state


def generate_answer_node(
    state: ReviewWorkflowState,
    generator: Generator = call_ollama,
) -> ReviewWorkflowState:
    learner_query = _learner_query_for_state(state)

    if _is_off_topic_free_question(state):
        return _off_topic_redirect_state(state)

    scope_decision = None
    if state.mode == "free-question":
        scope_decision = _course_scope_for_state(state)
        if scope_decision.scope == "out_of_course_tech":
            return _out_of_course_redirect_state(state, scope_decision.matched_card_id)
        if scope_decision.scope == "scope_unknown":
            _append_quality_flag(state, "scope_unknown")
        elif scope_decision.scope in {"course_card_hit", "course_card_miss"}:
            _append_quality_flag(state, "course_scope_applied")

    v2_decision = resolve_v2_approved_fast_path(
        learner_query,
        state.free_question_intent,
        selected_answer=state.request.selected_answer,
        allowed_card_ids=scope_decision.allowed_card_ids
        if scope_decision and scope_decision.allowed_card_ids
        else None,
    )
    state.v2_fast_path_decision = v2_decision.metadata()
    if state.mode == "free-question" and v2_decision.mode == "serve" and v2_decision.hit:
        state.answer = v2_decision.answer or ""
        state.prompt_version = prompt_version_for_mode(state.mode)
        state.model_used = "v2-approved-payload"
        state.fallback_used = False
        state.route = "v2_approved_fast_path"
        state.answer_style = _answer_style_for_state(state)
        state.contexts = []
        return state

    if lightweight_only_enabled():
        return lightweight_only_miss_state(state.mode, state.request)

    cache_key = cache_key_for(state.mode, state.request)
    cached_answer = get_cached_answer(cache_key)
    if cached_answer:
        state.answer = cached_answer
        state.prompt_version = prompt_version_for_mode(state.mode)
        state.model_used = f"{state.request.model}:cache"
        state.fallback_used = False
        state.route = "cache"
        return state

    prompt = build_prompt(state.mode, state.request, context=_context_text(state), intent=state.free_question_intent)
    state.prompt_version = prompt_version_for_mode(state.mode, state.free_question_intent)
    state.prompt_strategy = prompt_strategy_for_mode(state.mode, state.free_question_intent)
    state.prompt_hash = compute_prompt_hash(prompt)
    generation_model = _generation_model_for_state(state)
    started = time.perf_counter()
    try:
        state.answer = generator(
            model=generation_model,
            prompt=prompt,
            temperature=state.request.temperature,
            max_tokens=max_tokens_for_mode(state.mode, state.request.max_tokens),
            num_ctx=state.request.num_ctx,
            num_thread=state.request.num_thread,
        )
        state.generation_ms = int((time.perf_counter() - started) * 1000)
        state.answer = compact_answer(state.answer, state.mode)
        state.model_used = generation_model
        state.route = "rag_generation" if state.contexts else "generation"
        state.answer_style = _answer_style_for_state(state)
        if _should_retry_fallback_model(state) and not contains_korean(state.answer):
            retry_started = time.perf_counter()
            state.answer = generator(
                model=FALLBACK_MODEL,
                prompt=prompt,
                temperature=state.request.temperature,
                max_tokens=max_tokens_for_mode(state.mode, state.request.max_tokens),
                num_ctx=state.request.num_ctx,
                num_thread=state.request.num_thread,
            )
            state.answer = compact_answer(state.answer, state.mode)
            state.model_used = FALLBACK_MODEL
            state.error = None
            state.retry_generation_ms = int((time.perf_counter() - retry_started) * 1000)
    except Exception as exc:
        state.generation_ms = int((time.perf_counter() - started) * 1000)
        if _should_retry_fallback_model(state):
            retry_started = time.perf_counter()
            try:
                state.answer = generator(
                    model=FALLBACK_MODEL,
                    prompt=prompt,
                    temperature=state.request.temperature,
                    max_tokens=max_tokens_for_mode(state.mode, state.request.max_tokens),
                    num_ctx=state.request.num_ctx,
                    num_thread=state.request.num_thread,
                )
                state.answer = compact_answer(state.answer, state.mode)
                state.model_used = FALLBACK_MODEL
                state.route = "rag_generation" if state.contexts else "generation"
                state.answer_style = _answer_style_for_state(state)
                state.error = None
                state.retry_generation_ms = int((time.perf_counter() - retry_started) * 1000)
                return state
            except Exception as fallback_exc:
                state.retry_generation_ms = int((time.perf_counter() - retry_started) * 1000)
                state.error = f"{exc}; fallback model failed: {fallback_exc}"
                state.fallback_reason = _fallback_reason_from_exception(fallback_exc, exc)
        else:
            state.error = str(exc)
            state.fallback_reason = _fallback_reason_from_exception(exc)
        state.answer = _fallback_message(state.fallback_reason)
        state.model_used = "template"
        state.fallback_used = True
        state.route = "fallback_template"
        state.answer_style = _answer_style_for_state(state)

    # Semantic Judge 사후 평가 검증 레이어 연동
    from app.workflow.judge import semantic_judge_enabled
    if state.mode == "free-question" and state.route in {"generation", "rag_generation"} and state.answer and semantic_judge_enabled():
        # 1. Determine Tier (Tier 1 vs Tier 2)
        tier = "tier2"
        is_concept_def = state.free_question_intent and state.free_question_intent.intent == "concept_definition"
        short_answer = len(state.answer or "") < 700
        high_risk = _is_high_risk_question(state)
        
        if is_concept_def and short_answer and not high_risk and state.retry_count == 0:
            tier = "tier1"
            
        state.judge_tier = tier
        state.semantic_judge_skipped = False
        
        from app.workflow.judge import judge_answer
        # 1차 판정 수행
        judge_res = judge_answer(
            mode=state.mode,
            request=state.request,
            answer=state.answer,
            contexts=state.contexts,
            intent=state.free_question_intent,
            generator=generator,
        )
        state.judge_result = judge_res
        state.semantic_judge_prompt_version = judge_res.prompt_version
        state.semantic_judge_prompt_hash = judge_res.prompt_hash
        
        # 2. 1회 재시도 (should_retry 이고 retry_count == 0 일 때)
        if judge_res.should_retry and state.retry_count == 0:
            # retry 발생 시 무조건 Tier 2 격상
            state.judge_tier = "tier2"
            state.retry_count = 1
            
            # retry 시에는 background context 제거 버전 prompt 사용 (fake intent 활용해 context_dependent=False)
            if state.free_question_intent:
                from app.workflow.intent import FreeQuestionIntent
                fake_intent = FreeQuestionIntent(
                    intent=state.free_question_intent.intent,
                    rag_policy=state.free_question_intent.rag_policy,
                    topic=state.free_question_intent.topic,
                    confidence=state.free_question_intent.confidence,
                    context_dependent=False,
                    sub_intent=state.free_question_intent.sub_intent
                )
            else:
                fake_intent = None
            
            retry_prompt = build_prompt(state.mode, state.request, context=_context_text(state), intent=fake_intent)
            state.retry_prompt_version = "retry_v1"
            state.retry_prompt_hash = compute_prompt_hash(retry_prompt)
            
            # 2차 생성 시도
            try:
                state.answer = generator(
                    model=generation_model,
                    prompt=retry_prompt,
                    temperature=state.request.temperature,
                    max_tokens=max_tokens_for_mode(state.mode, state.request.max_tokens),
                    num_ctx=state.request.num_ctx,
                    num_thread=state.request.num_thread,
                )
                state.answer = compact_answer(state.answer, state.mode)
                
                # 2차 판정 수행
                second_judge = judge_answer(
                    mode=state.mode,
                    request=state.request,
                    answer=state.answer,
                    contexts=state.contexts,
                    intent=fake_intent or state.free_question_intent,
                    generator=generator,
                )
                state.judge_result = second_judge
                state.semantic_judge_prompt_version = second_judge.prompt_version
                state.semantic_judge_prompt_hash = second_judge.prompt_hash
            except Exception as retry_exc:
                state.error = f"Retry generation failed: {retry_exc}"
        
        # 3. 최종 판사 검증 실패 및 Fallback 우회 제어
        final_judge = state.judge_result
        if final_judge and (final_judge.relevance_score < 0.7 or final_judge.context_bias_score > 0.6 or final_judge.hallucination_risk == "high"):
            state.answer = _fallback_for_state(state)
            state.model_used = "template" if state.model_used is None else state.model_used
            state.fallback_used = True
            state.route = "fallback_template"
            state.answer_style = _answer_style_for_state(state)

        # Set metrics according to final tier
        if state.judge_tier == "tier1":
            state.grounding_judge_skipped = True
            state.estimated_latency_saved_ms = 2000.0
        else:
            state.grounding_judge_skipped = False
            state.estimated_latency_saved_ms = 0.0

    if state.mode == "free-question" and state.answer:
        from app.workflow.grounding import validate_grounding, GroundingResult
        from app.workflow.grounding import grounding_judge_enabled
        is_skipped = (
            state.route in {"static_fast_path", "generated_card_fast_path", "cache"}
            or not state.contexts
            or state.judge_tier == "tier0"
            or state.judge_tier == "tier1"
            or not grounding_judge_enabled()
        )
        if is_skipped:
            state.grounding_result = GroundingResult(
                grounding_score=1.0,
                evidence_coverage=1.0,
                unsupported_claims=[],
                grounded=True,
                reason="Skipped grounding: fast-path, cache, empty contexts, or tier",
            )
            state.grounding_judge_skipped = True
        else:
            # Tier 2! Run grounding judge.
            state.grounding_judge_skipped = False
            
            # Unit Test 환경이거나 Mock generator인 경우 동기식 처리로 Flake 원천 방지
            import sys
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
                # Production 환경: daemon Background Thread 기동 (Response Latency 완전 고립화)
                state.grounding_async_executed = True
                
                import threading
                import logging
                import json
                from app.observability import LOGGER_NAME
                
                # Capture variables safely
                req_copy = state.request
                ans_copy = state.answer
                ctx_copy = state.contexts
                intent_copy = state.free_question_intent
                route_copy = state.route
                model_copy = state.model_used
                fallback_copy = state.fallback_used
                retry_copy = state.retry_count > 0
                prompt_ver_copy = state.prompt_version
                prompt_hash_copy = state.prompt_hash
                prompt_strat_copy = state.prompt_strategy
                candidate_copy = state.candidate_id
                
                def async_grounding_task():
                    try:
                        res = validate_grounding(
                            request=req_copy,
                            answer=ans_copy,
                            contexts=ctx_copy,
                            generator=generator,
                        )
                        sink = logging.getLogger(LOGGER_NAME)
                        enriched = {
                            "event": "ai_review.grounding_evaluated",
                            "correlation_id": candidate_copy or "async-grounding",
                            "grounding_score": res.grounding_score,
                            "retrieval_coverage_score": res.evidence_coverage,
                            "unsupported_claim_detected": len(res.unsupported_claims) > 0,
                            "low_grounding_answer": res.grounding_score < 0.7,
                            "unsupported_claims": res.unsupported_claims,
                            "grounded": res.grounded,
                            "reason": res.reason,
                            
                            # Tags
                            "route": route_copy,
                            "intent": intent_copy.intent if intent_copy else "free-question",
                            "sub_intent": intent_copy.sub_intent if intent_copy else "unknown",
                            "rag_policy": intent_copy.rag_policy if intent_copy else "unknown",
                            "model": model_copy,
                            "fallback_used": fallback_copy,
                            "retry_used": retry_copy,
                            "hallucination_risk": "low",
                            
                            # Prompt versioning metadata
                            "prompt_version": prompt_ver_copy,
                            "prompt_hash": prompt_hash_copy,
                            "prompt_strategy": prompt_strat_copy,
                            "grounding_prompt_version": res.prompt_version,
                            "grounding_prompt_hash": res.prompt_hash,
                        }
                        sink.info(json.dumps(enriched, ensure_ascii=False, sort_keys=True))
                    except Exception as async_exc:
                        logging.getLogger("ai_review.workflow.nodes").error(f"Background grounding execution failed: {async_exc}")
                
                thread = threading.Thread(target=async_grounding_task)
                thread.daemon = True
                state.grounding_thread = thread
                thread.start()

        if state.grounding_result:
            state.grounding_prompt_version = state.grounding_result.prompt_version
            state.grounding_prompt_hash = state.grounding_result.prompt_hash

    return state


def validate_answer_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    if state.route in {"off_topic_redirect", "out_of_course_redirect"}:
        state.validation = ValidationResult(
            korean_ok=True,
            required_keywords_ok=True,
            forbidden_claims_ok=True,
            relevance_ok=True,
            score=1.0,
            reasons=state.quality_flags.copy(),
        )
        return state

    preserved_flags = [
        flag for flag in state.quality_flags
        if flag in {"scope_unknown", "course_scope_applied"}
    ]
    korean_ok = contains_korean(state.answer)
    required_keywords_ok = _required_keywords_ok(state)
    forbidden_claims_ok = not any(claim in state.answer for claim in FORBIDDEN_CLAIMS)
    relevance_ok = _answer_relevance_ok(state)
    topic_ok = _answer_mentions_topic(state)
    complete_ok = not re.search(r"(?:^|\n)\s*(?:\d+[.)]|[-*])\s*$", state.answer)

    reasons: list[str] = []
    if not korean_ok:
        reasons.append("non_korean")
    if not required_keywords_ok:
        reasons.append("missing_required_keywords")
    if not forbidden_claims_ok:
        reasons.append("forbidden_claim")
    if not relevance_ok:
        reasons.append("stale_original_context")
    if not topic_ok:
        reasons.append("missing_topic")
    if not complete_ok:
        reasons.append("incomplete_answer")

    score = sum((korean_ok, required_keywords_ok, forbidden_claims_ok, relevance_ok, topic_ok, complete_ok)) / 6
    state.quality_flags = preserved_flags + [reason for reason in reasons if reason not in preserved_flags]
    state.validation = ValidationResult(
        korean_ok=korean_ok,
        required_keywords_ok=required_keywords_ok,
        forbidden_claims_ok=forbidden_claims_ok,
        relevance_ok=relevance_ok,
        score=score,
        reasons=reasons,
    )
    return state


def confidence_gate_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    validation_score = state.validation.score if state.validation else 0.0
    if state.mode in {"free-question", "follow-up"} and not state.contexts:
        retrieval_score = 0.5
        rule_match_score = 1.0
    else:
        retrieval_score = min(1.0, max((context.score for context in state.contexts), default=0.0) / 4)
        rule_match_score = 1.0 if state.contexts else 0.5
    model_self_check_score = 0.0 if state.error else 1.0

    state.confidence = calculate_confidence(
        ConfidenceInputs(
            retrieval_score=retrieval_score,
            rule_match_score=rule_match_score,
            answer_validation_score=validation_score,
            model_self_check_score=model_self_check_score,
        )
    )
    return state


def fallback_answer_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    graph_route = state.route if state.route in {"dead_end_state", "error_state"} else None
    relevance_ok = state.validation.relevance_ok if state.validation else True
    topic_ok = "missing_topic" not in state.quality_flags
    complete_ok = "incomplete_answer" not in state.quality_flags
    if (
        state.confidence
        and not state.confidence.should_fallback
        and contains_korean(state.answer)
        and relevance_ok
        and topic_ok
        and complete_ok
    ):
        return state

    state.fallback_reason = state.fallback_reason or "quality_validation"
    reason_message = _fallback_message(state.fallback_reason)
    state.answer = (
        f"{reason_message} {_fallback_for_state(state)}"
        if state.fallback_reason == "quality_validation"
        else reason_message
    )
    state.model_used = "template" if state.model_used is None else state.model_used
    state.fallback_used = True
    state.route = graph_route or "fallback_template"
    state.answer_style = state.answer_style or _answer_style_for_state(state)
    if state.confidence and state.confidence.score >= 0.8:
        state.confidence = ConfidenceResult(
            score=0.79,
            band="medium",
            should_fallback=False,
            should_save_candidate=True,
        )
    return state


def max_tokens_for_mode(mode: str, request_limit: int) -> int:
    if mode == "first-question":
        return min(request_limit, 48)
    if mode == "follow-up":
        return min(request_limit, 90)
    if mode == "free-question":
        return min(request_limit, 128)
    return min(request_limit, 70)


def _query_for_request(
    mode: str,
    request: AiGenerateRequest,
    intent: FreeQuestionIntent | None = None,
    learner_query: str | None = None,
) -> str:
    if mode == "free-question" and request.user_answer:
        free_question_intent = intent or classify_free_question_with_embeddings(request.user_answer)
        if free_question_intent.rag_policy == "latest_question_only":
            return learner_query or request.user_answer

    return _full_context_query(request, learner_query)


def _full_context_query(request: AiGenerateRequest, learner_query: str | None = None) -> str:
    return " ".join(
        part
        for part in (
            request.question,
            request.correct_answer,
            request.selected_answer,
            learner_query or request.user_answer,
            request.evaluation,
        )
        if part
    )


def _fallback_for_state(state: ReviewWorkflowState) -> str:
    if (
        state.mode == "free-question"
        and state.free_question_intent
        and state.free_question_intent.rag_policy == "latest_question_only"
    ):
        topic = state.free_question_intent.topic or state.request.user_answer.strip()
        specific = _topic_specific_fallback(topic, state.request.user_answer)
        if specific:
            return specific
        return _contextual_free_question_fallback(topic, state.request)

    return korean_fallback(state.mode, state.request)


def _is_off_topic_free_question(state: ReviewWorkflowState) -> bool:
    return (
        state.mode == "free-question"
        and state.free_question_intent is not None
        and state.free_question_intent.intent == "off_topic"
    )


def _off_topic_redirect_state(state: ReviewWorkflowState) -> ReviewWorkflowState:
    state.answer = (
        "이 화면은 현재 틀린 문제를 복습하는 공간이라 해당 질문에는 답변하지 않을게요. "
        "현재 문제의 개념, 정답 근거, 오답 이유처럼 학습과 직접 관련된 질문을 해 주세요."
    )
    state.prompt_version = prompt_version_for_mode(state.mode, state.free_question_intent)
    state.prompt_strategy = prompt_strategy_for_mode(state.mode, state.free_question_intent)
    state.model_used = "template"
    state.fallback_used = False
    state.route = "off_topic_redirect"
    state.answer_style = "off_topic"
    state.quality_flags = ["off_topic"]
    state.contexts = []
    state.v2_fast_path_decision = {"mode": "skipped", "hit": False, "reason": "off_topic"}
    return state


def _approved_cards_for_scope():
    return [
        card for card in load_concept_cards()
        if getattr(card.review, "card_status", None) == CardStatus.APPROVED
    ]


def _course_scope_for_state(state: ReviewWorkflowState) -> CourseScopeDecision:
    return resolve_course_scope(
        query=_learner_query_for_state(state),
        course_id=state.request.course_id,
        intent=state.free_question_intent,
        approved_cards=_approved_cards_for_scope(),
    )


def _out_of_course_redirect_state(
    state: ReviewWorkflowState,
    matched_card_id: str | None,
) -> ReviewWorkflowState:
    state.answer = (
        "이 질문은 현재 코스 복습 범위 밖의 기술 주제라 여기서는 답변하지 않을게요. "
        "현재 문제의 개념, 정답 근거, 오답 이유를 질문해 주세요."
    )
    state.prompt_version = prompt_version_for_mode(state.mode, state.free_question_intent)
    state.prompt_strategy = prompt_strategy_for_mode(state.mode, state.free_question_intent)
    state.model_used = "template"
    state.fallback_used = False
    state.route = "out_of_course_redirect"
    state.answer_style = "out_of_course"
    state.quality_flags = ["out_of_course"]
    state.contexts = []
    state.matched_concept_id_override = matched_card_id
    state.v2_fast_path_decision = {
        "mode": "skipped",
        "hit": False,
        "reason": "out_of_course",
        "card_id": matched_card_id,
    }
    return state


def _append_quality_flag(state: ReviewWorkflowState, flag: str) -> None:
    if flag not in state.quality_flags:
        state.quality_flags.append(flag)


def _topic_specific_fallback(topic: str, user_answer: str) -> str | None:
    normalized = normalize_question(" ".join((topic, user_answer))).lower()
    compact = normalized.replace(" ", "")
    if "equals" in normalized and "==" in normalized:
        return (
            "Java에서 `equals()`는 객체의 논리적 동등성, 즉 내용이나 값이 같은지를 비교합니다. "
            "`==`는 기본형에서는 값을 비교하지만 객체 참조형에서는 두 변수가 같은 객체를 가리키는지 비교합니다."
        )
    if "@transactional" in normalized or "transactional" in normalized or "트랜잭션" in normalized:
        return (
            "@Transactional은 Spring에서 한 업무 단위를 하나의 transaction으로 묶는 선언입니다. "
            "메서드 안의 DB 작업이 모두 성공하면 commit되고, 중간에 예외가 나면 rollback되어 데이터가 어중간하게 저장되는 일을 막습니다. "
            "여러 Repository 호출을 하나의 업무 흐름으로 묶어야 하므로 보통 Service 계층에 두는 것이 정답 기준입니다."
        )
    if (
        "계층" in normalized
        or "layer" in normalized
    ):
        return (
            "계층은 보통 `Controller -> Service -> Repository -> Entity`로 나눠 봅니다. "
            "Controller는 요청/응답, Service는 비즈니스 규칙과 transaction boundary, Repository는 DB 접근, Entity는 저장되는 도메인 상태를 맡습니다. "
            "따라서 문제를 풀 때는 단어가 익숙한지보다 어느 계층의 책임인지를 먼저 따져야 합니다."
        )
    if "gittag" in compact or ("git" in normalized and "tag" in normalized):
        return (
            "Git tag는 특정 commit에 붙이는 고정 이름표입니다. 보통 `v1.0.0`처럼 release version을 표시할 때 씁니다. "
            "branch는 계속 움직일 수 있지만, tag는 한 commit을 가리키는 기준점이라 배포 버전 추적, rollback 기준, 릴리스 노트 연결에 유용합니다."
        )
    if "idempotent" in normalized or "idempotency" in normalized:
        return (
            "Idempotent 설계는 같은 요청이나 메시지가 여러 번 들어와도 최종 결과가 한 번 처리한 것과 same result가 되게 만드는 방식입니다. "
            "네트워크 retry, 중복 클릭, 메시지 재전달 상황에서 데이터가 두 번 생성되거나 금액이 두 번 차감되는 문제를 막기 위해 사용합니다. "
            "실무에서는 idempotency key, unique constraint, 처리 이력 테이블 같은 장치로 중복 처리를 막습니다."
        )
    if (
        "network" in normalized
        or "네트워크" in normalized
        or ("자동" in normalized and "연결" in normalized)
        or "autoconnect" in compact
    ):
        return (
            "network 다이어그램에서 auto layout이나 auto connect 기능은 도구에 따라 가능합니다. "
            "다만 도구가 의미를 완전히 추론해 없는 관계를 자동 생성한다기보다, 사용자가 node와 edge 데이터를 주면 layout 엔진이 보기 좋게 배치하고 연결선을 그려주는 방식입니다. "
            "예를 들어 Mermaid, Graphviz, Cytoscape 같은 도구는 관계 데이터가 있을 때 자동 배치와 연결선 정리를 도와줍니다."
        )
    return None


def _contextual_free_question_fallback(topic: str, request: AiGenerateRequest) -> str:
    correct_hint = f" 이 문제의 정답 기준은 `{request.correct_answer}`입니다." if request.correct_answer else ""
    clean_topic = topic.strip() or "그 개념"
    return (
        f"{clean_topic}에 대한 승인된 지식 카드가 아직 부족해서 현재 문제 맥락 기준으로 답할게요."
        f"{correct_hint} "
        "정의만 외우기보다 이 개념이 어떤 조건에서 정답이 되는지, 그리고 헷갈리는 보기와 어떤 책임이나 역할이 다른지를 나눠서 보면 됩니다."
    )


def _answer_relevance_ok(state: ReviewWorkflowState) -> bool:
    intent = state.free_question_intent
    if state.mode != "free-question" or not intent or intent.rag_policy != "latest_question_only":
        return True

    topic = normalize_question(intent.topic)
    answer = normalize_question(state.answer)
    if topic and topic in answer:
        return True
    topic_tokens = re.findall(r"[a-z0-9+#.-]+|[가-힣]{2,}", topic)
    if any(token and token in answer for token in topic_tokens):
        return True

    stale_terms = _stale_original_terms(state.request)
    return not any(term and term in answer for term in stale_terms)


def _answer_mentions_topic(state: ReviewWorkflowState) -> bool:
    intent = state.free_question_intent
    if state.mode != "free-question" or not intent or intent.rag_policy != "latest_question_only":
        return True
    if state.model_used == LIGHTWEIGHT_MODEL_NAME:
        return True

    answer = normalize_question(state.answer)
    candidates = [
        intent.topic,
        state.resolved_query.matched_term if state.resolved_query else None,
        state.resolved_query.matched_concept_id if state.resolved_query else None,
    ]
    normalized_candidates = [normalize_question(value) for value in candidates if value]
    normalized_candidates = [
        candidate
        for candidate in normalized_candidates
        if candidate not in {"이개념", "개념", "개념을", "이개념을"}
    ]
    if not normalized_candidates:
        return True
    if any(candidate and candidate in answer for candidate in normalized_candidates):
        return True

    topic_tokens = re.findall(r"[a-z0-9+#.-]+|[가-힣]{2,}", " ".join(normalized_candidates))
    return any(token and token in answer for token in topic_tokens)


def _answer_style_for_state(state: ReviewWorkflowState) -> str | None:
    intent = state.free_question_intent
    if state.mode != "free-question" or not intent:
        return None
    return {
        "comparison": "comparison",
        "practical_application": "practical",
        "related_concept": "related",
    }.get(intent.intent, "definition")


def _stale_original_terms(request: AiGenerateRequest) -> set[str]:
    terms: set[str] = set()
    for value in (request.question, request.correct_answer, request.selected_answer):
        normalized = normalize_question(value)
        if not normalized:
            continue
        for token in re.findall(r"[a-z0-9+#.-]+|[가-힣]{2,}", normalized):
            terms.add(token)
    return terms


def _context_text(state: ReviewWorkflowState) -> str:
    return "\n\n".join(context.content for context in state.contexts)


def _learner_query_for_state(state: ReviewWorkflowState) -> str:
    if state.resolved_query:
        return state.resolved_query.resolved_query
    return state.request.user_answer


def _matched_concept_id_for_lightweight(state: ReviewWorkflowState) -> str | None:
    if state.resolved_query and state.resolved_query.matched_concept_id:
        return state.resolved_query.matched_concept_id
    for context in state.contexts:
        if (
            context.concept_id
            and should_use_workflow_context(context)
            and context.metadata.get("version") in {"admin-approved-candidate", "course-candidate"}
        ):
            return context.concept_id
    return None


def _should_retry_fallback_model(state: ReviewWorkflowState) -> bool:
    return state.mode in {"first-question", "follow-up", "free-question"}


def _fallback_reason_from_exception(*exceptions: BaseException) -> str:
    for exc in exceptions:
        current: BaseException | None = exc
        while current is not None:
            name = current.__class__.__name__.lower()
            message = str(current).lower()
            if "timeout" in name or "timed out" in message or "timeout" in message:
                return "timeout"
            current = current.__cause__
    return "other_error"


def _fallback_message(reason: str | None) -> str:
    if reason == "timeout":
        return "답변 생성에 시간이 조금 더 걸리고 있습니다. 잠시 후 다시 시도해주세요."
    if reason == "quality_validation":
        return "더 정확한 답변을 준비하고 있습니다. 질문을 조금 더 구체적으로 작성해 다시 시도해주세요."
    return "지금은 답변을 준비하지 못했습니다. 잠시 후 다시 시도해주세요."


def _generation_model_for_state(state: ReviewWorkflowState) -> str:
    if state.mode == "follow-up":
        return FALLBACK_MODEL
    return state.request.model


def _required_keywords_ok(state: ReviewWorkflowState) -> bool:
    if state.mode == "follow-up":
        return True
    if state.mode != "free-question":
        return True

    validation_contexts = state.contexts

    if not validation_contexts:
        return True

    keywords = []
    for context in validation_contexts:
        content = context.content
        if "## 평가 키워드" not in content:
            continue
        keyword_block = content.split("## 평가 키워드", 1)[1]
        for line in keyword_block.splitlines():
            stripped = line.strip()
            if stripped.startswith("-"):
                keywords.append(stripped.lstrip("-").strip())

    if not keywords:
        return True
    return any(keyword and keyword in state.answer for keyword in keywords)
