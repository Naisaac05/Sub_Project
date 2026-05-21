import time
from app.ollama.client import call_ollama
from app.schemas import AiGenerateRequest, AiGenerateResponse
from app.workflow.answer_cache import cache_key_for, put_cached_answer, run_single_flight
from app.workflow.graph import LANGGRAPH_AVAILABLE, candidate_save_node, run_state_graph
from app.workflow.nodes import (
    Generator,
    confidence_gate_node,
    fallback_answer_node,
    generate_answer_node,
    retrieve_context_node,
    rule_evaluate_node,
    validate_answer_node,
)
from app.workflow.state import ReviewWorkflowState


def run_review_workflow(
    mode: str,
    request: AiGenerateRequest,
    generator: Generator = call_ollama,
) -> AiGenerateResponse:
    started = time.perf_counter()
    cache_key = cache_key_for(mode, request)

    def run_workflow() -> ReviewWorkflowState:
        if LANGGRAPH_AVAILABLE:
            return run_state_graph(mode=mode, request=request, generator=generator)
        return _run_sequential_workflow(mode=mode, request=request, generator=generator)

    state = run_single_flight(cache_key, run_workflow)

    latency_ms = int((time.perf_counter() - started) * 1000)
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
    response.observability_events = [_workflow_completed_event(response)]
    return response


def _run_sequential_workflow(
    mode: str,
    request: AiGenerateRequest,
    generator: Generator = call_ollama,
) -> ReviewWorkflowState:
    state = ReviewWorkflowState(mode=mode, request=request)

    state = retrieve_context_node(state)
    state = rule_evaluate_node(state)
    state = generate_answer_node(state, generator=generator)
    state = validate_answer_node(state)
    state = confidence_gate_node(state)
    state = fallback_answer_node(state)
    if not state.fallback_used and state.model_used not in {"template", "lightweight-template"}:
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



