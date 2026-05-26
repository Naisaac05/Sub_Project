from __future__ import annotations

import os
from pathlib import Path
from collections.abc import Callable

from app.knowledge.auto_candidates import (
    append_auto_candidate,
    build_auto_candidate,
    should_capture_auto_candidate,
)
from app.knowledge.candidate_sink import candidate_sink_mode, save_auto_candidate
from app.ollama.client import call_ollama
from app.workflow.answer_cache import cache_key_for, put_cached_answer
from app.workflow.degraded import no_candidate_capture_enabled
from app.workflow.nodes import (
    Generator,
    confidence_gate_node,
    fallback_answer_node,
    generate_answer_node,
    retrieve_context_node,
    rule_evaluate_node,
    validate_answer_node,
)
from app.workflow.semantic_gate import semantic_evaluate_node, should_store_answer_cache
from app.workflow.state import ReviewWorkflowState

try:
    from langgraph.graph import END, StateGraph

    LANGGRAPH_AVAILABLE = True
except ImportError:
    END = "__end__"
    StateGraph = None
    LANGGRAPH_AVAILABLE = False


WORKFLOW_NODE_NAMES = (
    "retrieve_context",
    "rule_evaluate",
    "generate_answer",
    "validate_answer",
    "confidence_gate",
    "fallback_answer",
    "cache_answer",
    "candidate_save",
    "error_state",
    "dead_end_state",
)

DEFAULT_AUTO_CANDIDATES_PATH = (
    Path(__file__).resolve().parents[1] / "knowledge" / "candidates" / "auto_candidates.jsonl"
)


GraphNode = Callable[[ReviewWorkflowState], ReviewWorkflowState]


def build_review_state_graph(generator: Generator = call_ollama):
    if StateGraph is None:
        raise RuntimeError(
            "langgraph is not installed; install ai/requirements-rag.txt to enable StateGraph execution"
        )

    graph = StateGraph(ReviewWorkflowState)
    graph.add_node("retrieve_context", _safe_node(retrieve_context_node))
    graph.add_node("rule_evaluate", _safe_node(rule_evaluate_node))
    graph.add_node("generate_answer", _safe_node(lambda state: generate_answer_node(state, generator=generator)))
    graph.add_node("validate_answer", _safe_node(validate_answer_node))
    graph.add_node("confidence_gate", _safe_node(confidence_gate_node))
    graph.add_node("fallback_answer", _safe_node(fallback_answer_node))
    graph.add_node("cache_answer", _safe_node(cache_answer_node))
    graph.add_node("candidate_save", _safe_node(candidate_save_node))
    graph.add_node("error_state", error_state_node)
    graph.add_node("dead_end_state", dead_end_state_node)

    graph.set_entry_point("retrieve_context")
    _add_error_aware_edge(graph, "retrieve_context", "rule_evaluate")
    _add_error_aware_edge(graph, "rule_evaluate", "generate_answer")
    _add_error_aware_edge(graph, "generate_answer", "validate_answer")
    _add_error_aware_edge(graph, "validate_answer", "confidence_gate")
    _add_error_aware_edge(graph, "confidence_gate", "fallback_answer")
    graph.add_conditional_edges(
        "fallback_answer",
        _next_after_fallback,
        {
            "dead_end_state": "dead_end_state",
            "cache_answer": "cache_answer",
        },
    )
    graph.add_edge("dead_end_state", "cache_answer")
    graph.add_edge("error_state", "fallback_answer")
    graph.add_edge("cache_answer", "candidate_save")
    graph.add_edge("candidate_save", END)
    return graph


def run_state_graph(
    mode: str,
    request,
    generator: Generator = call_ollama,
) -> ReviewWorkflowState:
    graph = build_review_state_graph(generator=generator).compile()
    initial_state = ReviewWorkflowState(mode=mode, request=request)
    result = graph.invoke(initial_state)
    if isinstance(result, ReviewWorkflowState):
        return result
    return ReviewWorkflowState(**result)


def cache_answer_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    state = semantic_evaluate_node(state)
    if should_store_answer_cache(state):
        put_cached_answer(cache_key_for(state.mode, state.request), state.answer)
    state.graph_status = "completed"
    return state


def candidate_save_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    if no_candidate_capture_enabled():
        state.quality_flags.append("candidate_capture_disabled")
        return state

    queue_path = os.environ.get("AI_REVIEW_AUTO_CANDIDATES_PATH")
    queue = Path(queue_path) if queue_path else DEFAULT_AUTO_CANDIDATES_PATH

    reason = should_capture_auto_candidate(
        mode=state.mode,
        route=state.route,
        confidence_score=state.confidence.score if state.confidence else None,
        retrieved_concept_ids=[context.concept_id for context in state.contexts],
        fallback_used=state.fallback_used,
    )
    if not reason:
        return state

    candidate = build_auto_candidate(
        source_question=state.request.user_answer,
        resolved_query=_candidate_resolved_query(state),
        route=state.route,
        confidence_score=state.confidence.score if state.confidence else None,
        needs_review_reason=reason,
        generated_answer=state.answer,
    )
    try:
        if candidate_sink_mode() == "jsonl":
            written = append_auto_candidate(queue, candidate)
        else:
            written = save_auto_candidate(candidate)
    except Exception:
        state.quality_flags.append("candidate_capture_failed")
        return state

    if written:
        state.candidate_id = str(candidate["candidate_id"])
    return state


def _candidate_resolved_query(state: ReviewWorkflowState) -> str:
    intent_topic = state.free_question_intent.topic if state.free_question_intent else ""
    if intent_topic:
        return intent_topic
    if state.resolved_query:
        return state.resolved_query.resolved_query
    return state.request.user_answer


def error_state_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    state.graph_status = "error"
    state.route = "error_state"
    return state


def dead_end_state_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    state.graph_status = "dead_end"
    state.route = "dead_end_state"
    state = fallback_answer_node(state)
    return state


def _safe_node(node: GraphNode) -> GraphNode:
    def wrapped(state: ReviewWorkflowState) -> ReviewWorkflowState:
        try:
            return node(state)
        except Exception as exc:
            state.error = str(exc)
            return error_state_node(state)

    return wrapped


def _add_error_aware_edge(graph, source: str, target: str) -> None:
    graph.add_conditional_edges(
        source,
        lambda state: "error_state" if state.route == "error_state" else target,
        {
            "error_state": "error_state",
            target: target,
        },
    )


def _next_after_fallback(state: ReviewWorkflowState) -> str:
    if not state.answer or state.validation is None or state.confidence is None:
        return "dead_end_state"
    return "cache_answer"
