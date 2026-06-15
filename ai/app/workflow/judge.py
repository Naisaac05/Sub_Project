from dataclasses import dataclass
import hashlib
import inspect
import json
import logging
import os
import re
import threading
import time

from app.ollama.client import FALLBACK_MODEL, call_ollama
from app.prompts.registry import compute_prompt_hash
from app.schemas import AiGenerateRequest
from app.workflow.intent import FreeQuestionIntent

logger = logging.getLogger("ai_review.workflow.judge")

JUDGE_MODEL = os.getenv("PYTHON_AI_JUDGE_MODEL", FALLBACK_MODEL)
LITE_SEMANTIC_JUDGE_PROMPT_VERSION = "semantic_judge_lite_v1"


@dataclass(frozen=True)
class SemanticJudgeResult:
    relevance_score: float
    context_bias_score: float
    hallucination_risk: str
    should_retry: bool
    reason: str
    prompt_version: str | None = None
    prompt_hash: str | None = None
    cache_hit: bool = False
    cache_key_hash: str | None = None
    prompt_tokens_estimate: int = 0


_JUDGE_CACHE = {}
_JUDGE_CACHE_LOCK = threading.Lock()
MAX_CACHE_ENTRIES = 512
TTL_SECONDS = 300


def semantic_judge_enabled() -> bool:
    return os.getenv("AI_REVIEW_SEMANTIC_JUDGE_ENABLED", "false").lower() in {"1", "true", "yes", "on"}


def clear_answer_cache():
    with _JUDGE_CACHE_LOCK:
        _JUDGE_CACHE.clear()


def judge_answer(
    mode: str,
    request: AiGenerateRequest,
    answer: str,
    contexts: list,
    intent: FreeQuestionIntent | None = None,
    generator=call_ollama,
) -> SemanticJudgeResult:
    if not semantic_judge_enabled():
        return SemanticJudgeResult(1.0, 0.0, "low", False, "Skipped judge: disabled by configuration")
    if mode != "free-question" or not answer:
        return SemanticJudgeResult(
            1.0,
            0.0,
            "low",
            False,
            "Skipped judge for non-free-question or empty answer",
        )

    if not _is_judge_compatible(generator):
        return SemanticJudgeResult(
            1.0,
            0.0,
            "low",
            False,
            "Skipped judge: generator is not judge-compatible",
        )

    prompt_version = LITE_SEMANTIC_JUDGE_PROMPT_VERSION
    intent_str = intent.intent if intent else "unknown"
    raw_key = f"{request.user_answer or ''}|{answer}|{intent_str}|{prompt_version}"
    cache_key_hash = hashlib.sha256(raw_key.encode("utf-8")).hexdigest()
    now = time.time()

    with _JUDGE_CACHE_LOCK:
        entry = _JUDGE_CACHE.get(cache_key_hash)
        if entry and now < entry["expires_at"]:
            cached = entry["result"]
            return SemanticJudgeResult(
                relevance_score=cached.relevance_score,
                context_bias_score=cached.context_bias_score,
                hallucination_risk=cached.hallucination_risk,
                should_retry=cached.should_retry,
                reason=cached.reason,
                prompt_version=cached.prompt_version,
                prompt_hash=cached.prompt_hash,
                cache_hit=True,
                cache_key_hash=cache_key_hash,
                prompt_tokens_estimate=cached.prompt_tokens_estimate,
            )
        if entry:
            _JUDGE_CACHE.pop(cache_key_hash, None)

    context_text = "\n\n".join(ctx.content for ctx in contexts[:2]) if contexts else "No retrieved contexts."
    if len(context_text) > 1600:
        context_text = context_text[:1600]

    prompt = f"""
You are a lightweight, precise AI Semantic Judge for a programming mentor answer.
Return JSON only. Prefer speed and stable coarse scoring over detailed critique.

[Latest Learner Question]
{request.user_answer}

[Answer To Judge]
{answer}

[Original Quiz Context]
question={request.question}
correct_answer={request.correct_answer}
selected_answer={request.selected_answer}

[Retrieved Contexts]
{context_text}

[Intent Info]
- Intent: {intent.intent if intent else "unknown"}
- Sub Intent: {intent.sub_intent if intent else "unknown"}
- Context Dependent: {intent.context_dependent if intent else "False"}

Rules:
1. relevance_score: direct answer to Latest Learner Question.
2. context_bias_score: high only when answer explains the original quiz/choices instead of the latest question.
3. Topic Continuity Rule: same-topic concept explanations are valid, not bias.
4. hallucination_risk: high only for clearly unsafe or unsupported technical claims.
5. should_retry: true only if relevance_score < 0.7 or context_bias_score > 0.6.

JSON Schema:
{{
  "relevance_score": 0.0-1.0,
  "context_bias_score": 0.0-1.0,
  "hallucination_risk": "low"|"medium"|"high",
  "should_retry": true|false,
  "reason": "short Korean reason"
}}
""".strip()
    prompt_hash = compute_prompt_hash(prompt)

    try:
        raw_response = generator(
            model=JUDGE_MODEL,
            prompt=prompt,
            temperature=0.0,
            max_tokens=96,
            num_ctx=min(request.num_ctx, 1024),
            num_thread=request.num_thread,
        )
        data = json.loads(_extract_json(raw_response))

        relevance_score = float(data.get("relevance_score", 1.0))
        context_bias_score = float(data.get("context_bias_score", 0.0))
        hallucination_risk = str(data.get("hallucination_risk", "low")).lower()
        if hallucination_risk not in {"low", "medium", "high"}:
            hallucination_risk = "low"

        should_retry = bool(data.get("should_retry", False))
        if relevance_score < 0.7 or context_bias_score > 0.6:
            should_retry = True
        if hallucination_risk == "high":
            should_retry = False

        result = SemanticJudgeResult(
            relevance_score=relevance_score,
            context_bias_score=context_bias_score,
            hallucination_risk=hallucination_risk,
            should_retry=should_retry,
            reason=str(data.get("reason", "Judge completed")),
            prompt_version=prompt_version,
            prompt_hash=prompt_hash,
            cache_hit=False,
            cache_key_hash=cache_key_hash,
            prompt_tokens_estimate=len(prompt) // 4,
        )
        _save_to_cache(cache_key_hash, result, now)
        return result
    except Exception as exc:
        logger.warning("Semantic judge execution failed, fallback to safe result. Error: %s", exc)
        return SemanticJudgeResult(
            relevance_score=1.0,
            context_bias_score=0.0,
            hallucination_risk="low",
            should_retry=False,
            reason=f"Exception occurred: {exc}",
            prompt_version=prompt_version,
            prompt_hash=prompt_hash,
            cache_hit=False,
            cache_key_hash=cache_key_hash,
            prompt_tokens_estimate=0,
        )


def _is_judge_compatible(generator) -> bool:
    func_name = getattr(generator, "__name__", "")
    if func_name in ("call_ollama", "recording_generator", "call_ollama_stream_async"):
        return True
    try:
        source = inspect.getsource(generator)
    except Exception:
        return (
            "judge" in func_name.lower()
            or "mock_llm" in func_name.lower()
            or "fallback" in func_name.lower()
        )
    return any(
        token in source
        for token in (
            "precise AI Semantic Judge",
            "Semantic Judge",
            "judge",
            "relevance_score",
            "context_bias_score",
        )
    )


def _extract_json(raw_response: str) -> str:
    cleaned = raw_response.strip()
    match = re.search(r"\{.*\}", cleaned, re.DOTALL)
    return match.group(0) if match else cleaned


def _save_to_cache(cache_key_hash: str, result: SemanticJudgeResult, now: float) -> None:
    with _JUDGE_CACHE_LOCK:
        expired_keys = [key for key, value in _JUDGE_CACHE.items() if now >= value["expires_at"]]
        for key in expired_keys:
            _JUDGE_CACHE.pop(key, None)

        if len(_JUDGE_CACHE) >= MAX_CACHE_ENTRIES:
            oldest = next(iter(_JUDGE_CACHE))
            _JUDGE_CACHE.pop(oldest, None)

        _JUDGE_CACHE[cache_key_hash] = {
            "result": result,
            "expires_at": now + TTL_SECONDS,
        }
