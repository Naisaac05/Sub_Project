from __future__ import annotations

from dataclasses import dataclass
import re

from app.rag.retriever import LexicalRetrieverAdapter
from app.schemas.rag_card import RagCard
from app.workflow.intent import FreeQuestionIntent


@dataclass(frozen=True)
class CourseScopeDecision:
    scope: str
    current_course: str
    allowed_card_ids: frozenset[str]
    matched_card_id: str | None = None
    reason: str = ""


COURSE_ALIASES = {
    "java": "java",
    "java-basic": "java",
    "java-backend": "java",
    "spring": "spring",
    "spring-backend": "spring",
    "frontend": "frontend",
    "front-end": "frontend",
    "react": "frontend",
    "nextjs": "frontend",
    "next-js": "frontend",
    "python": "python",
    "python-backend": "python",
    "algorithm": "algorithm",
    "coding-test": "algorithm",
}


def normalize_course_id(value: str | None) -> str:
    normalized = re.sub(r"[^a-z0-9]+", "-", (value or "").strip().lower()).strip("-")
    return COURSE_ALIASES.get(normalized, normalized)


def card_courses(card: RagCard) -> set[str]:
    courses = {normalize_course_id(card.category)}
    for source_id in card.source_question_ids:
        prefix = str(source_id).split(":", 1)[0]
        normalized = normalize_course_id(prefix)
        if normalized:
            courses.add(normalized)
    return {course for course in courses if course}


def cards_for_course(cards: list[RagCard], course_id: str | None) -> list[RagCard]:
    current = normalize_course_id(course_id)
    if not current:
        return []
    return [card for card in cards if current in card_courses(card)]


def resolve_course_scope(
    *,
    query: str,
    course_id: str | None,
    intent: FreeQuestionIntent | None,
    approved_cards: list[RagCard],
) -> CourseScopeDecision:
    current = normalize_course_id(course_id)
    if not current:
        return CourseScopeDecision("scope_unknown", "", frozenset(), reason="missing_course_id")

    allowed = cards_for_course(approved_cards, current)
    allowed_ids = frozenset(card.card_id for card in allowed)
    if intent is None or intent.intent in {"off_topic", "unknown"}:
        return CourseScopeDecision("not_applicable", current, allowed_ids, reason="unsupported_intent")
    if intent.intent not in {"concept_definition", "wrong_answer_explanation", "follow_up"}:
        return CourseScopeDecision("not_applicable", current, allowed_ids, reason="unsupported_intent")

    all_hits = LexicalRetrieverAdapter(card_loader=lambda: approved_cards).retrieve(query, limit=1)
    if not all_hits:
        return CourseScopeDecision("course_card_miss", current, allowed_ids, reason="no_card_hit")

    top_card_id = all_hits[0].concept_id
    if top_card_id in allowed_ids:
        return CourseScopeDecision("course_card_hit", current, allowed_ids, top_card_id, "current_course_card")
    return CourseScopeDecision("out_of_course_tech", current, allowed_ids, top_card_id, "matched_other_course_card")
