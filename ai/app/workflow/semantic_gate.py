from __future__ import annotations

from app.evaluation.semantic import judge_answer_semantics, should_cache_answer
from app.workflow.nodes import _context_text, _fallback_for_state, _fallback_message
from app.workflow.state import ReviewWorkflowState


NON_CACHEABLE_MODELS = {"template", "lightweight-template"}


def semantic_evaluate_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    if state.route == "v2_approved_fast_path":
        return state

    state.quality_flags = judge_answer_semantics(
        answer=state.answer,
        route=state.route,
        fallback_used=state.fallback_used,
        retrieved_concept_ids=[context.concept_id for context in state.contexts],
        context_text=_context_text(state),
        existing_quality_flags=state.quality_flags,
    )
    if "contradiction_suspected" in state.quality_flags and not state.fallback_used:
        state.fallback_reason = "quality_validation"
        fallback_body = _fallback_for_state(state)
        state.answer = (
            fallback_body
            if "current_problem_context" in state.quality_flags
            else f"{_fallback_message(state.fallback_reason)} {fallback_body}"
        )
        state.model_used = "template"
        state.fallback_used = True
        state.route = "fallback_template"
    return state


def should_store_answer_cache(state: ReviewWorkflowState) -> bool:
    return (
        not state.fallback_used
        and state.model_used not in NON_CACHEABLE_MODELS
        and should_cache_answer(state.quality_flags)
    )
