from __future__ import annotations

import re


NON_CACHEABLE_FLAGS = {"hallucination_suspected", "contradiction_suspected"}
SAFE_ROUTES = {
    "cache",
    "fallback_template",
    "static_fast_path",
    "generated_card_fast_path",
    "lightweight_only_miss",
    "cache_only_miss",
    "template_fallback_only",
}
CONCRETE_CLAIM_PATTERNS = (
    r"@[A-Za-z][A-Za-z0-9_]+",
    r"\bGET\b|\bPOST\b|\bPUT\b|\bDELETE\b",
    r"\b\d{3}\b",
    r"\bSQL\b|\bDB\b|\bAPI\b|\bHTTP\b|\bRedis\b|\bCache\b|\bJPA\b|\bSpring\b",
    r"자동으로|항상|반드시|모든",
)
KNOWN_CONCEPT_MARKERS = {
    "spring-n-plus-one": ("spring-n-plus-one", "n+1"),
    "java-equals": ("java-equals", "equals", "hashcode"),
    "spring-fetch-join": ("spring-fetch-join", "fetch join"),
    "redis-cache": ("redis-cache", "redis cache", "cache miss"),
}


def judge_answer_semantics(
    *,
    answer: str,
    route: str | None,
    fallback_used: bool | None,
    retrieved_concept_ids: list[str],
    context_text: str,
    existing_quality_flags: list[str],
) -> list[str]:
    flags = list(dict.fromkeys(existing_quality_flags))
    route_value = route or ""
    if fallback_used or route_value in SAFE_ROUTES:
        return flags

    has_context = bool(retrieved_concept_ids) or bool(context_text.strip())
    concrete = _has_concrete_claim(answer)
    if not has_context and concrete:
        _append_flag(flags, "evidence_missing")
        _append_flag(flags, "hallucination_suspected")

    if _mentions_unretrieved_known_concept(answer, retrieved_concept_ids):
        _append_flag(flags, "contradiction_suspected")

    return flags


def should_cache_answer(quality_flags: list[str]) -> bool:
    return not (set(quality_flags) & NON_CACHEABLE_FLAGS)


def _has_concrete_claim(answer: str) -> bool:
    return any(re.search(pattern, answer, flags=re.IGNORECASE) for pattern in CONCRETE_CLAIM_PATTERNS)


def _mentions_unretrieved_known_concept(answer: str, retrieved_concept_ids: list[str]) -> bool:
    normalized_answer = answer.lower()
    retrieved = set(retrieved_concept_ids)
    for concept_id, markers in KNOWN_CONCEPT_MARKERS.items():
        if concept_id in retrieved:
            continue
        if any(marker in normalized_answer for marker in markers):
            return True
    return False


def _append_flag(flags: list[str], flag: str) -> None:
    if flag not in flags:
        flags.append(flag)
