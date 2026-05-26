from __future__ import annotations

from app.evaluation.semantic import judge_answer_semantics, should_cache_answer
from app.workflow.nodes import _context_text
from app.workflow.state import ReviewWorkflowState


NON_CACHEABLE_MODELS = {"template", "lightweight-template"}


def semantic_evaluate_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    state.quality_flags = judge_answer_semantics(
        answer=state.answer,
        route=state.route,
        fallback_used=state.fallback_used,
        retrieved_concept_ids=[context.concept_id for context in state.contexts],
        context_text=_context_text(state),
        existing_quality_flags=state.quality_flags,
    )
    return state


def should_store_answer_cache(state: ReviewWorkflowState) -> bool:
    return (
        not state.fallback_used
        and state.model_used not in NON_CACHEABLE_MODELS
        and should_cache_answer(state.quality_flags)
    )
