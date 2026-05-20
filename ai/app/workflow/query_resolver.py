from dataclasses import dataclass
from difflib import SequenceMatcher
import re


@dataclass(frozen=True)
class ResolvedQuery:
    original_query: str
    resolved_query: str
    matched_concept_id: str | None
    matched_term: str | None
    correction_type: str
    confidence: float


@dataclass(frozen=True)
class ConceptAlias:
    concept_id: str
    term: str
    aliases: tuple[str, ...]
    typos: tuple[str, ...] = ()


CONCEPT_ALIASES: tuple[ConceptAlias, ...] = (
    ConceptAlias(
        concept_id="frontend-aria-label",
        term="aria-label",
        aliases=("aria-label", "aria label", "aria_label", "arialabel", "접근성 라벨", "스크린리더 라벨"),
        typos=("arila-label", "arial-label", "aria-lable", "arialabels"),
    ),
    ConceptAlias(
        concept_id="frontend-pagination",
        term="pagination",
        aliases=("pagination", "페이지네이션", "페이징", "page navigation"),
        typos=("pagnation", "pagenation", "pagniation"),
    ),
    ConceptAlias(
        concept_id="java-backend-controlleradvice",
        term="@ControllerAdvice",
        aliases=("@ControllerAdvice", "ControllerAdvice", "Controller Advice", "전역 예외 처리"),
        typos=("ConrollerAdvice", "ControllerAdvise", "ControlerAdvice"),
    ),
    ConceptAlias(
        concept_id="rest-api",
        term="REST API",
        aliases=("REST API", "RESTAPI", "rest api"),
        typos=("res api", "rest apu"),
    ),
)

NO_MATCH = ("none", 0.0)


def resolve_learner_query(query: str) -> ResolvedQuery:
    stripped = query.strip()
    if not stripped:
        return _no_match(query)

    normalized_query = _normalize(stripped)
    for concept in CONCEPT_ALIASES:
        for alias in concept.aliases:
            if _normalize(alias) and _normalize(alias) in normalized_query:
                correction_type = "exact" if _contains_literal(stripped, concept.term) else "alias"
                return _resolved(query, stripped, concept, alias, correction_type, 0.95)

    for concept in CONCEPT_ALIASES:
        for typo in concept.typos:
            if _normalize(typo) and _normalize(typo) in normalized_query:
                return _resolved(query, stripped, concept, typo, "typo", 0.9)

    for token in _latin_tokens(stripped):
        if len(token) < 6:
            continue
        best = _best_fuzzy_match(token)
        if best is None:
            continue
        concept, score = best
        if score >= 0.88:
            return _resolved(query, stripped, concept, token, "typo", score)

    return _no_match(query)


def _resolved(
    original_query: str,
    stripped_query: str,
    concept: ConceptAlias,
    matched_text: str,
    correction_type: str,
    confidence: float,
) -> ResolvedQuery:
    resolved_query = _replace_first_term(stripped_query, matched_text, concept.term)
    if resolved_query == stripped_query and concept.term not in stripped_query:
        resolved_query = f"{concept.term} {stripped_query}"
    return ResolvedQuery(
        original_query=original_query,
        resolved_query=resolved_query,
        matched_concept_id=concept.concept_id,
        matched_term=concept.term,
        correction_type=correction_type,
        confidence=round(confidence, 3),
    )


def _no_match(query: str) -> ResolvedQuery:
    return ResolvedQuery(
        original_query=query,
        resolved_query=query,
        matched_concept_id=None,
        matched_term=None,
        correction_type=NO_MATCH[0],
        confidence=NO_MATCH[1],
    )


def _replace_first_term(query: str, matched_text: str, replacement: str) -> str:
    if not matched_text:
        return query
    pattern = re.compile(re.escape(matched_text), re.IGNORECASE)
    return pattern.sub(replacement, query, count=1)


def _contains_literal(query: str, term: str) -> bool:
    return re.search(re.escape(term), query, re.IGNORECASE) is not None


def _best_fuzzy_match(token: str) -> tuple[ConceptAlias, float] | None:
    candidates: list[tuple[ConceptAlias, float]] = []
    normalized_token = _normalize(token)
    for concept in CONCEPT_ALIASES:
        for value in (concept.term, *concept.aliases):
            normalized_value = _normalize(value)
            if len(normalized_value) < 6:
                continue
            score = SequenceMatcher(None, normalized_token, normalized_value).ratio()
            candidates.append((concept, score))

    if not candidates:
        return None
    candidates.sort(key=lambda item: item[1], reverse=True)
    return candidates[0]


def _latin_tokens(text: str) -> list[str]:
    return re.findall(r"[A-Za-z][A-Za-z0-9@+#_.-]*", text)


def _normalize(text: str) -> str:
    return re.sub(r"[^a-z0-9가-힣@+#]+", "", text.lower())
