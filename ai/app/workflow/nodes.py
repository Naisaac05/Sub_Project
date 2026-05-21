from collections.abc import Callable
import re

from app.ollama.client import FALLBACK_MODEL, call_ollama
from app.prompts import build_prompt, prompt_version_for_mode
from app.rag.retriever import retrieve_context
from app.schemas import AiGenerateRequest
from app.scoring import ConfidenceInputs, ConfidenceResult, calculate_confidence
from app.validation.text import compact_answer, contains_korean, korean_fallback
from app.workflow.answer_cache import cache_key_for, get_cached_answer
from app.workflow.intent import FreeQuestionIntent, classify_free_question, normalize_question
from app.workflow.lightweight_answers import LIGHTWEIGHT_MODEL_NAME, resolve_lightweight_answer
from app.workflow.query_resolver import resolve_learner_query
from app.workflow.state import ReviewWorkflowState, ValidationResult


Generator = Callable[..., str]
# 강신호(제목/키워드/concept_id) 토큰이 하나라도 맞으면 score_card 점수는 최소 5.0이다
# (강매칭 +4 보너스 + 겹침 ≥1). 그 미만은 강신호 없는 본문 우연 겹침이라 엉뚱한 카드를
# 근거로 주입할 위험이 있어 워크플로 컨텍스트에서 제외한다.
MIN_WORKFLOW_CONTEXT_SCORE = 5.0

FORBIDDEN_CLAIMS = (
    "트랜잭션 문제",
    "네트워크 지연 문제",
)


def retrieve_context_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    learner_query = state.request.user_answer
    if state.mode == "free-question":
        state.resolved_query = resolve_learner_query(state.request.user_answer)
        learner_query = state.resolved_query.resolved_query
        state.free_question_intent = classify_free_question(learner_query)

    query = _query_for_request(state.mode, state.request, state.free_question_intent, learner_query)
    state.contexts = [
        context
        for context in retrieve_context(query, limit=3)
        if context.score >= MIN_WORKFLOW_CONTEXT_SCORE
    ]
    return state


def rule_evaluate_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    return state


def generate_answer_node(
    state: ReviewWorkflowState,
    generator: Generator = call_ollama,
) -> ReviewWorkflowState:
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
        return state

    cache_key = cache_key_for(state.mode, state.request)
    cached_answer = get_cached_answer(cache_key)
    if cached_answer:
        state.answer = cached_answer
        state.prompt_version = prompt_version_for_mode(state.mode)
        state.model_used = f"{state.request.model}:cache"
        state.fallback_used = False
        state.route = "cache"
        return state

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
        state.route = "rag_generation" if state.contexts else "generation"
        state.answer_style = _answer_style_for_state(state)
    except Exception as exc:
        if _should_retry_fallback_model(state):
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
                return state
            except Exception as fallback_exc:
                state.error = f"{exc}; fallback model failed: {fallback_exc}"
        else:
            state.error = str(exc)
        state.answer = korean_fallback(state.mode, state.request)
        state.model_used = "template"
        state.fallback_used = True
        state.route = "fallback_template"
        state.answer_style = _answer_style_for_state(state)
    return state


def validate_answer_node(state: ReviewWorkflowState) -> ReviewWorkflowState:
    korean_ok = contains_korean(state.answer)
    required_keywords_ok = _required_keywords_ok(state)
    forbidden_claims_ok = not any(claim in state.answer for claim in FORBIDDEN_CLAIMS)
    relevance_ok = _answer_relevance_ok(state)
    topic_ok = _answer_mentions_topic(state)

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

    score = sum((korean_ok, required_keywords_ok, forbidden_claims_ok, relevance_ok, topic_ok)) / 5
    state.quality_flags = reasons.copy()
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
    if state.mode == "free-question" and not state.contexts:
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
    if (
        state.confidence
        and not state.confidence.should_fallback
        and contains_korean(state.answer)
        and relevance_ok
        and topic_ok
    ):
        return state

    state.answer = _fallback_for_state(state)
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
        free_question_intent = intent or classify_free_question(request.user_answer)
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


def _topic_specific_fallback(topic: str, user_answer: str) -> str | None:
    normalized = normalize_question(" ".join((topic, user_answer))).lower()
    compact = normalized.replace(" ", "")
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
            and context.score >= MIN_WORKFLOW_CONTEXT_SCORE
            and context.metadata.get("version") in {"admin-approved-candidate", "course-candidate"}
        ):
            return context.concept_id
    return None


def _should_retry_fallback_model(state: ReviewWorkflowState) -> bool:
    return (
        state.mode == "free-question"
        and state.free_question_intent is not None
        and state.free_question_intent.rag_policy == "latest_question_only"
        and state.request.model != FALLBACK_MODEL
    )


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
