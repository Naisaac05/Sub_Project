from collections.abc import Callable, Iterable
from dataclasses import asdict, dataclass
from functools import lru_cache
import json
import os
from pathlib import Path
import random
import re

from app.rag.documents import parse_concept_card
from app.rag.parallel_config import load_parallel_rag_config, should_serve_v2
from app.rag.retriever import LexicalRetrieverAdapter
from app.schemas.rag_card import CardStatus, PayloadStatus, RagCard
from app.workflow.intent import FreeQuestionIntent


V2_CONCEPT_ROOT = Path(__file__).resolve().parents[1] / "knowledge" / "concepts_v2"


def _approved_v2_card_paths() -> dict[str, Path]:
    paths: dict[str, Path] = {}
    for path in sorted(V2_CONCEPT_ROOT.rglob("*.json")):
        try:
            card = json.loads(path.read_text(encoding="utf-8-sig"))
        except Exception:
            continue
        if card.get("review", {}).get("card_status") == "approved":
            paths[card["card_id"]] = path
    return paths


def approved_v2_card_ids() -> frozenset[str]:
    return frozenset(_approved_v2_card_paths())


APPROVED_V2_CARD_IDS = approved_v2_card_ids()
CardLoader = Callable[[Iterable[str]], list[RagCard]]


@dataclass(frozen=True)
class V2FastPathDecision:
    mode: str
    hit: bool
    reason: str
    card_id: str | None = None
    payload_intent: str | None = None
    answer: str | None = None
    score: float = 0.0

    @property
    def reason_message(self) -> str:
        return {
            "disabled": "Fast Path 기능이 비활성화되어 있습니다",
            "no_approved_cards": "승인된 Fast Path 카드가 없습니다",
            "unsupported_intent": "현재 지원하지 않는 intent입니다",
            "score_gate": "카드 매칭 점수가 부족합니다",
            "empty_allowlist": "승인된 Fast Path 카드가 없습니다",
            "retrieval_miss": "승인된 Fast Path 카드가 없습니다",
            "payload_not_approved": "승인된 Fast Path 카드가 없습니다",
        }.get(self.reason, self.reason)

    def metadata(self) -> dict[str, object]:
        data = asdict(self)
        data.pop("answer", None)
        data["reason_message"] = self.reason_message
        return data


def resolve_v2_approved_fast_path(
    query: str,
    intent: FreeQuestionIntent | None,
    *,
    selected_answer: str | None = None,
    allowed_card_ids: Iterable[str] | None = None,
    card_loader: CardLoader | None = None,
    random_value: float | None = None,
) -> V2FastPathDecision:
    config = load_parallel_rag_config()
    if not _master_enabled(config.enabled):
        return V2FastPathDecision("off", False, "disabled")

    value = random.random() if random_value is None else random_value
    mode = "serve" if should_serve_v2(config, value) else "shadow"

    payload_intent = _payload_intent(intent, selected_answer)
    if payload_intent is None:
        return V2FastPathDecision(mode, False, "unsupported_intent")

    eligible_ids = _runtime_allowlist()
    if allowed_card_ids is not None:
        eligible_ids = frozenset(eligible_ids & set(allowed_card_ids))
    if not eligible_ids:
        return V2FastPathDecision(mode, False, "empty_allowlist", payload_intent=payload_intent)

    loader = card_loader or load_allowlisted_v2_cards
    try:
        cards = [
            card
            for card in loader(eligible_ids)
            if card.card_id in eligible_ids and card.review.card_status == CardStatus.APPROVED
        ]
    except Exception:
        return V2FastPathDecision(mode, False, "loader_error", payload_intent=payload_intent)
    if not cards:
        return V2FastPathDecision(mode, False, "no_approved_cards", payload_intent=payload_intent)

    top = LexicalRetrieverAdapter(card_loader=lambda: cards).retrieve(query, limit=1)
    if not top:
        return V2FastPathDecision(mode, False, "retrieval_miss", payload_intent=payload_intent)
    result = top[0]
    min_score = _env_float("AI_REVIEW_V2_APPROVED_FAST_PATH_MIN_SCORE", 5.0)
    if result.score < min_score:
        return V2FastPathDecision(
            mode, False, "score_gate", result.concept_id, payload_intent, score=result.score
        )

    card = next(card for card in cards if card.card_id == result.concept_id)
    if not _has_specific_anchor(card, query):
        return V2FastPathDecision(
            mode, False, "anchor_miss", card.card_id, payload_intent, score=result.score
        )
    if card.review.payload_status.get(payload_intent) != PayloadStatus.APPROVED:
        return V2FastPathDecision(
            mode, False, "payload_not_approved", card.card_id, payload_intent, score=result.score
        )
    answer = _render_payload(card, payload_intent, selected_answer)
    if not answer:
        return V2FastPathDecision(
            mode, False, "payload_empty", card.card_id, payload_intent, score=result.score
        )
    return V2FastPathDecision(mode, True, "hit", card.card_id, payload_intent, answer, result.score)


def load_allowlisted_v2_cards(card_ids: Iterable[str]) -> list[RagCard]:
    selected = tuple(sorted(set(card_ids) & approved_v2_card_ids()))
    return list(_load_allowlisted_v2_cards_cached(selected))


@lru_cache(maxsize=4)
def _load_allowlisted_v2_cards_cached(card_ids: tuple[str, ...]) -> tuple[RagCard, ...]:
    approved_paths = _approved_v2_card_paths()
    cards: list[RagCard] = []
    for card_id in card_ids:
        path = approved_paths[card_id]
        cards.append(parse_concept_card(path))
    return tuple(cards)


def _runtime_allowlist() -> frozenset[str]:
    approved_ids = approved_v2_card_ids()
    configured = os.getenv("AI_REVIEW_V2_APPROVED_FAST_PATH_CARD_IDS")
    if configured is None:
        return approved_ids
    requested = {value.strip() for value in configured.split(",") if value.strip()}
    return frozenset(requested & approved_ids)


def _payload_intent(intent: FreeQuestionIntent | None, selected_answer: str | None) -> str | None:
    if intent is None:
        return None
    if intent.intent == "concept_definition" and intent.sub_intent == "definition":
        return "CONCEPT_DEFINITION"
    if intent.intent == "concept_definition" and intent.sub_intent == "comparison":
        return "CONCEPT_DEFINITION"
    if intent.intent == "wrong_answer_explanation":
        return "WRONG_ANSWER_REASON" if selected_answer else "ANSWER_REASON"
    return None


def _render_payload(card: RagCard, payload_intent: str, selected_answer: str | None) -> str:
    if payload_intent == "CONCEPT_DEFINITION" and card.payloads.CONCEPT_DEFINITION:
        return card.payloads.CONCEPT_DEFINITION.content.strip()
    if payload_intent == "ANSWER_REASON" and card.payloads.ANSWER_REASON:
        return card.payloads.ANSWER_REASON.why_correct.strip()
    if payload_intent == "WRONG_ANSWER_REASON" and card.payloads.WRONG_ANSWER_REASON:
        payload = card.payloads.WRONG_ANSWER_REASON
        selected = (selected_answer or "").strip().lower()
        for option in payload.per_option.values():
            if selected and option.text.strip().lower() == selected:
                return option.reason.strip()
        return "\n".join(item.strip() for item in payload.common_mistakes if item.strip())
    return ""


def _has_specific_anchor(card: RagCard, query: str) -> bool:
    query_tokens = _tokens(query)
    phrases = [card.term, *card.aliases, *card.retrieval.boost_keywords]
    for phrase in phrases:
        phrase_tokens = _tokens(phrase)
        if not phrase_tokens:
            continue
        if len(phrase_tokens) == 1 and phrase_tokens == _tokens(card.term):
            if phrase_tokens[0] in query_tokens:
                return True
        if len(phrase_tokens) >= 2 and _contains_tokens(query_tokens, phrase_tokens):
            return True
    return False


def _tokens(value: str) -> list[str]:
    return re.findall(r"[a-z0-9+#.]+|[가-힣]{2,}", value.lower())


def _contains_tokens(query_tokens: list[str], phrase_tokens: list[str]) -> bool:
    width = len(phrase_tokens)
    return any(query_tokens[index:index + width] == phrase_tokens for index in range(len(query_tokens) - width + 1))


def _env_flag(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    return default if raw is None else raw.lower() in {"1", "true", "yes", "on"}


def _master_enabled(config_enabled: bool) -> bool:
    if "AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED" in os.environ:
        return _env_flag("AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED", False)
    return config_enabled


def _env_float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, str(default)))
    except ValueError:
        return default
