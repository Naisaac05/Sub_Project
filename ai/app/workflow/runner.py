import time
from app.ollama.client import call_ollama
from app.schemas import AiGenerateRequest, AiGenerateResponse
from app.workflow.answer_cache import cache_key_for, put_cached_answer, run_single_flight, get_cached_answer
from app.workflow.degraded import degraded_state_for, lightweight_only_enabled, lightweight_only_miss_state
from app.workflow.graph import LANGGRAPH_AVAILABLE, candidate_save_node, run_state_graph
from app.workflow.nodes import (
    Generator,
    confidence_gate_node,
    fallback_answer_node,
    generate_answer_node,
    retrieve_context_node,
    rule_evaluate_node,
    validate_answer_node,
    _context_text,
    _learner_query_for_state,
    _matched_concept_id_for_lightweight,
    _answer_style_for_state,
    _should_retry_fallback_model,
    max_tokens_for_mode,
)
from app.workflow.state import ReviewWorkflowState
from app.workflow.lightweight_answers import LIGHTWEIGHT_MODEL_NAME, resolve_lightweight_answer
from app.workflow.semantic_gate import semantic_evaluate_node, should_store_answer_cache
from app.prompts import build_prompt, prompt_version_for_mode
from app.validation.text import compact_answer, korean_fallback
from app.ollama.client import FALLBACK_MODEL


def _build_response_from_state(state: ReviewWorkflowState, latency_ms: int) -> AiGenerateResponse:
    response = AiGenerateResponse(
        answer=state.answer,
        model_used=state.model_used,
        fallback_used=state.fallback_used,
        confidence_score=state.confidence.score if state.confidence else None,
        retrieved_concept_ids=[context.concept_id for context in state.contexts],
        prompt_version=state.prompt_version,
        latency_ms=latency_ms,
        route=state.route,
        resolved_query=state.resolved_query.resolved_query if state.resolved_query else None,
        correction_type=state.resolved_query.correction_type if state.resolved_query else None,
        matched_concept_id=state.resolved_query.matched_concept_id if state.resolved_query else None,
        answer_style=state.answer_style,
        quality_flags=state.quality_flags,
        candidate_id=state.candidate_id,
    )
    
    events = [_workflow_completed_event(response)]
    
    if state.judge_result:
        judge_res = state.judge_result
        passed = (judge_res.relevance_score >= 0.7 and 
                  judge_res.context_bias_score <= 0.6 and 
                  judge_res.hallucination_risk != "high")
                  
        events.append({
            "event": "ai_review.semantic_judge_evaluated",
            "semantic_judge_passed": passed,
            "semantic_judge_failed": not passed,
            "semantic_judge_retry": state.retry_count > 0,
            "semantic_judge_fallback": state.fallback_used and not passed,
            "semantic_context_bias_detected": judge_res.context_bias_score > 0.6,
            "relevance_score": judge_res.relevance_score,
            "context_bias_score": judge_res.context_bias_score,
            "hallucination_risk": judge_res.hallucination_risk,
            "reason": judge_res.reason,
        })
        
    response.observability_events = events
    return response


def run_review_workflow(
    mode: str,
    request: AiGenerateRequest,
    generator: Generator = call_ollama,
) -> AiGenerateResponse:
    started = time.perf_counter()
    degraded_state = degraded_state_for(mode, request)
    if degraded_state is not None:
        latency_ms = int((time.perf_counter() - started) * 1000)
        return _build_response_from_state(degraded_state, latency_ms)

    cache_key = cache_key_for(mode, request)

    def run_workflow() -> ReviewWorkflowState:
        if LANGGRAPH_AVAILABLE:
            return run_state_graph(mode=mode, request=request, generator=generator)
        return _run_sequential_workflow(mode=mode, request=request, generator=generator)

    state = run_single_flight(cache_key, run_workflow)
    latency_ms = int((time.perf_counter() - started) * 1000)
    return _build_response_from_state(state, latency_ms)


async def run_review_workflow_stream(
    mode: str,
    request: AiGenerateRequest,
    generator=None,
):
    degraded_state = degraded_state_for(mode, request)
    if degraded_state is not None:
        yield {"type": "start"}
        yield {"type": "chunk", "chunk": degraded_state.answer}
        response = _build_response_from_state(degraded_state, 0)
        yield {"type": "done", "response": response}
        return

    state = ReviewWorkflowState(mode=mode, request=request)
    state = retrieve_context_node(state)
    state = rule_evaluate_node(state)

    # 1. Fast Path (Lightweight Answer) Check
    learner_query = _learner_query_for_state(state)
    lightweight_answer = resolve_lightweight_answer(
        learner_query,
        state.free_question_intent,
        matched_concept_id=_matched_concept_id_for_lightweight(state),
    )
    if state.mode == "free-question" and lightweight_answer:
        state.answer = lightweight_answer.answer
        state.prompt_version = prompt_version_for_mode(state.mode)
        state.model_used = LIGHTWEIGHT_MODEL_NAME
        state.fallback_used = False
        state.route = lightweight_answer.route
        state.answer_style = lightweight_answer.style
        if lightweight_answer.route == "static_fast_path":
            state.contexts = []

        yield {"type": "start"}
        yield {"type": "chunk", "chunk": state.answer}

        state = validate_answer_node(state)
        state = confidence_gate_node(state)
        state = fallback_answer_node(state)
        state = candidate_save_node(state)

        response = _build_response_from_state(state, 0)
        yield {"type": "done", "response": response}
        return

    if lightweight_only_enabled():
        state = lightweight_only_miss_state(state.mode, state.request)
        yield {"type": "start"}
        yield {"type": "chunk", "chunk": state.answer}
        response = _build_response_from_state(state, 0)
        yield {"type": "done", "response": response}
        return

    # 2. Cache Check
    cache_key = cache_key_for(state.mode, state.request)
    cached_answer = get_cached_answer(cache_key)
    if cached_answer:
        state.answer = cached_answer
        state.prompt_version = prompt_version_for_mode(state.mode)
        state.model_used = f"{state.request.model}:cache"
        state.fallback_used = False
        state.route = "cache"

        yield {"type": "start"}
        yield {"type": "chunk", "chunk": state.answer}

        state = validate_answer_node(state)
        state = confidence_gate_node(state)
        state = fallback_answer_node(state)
        state = candidate_save_node(state)

        response = _build_response_from_state(state, 0)
        yield {"type": "done", "response": response}
        return

    # 3. Stream Token Generation
    prompt = build_prompt(state.mode, state.request, context=_context_text(state))
    state.prompt_version = prompt_version_for_mode(state.mode)

    yield {"type": "start"}

    started = time.perf_counter()
    chunks = []

    if generator is None:
        from app.ollama.client import call_ollama_stream_async
        stream_gen = call_ollama_stream_async
    else:
        stream_gen = generator

    try:
        async for chunk in stream_gen(
            model=state.request.model,
            prompt=prompt,
            temperature=state.request.temperature,
            max_tokens=max_tokens_for_mode(state.mode, state.request.max_tokens),
            num_ctx=state.request.num_ctx,
            num_thread=state.request.num_thread,
        ):
            chunks.append(chunk)
            yield {"type": "chunk", "chunk": chunk}

        state.answer = "".join(chunks)
        state.answer = compact_answer(state.answer, state.mode)
        state.model_used = state.request.model
        state.route = "rag_generation" if state.contexts else "generation"
        state.answer_style = _answer_style_for_state(state)
    except Exception as exc:
        if _should_retry_fallback_model(state):
            try:
                chunks = []
                async for chunk in stream_gen(
                    model=FALLBACK_MODEL,
                    prompt=prompt,
                    temperature=state.request.temperature,
                    max_tokens=max_tokens_for_mode(state.mode, state.request.max_tokens),
                    num_ctx=state.request.num_ctx,
                    num_thread=state.request.num_thread,
                ):
                    chunks.append(chunk)
                    yield {"type": "chunk", "chunk": chunk}

                state.answer = "".join(chunks)
                state.answer = compact_answer(state.answer, state.mode)
                state.model_used = FALLBACK_MODEL
                state.route = "rag_generation" if state.contexts else "generation"
                state.answer_style = _answer_style_for_state(state)
                state.error = None
            except Exception as fallback_exc:
                state.error = f"{exc}; fallback model failed: {fallback_exc}"
                state.answer = korean_fallback(state.mode, state.request)
                state.model_used = "template"
                state.fallback_used = True
                state.route = "fallback_template"
                state.answer_style = _answer_style_for_state(state)
                yield {"type": "chunk", "chunk": state.answer}
        else:
            state.error = str(exc)
            state.answer = korean_fallback(state.mode, state.request)
            state.model_used = "template"
            state.fallback_used = True
            state.route = "fallback_template"
            state.answer_style = _answer_style_for_state(state)
            yield {"type": "chunk", "chunk": state.answer}

    state = validate_answer_node(state)
    state = confidence_gate_node(state)
    state = fallback_answer_node(state)
    state = semantic_evaluate_node(state)
    if should_store_answer_cache(state):
        put_cached_answer(cache_key_for(state.mode, state.request), state.answer)
    state = candidate_save_node(state)

    latency_ms = int((time.perf_counter() - started) * 1000)
    response = _build_response_from_state(state, latency_ms)
    yield {"type": "done", "response": response}



def _run_sequential_workflow(
    mode: str,
    request: AiGenerateRequest,
    generator: Generator = call_ollama,
) -> ReviewWorkflowState:
    state = ReviewWorkflowState(mode=mode, request=request)

    state = retrieve_context_node(state)
    state = rule_evaluate_node(state)
    state = generate_answer_node(state, generator=generator)
    if state.route == "lightweight_only_miss":
        return state
    state = validate_answer_node(state)
    state = confidence_gate_node(state)
    state = fallback_answer_node(state)
    state = semantic_evaluate_node(state)
    if should_store_answer_cache(state):
        put_cached_answer(cache_key_for(mode, request), state.answer)
    state = candidate_save_node(state)
    return state


def _workflow_completed_event(response: AiGenerateResponse) -> dict[str, object]:
    return {
        "event": "ai_review.workflow_completed",
        "route": response.route,
        "model_used": response.model_used,
        "fallback_used": response.fallback_used,
        "confidence_score": response.confidence_score,
        "retrieved_concept_ids": response.retrieved_concept_ids,
        "candidate_id": response.candidate_id,
        "prompt_version": response.prompt_version,
        "latency_ms": response.latency_ms,
        "quality_flags": response.quality_flags,
    }



