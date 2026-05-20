# AI Review LangGraph StateGraph Design

## Goal

Move the AI review workflow from a LangGraph-compatible plain Python sequence to a real `StateGraph` execution path while keeping `run_review_workflow()` and the Spring/FastAPI response contract stable.

## Architecture

`ai/app/workflow/graph.py` owns graph construction. It imports `langgraph.graph.StateGraph` only inside the builder so the default lightweight environment can still run without optional RAG dependencies. When LangGraph is installed, `run_review_workflow()` executes the compiled graph. When it is absent, the runner keeps the current sequential behavior as a dependency-tolerant fallback.

The graph nodes mirror the current workflow names and add explicit terminal side-effect nodes:

1. `retrieve_context`
2. `rule_evaluate`
3. `generate_answer`
4. `validate_answer`
5. `confidence_gate`
6. `fallback_answer`
7. `cache_answer`
8. `candidate_save`

`generate_answer` already records generator exceptions in state and routes to template fallback. Phase 18 adds graph-level metadata for `error_state` and `dead_end_state` so operators can distinguish normal fallback from graph/node failures and incomplete states.

## Data Flow

The graph receives `ReviewWorkflowState`. Existing node functions mutate and return that state, matching current behavior. `cache_answer` writes successful model answers to the in-memory answer cache. `candidate_save` reads `AI_REVIEW_AUTO_CANDIDATES_PATH`, evaluates capture rules, appends JSONL candidates, and stores `candidate_id` on workflow state. The runner then builds `AiGenerateResponse` from final state and attaches the structured observability event.

## Error And Dead-End Handling

Node exceptions outside existing handled model-generation errors are caught by the graph node wrapper. The wrapper records `state.error`, sets `state.route` to `error_state`, and continues to `fallback_answer`. A dead-end state means graph execution produced no answer or skipped confidence/validation; the dead-end guard sets `state.route` to `dead_end_state` and sends the state to `fallback_answer`.

## Testing

Tests cover the graph builder shape, public runner compatibility, generator exception fallback, candidate-save node behavior, and graph unavailability fallback. Existing workflow/evaluator tests remain the regression suite.
