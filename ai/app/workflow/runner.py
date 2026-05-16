import time

from app.ollama.client import call_ollama
from app.schemas import AiGenerateRequest, AiGenerateResponse
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
    state = ReviewWorkflowState(mode=mode, request=request)

    state = retrieve_context_node(state)
    state = rule_evaluate_node(state)
    state = generate_answer_node(state, generator=generator)
    state = validate_answer_node(state)
    state = confidence_gate_node(state)
    state = fallback_answer_node(state)

    latency_ms = int((time.perf_counter() - started) * 1000)
    return AiGenerateResponse(
        answer=state.answer,
        model_used=state.model_used,
        fallback_used=state.fallback_used,
        confidence_score=state.confidence.score if state.confidence else None,
        retrieved_concept_ids=[context.concept_id for context in state.contexts],
        prompt_version=state.prompt_version,
        latency_ms=latency_ms,
    )

