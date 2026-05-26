import os

from app.prompts import prompt_version_for_mode
from app.schemas import AiGenerateRequest
from app.validation.text import korean_fallback
from app.workflow.answer_cache import cache_key_for, get_cached_answer
from app.workflow.state import ReviewWorkflowState


def template_fallback_only_enabled() -> bool:
    return _truthy(os.environ.get("AI_REVIEW_TEMPLATE_FALLBACK_ONLY", "false"))


def cache_only_enabled() -> bool:
    return _truthy(os.environ.get("AI_REVIEW_CACHE_ONLY", "false"))


def lightweight_only_enabled() -> bool:
    return _truthy(os.environ.get("AI_REVIEW_LIGHTWEIGHT_ONLY", "false"))


def no_candidate_capture_enabled() -> bool:
    return _truthy(os.environ.get("AI_REVIEW_NO_CANDIDATE_CAPTURE", "false"))


def degraded_state_for(mode: str, request: AiGenerateRequest) -> ReviewWorkflowState | None:
    if template_fallback_only_enabled():
        return _template_state(mode, request, route="template_fallback_only")

    if not cache_only_enabled():
        return None

    cache_key = cache_key_for(mode, request)
    cached_answer = get_cached_answer(cache_key)
    if cached_answer:
        state = ReviewWorkflowState(mode=mode, request=request)
        state.answer = cached_answer
        state.prompt_version = prompt_version_for_mode(mode)
        state.model_used = f"{request.model}:cache"
        state.fallback_used = False
        state.route = "cache"
        return state

    state = _template_state(mode, request, route="cache_only_miss")
    state.quality_flags = ["cache_only_miss"]
    return state


def _template_state(mode: str, request: AiGenerateRequest, *, route: str) -> ReviewWorkflowState:
    state = ReviewWorkflowState(mode=mode, request=request)
    state.answer = korean_fallback(mode, request)
    state.prompt_version = prompt_version_for_mode(mode)
    state.model_used = "template"
    state.fallback_used = True
    state.route = route
    return state


def lightweight_only_miss_state(mode: str, request: AiGenerateRequest) -> ReviewWorkflowState:
    state = _template_state(mode, request, route="lightweight_only_miss")
    state.quality_flags = ["lightweight_only_miss"]
    return state


def _truthy(value: str) -> bool:
    return value.strip().lower() in {"1", "true", "yes", "on"}
