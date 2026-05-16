from collections.abc import Callable

from app.ollama.client import call_ollama
from app.prompts import build_prompt, prompt_version_for_mode
from app.rag.retriever import retrieve_context
from app.schemas import AiGenerateRequest
from app.scoring import ConfidenceInputs, ConfidenceResult, calculate_confidence
from app.validation.text import compact_answer, contains_korean, korean_fallback
from app.workflow.state import ReviewWorkflowState, ValidationResult


Generator = Callable[..., str]

FORBIDDEN_CLAIMS = (
    "트랜잭션 문제",
    "네트워크 문제",
)


def retrieve_context_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    state.contexts = retrieve_context(_query_for_request(state.request), limit=3)
    return state


def rule_evaluate_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    return state


def generate_answer_node(
    state: ReviewWorkflowState,
    generator: Generator = call_ollama,
) -> ReviewWorkflowState:
    prompt = build_prompt(state.mode, state.request, context=_context_text(state))
    state.prompt_version = prompt_version_for_mode(state.mode)
    try:
        state.answer = generator(
            model=state.request.model,
            prompt=prompt,
            temperature=state.request.temperature,
            max_tokens=max_tokens_for_mode(state.mode, state.request.max_tokens),
            num_ctx=state.request.num_ctx,
            num_thread=state.request.num_thread,
        )
        state.answer = compact_answer(state.answer, state.mode)
        state.model_used = state.request.model
    except Exception as exc:
        state.error = str(exc)
        state.answer = korean_fallback(state.mode, state.request)
        state.model_used = "template"
        state.fallback_used = True
    return state


def validate_answer_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    korean_ok = contains_korean(state.answer)
    required_keywords_ok = _required_keywords_ok(state)
    forbidden_claims_ok = not any(claim in state.answer for claim in FORBIDDEN_CLAIMS)

    reasons: list[str] = []
    if not korean_ok:
        reasons.append("non_korean")
    if not required_keywords_ok:
        reasons.append("missing_required_keywords")
    if not forbidden_claims_ok:
        reasons.append("forbidden_claim")

    score = sum((korean_ok, required_keywords_ok, forbidden_claims_ok)) / 3
    state.validation = ValidationResult(
        korean_ok=korean_ok,
        required_keywords_ok=required_keywords_ok,
        forbidden_claims_ok=forbidden_claims_ok,
        score=score,
        reasons=reasons,
    )
    return state


def confidence_gate_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    validation_score = state.validation.score if state.validation else 0.0
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
    if state.confidence and not state.confidence.should_fallback and contains_korean(state.answer):
        return state

    state.answer = korean_fallback(state.mode, state.request)
    state.model_used = "template" if state.model_used is None else state.model_used
    state.fallback_used = True
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
    return min(request_limit, 120)


def _query_for_request(request: AiGenerateRequest) -> str:
    return " ".join(
        part
        for part in (
            request.question,
            request.correct_answer,
            request.selected_answer,
            request.user_answer,
            request.evaluation,
        )
        if part
    )


def _context_text(state: ReviewWorkflowState) -> str:
    return "\n\n".join(context.content for context in state.contexts)


def _required_keywords_ok(state: ReviewWorkflowState) -> bool:
    if not state.contexts:
        return True

    keywords = []
    for context in state.contexts:
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
