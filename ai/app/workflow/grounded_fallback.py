from __future__ import annotations

from dataclasses import dataclass
import os
import re
from typing import Iterable

from app.rag.documents import load_concept_cards
from app.rag.retriever import LexicalRetrieverAdapter
from app.schemas.rag_card import CardStatus, PayloadStatus, RagCard


SAFE_GROUNDED_FALLBACK_ANSWER = (
    "현재 승인된 학습 근거만으로는 정확한 답변을 제공하기 어렵습니다. "
    "기술명과 확인하려는 동작을 조금 더 구체적으로 작성해 주세요."
)
MIN_EVIDENCE_SCORE = 8.0
MIN_EVIDENCE_MARGIN = 1.0


def grounded_fallback_enabled() -> bool:
    return os.getenv("AI_REVIEW_GROUNDED_FALLBACK_ENABLED", "true").lower() in {
        "1", "true", "yes", "on"
    }


@dataclass(frozen=True)
class GroundedEvidence:
    card_id: str
    term: str
    aliases: tuple[str, ...]
    content: str
    score: float


@dataclass(frozen=True)
class GroundedQualityResult:
    passed: bool
    reasons: tuple[str, ...]


def select_grounded_evidence(
    query: str,
    *,
    cards: Iterable[RagCard] | None = None,
    allowed_card_ids: Iterable[str] | None = None,
) -> GroundedEvidence | None:
    source = list(cards) if cards is not None else [
        card for card in load_concept_cards()
        if isinstance(card, RagCard)
    ]
    allowed = set(allowed_card_ids) if allowed_card_ids is not None else None
    approved = [
        card for card in source
        if card.review.card_status == CardStatus.APPROVED
        and card.review.payload_status.get("CONCEPT_DEFINITION") == PayloadStatus.APPROVED
        and card.payloads.CONCEPT_DEFINITION
        and card.payloads.CONCEPT_DEFINITION.content.strip()
        and (allowed is None or card.card_id in allowed)
    ]
    ranked = LexicalRetrieverAdapter(card_loader=lambda: approved).retrieve(query, limit=2)
    if not ranked or ranked[0].score < MIN_EVIDENCE_SCORE:
        return None
    runner_up = ranked[1].score if len(ranked) > 1 else 0.0
    if ranked[0].score - runner_up < MIN_EVIDENCE_MARGIN:
        return None
    selected = next(card for card in approved if card.card_id == ranked[0].concept_id)
    return GroundedEvidence(
        card_id=selected.card_id,
        term=selected.term,
        aliases=tuple(selected.aliases),
        content=selected.payloads.CONCEPT_DEFINITION.content.strip(),
        score=ranked[0].score,
    )


def validate_grounded_answer(
    question: str,
    answer: str,
    evidence: GroundedEvidence | None,
) -> GroundedQualityResult:
    reasons: list[str] = []
    normalized = answer.strip()
    lowered = normalized.lower()
    if not re.search(r"[가-힣]", normalized):
        reasons.append("non_korean")
    if not normalized or normalized[-1] not in ".!?다요죠음함됨니다세요":
        reasons.append("incomplete_answer")
    anchors = [evidence.term, *evidence.aliases] if evidence else []
    if anchors and not any(anchor.lower() in lowered for anchor in anchors if anchor.strip()):
        reasons.append("missing_topic")
    evidence_tokens = _meaningful_tokens(evidence.content if evidence else "")
    answer_tokens = _meaningful_tokens(normalized)
    anchor_tokens = _meaningful_tokens(" ".join(anchors))
    evidence_tokens -= anchor_tokens
    answer_tokens -= anchor_tokens
    if evidence and len(evidence_tokens & answer_tokens) < 2:
        reasons.append("insufficient_evidence_overlap")
    if not evidence:
        reasons.append("missing_approved_evidence")
    return GroundedQualityResult(not reasons, tuple(dict.fromkeys(reasons)))


def grounded_prompt_context(evidence: GroundedEvidence) -> str:
    return f"[승인된 근거: {evidence.card_id}]\n{evidence.content}"


def build_grounded_answer_from_evidence(question: str, evidence: GroundedEvidence) -> str:
    topic = _display_topic(evidence)
    evidence_sentence = _first_complete_sentence(evidence.content)
    usage_hint = _usage_hint(question, evidence.content)
    lead = evidence_sentence if _starts_with_topic(evidence_sentence, topic) else f"{topic}는 {evidence_sentence}"
    if usage_hint:
        return f"{lead} 따라서 {usage_hint}"
    return lead


def _display_topic(evidence: GroundedEvidence) -> str:
    aliases = [alias for alias in evidence.aliases if alias.strip()]
    if "-" not in evidence.term and any(alias.lower() == evidence.term.lower() for alias in aliases):
        return evidence.term
    spaced_alias = next(
        (
            alias for alias in aliases
            if " " in alias
            and re.search(r"[a-z]", alias, re.I)
            and "?" not in alias
            and len(alias.split()) <= 3
        ),
        None,
    )
    if spaced_alias:
        if spaced_alias.lower() == "react key":
            return "React key"
        return spaced_alias
    topic = evidence.term.replace("-", " ")
    if topic.lower().startswith("react "):
        return "React " + topic[6:]
    return topic


def _first_complete_sentence(text: str) -> str:
    normalized = re.sub(r"\s+", " ", text.strip())
    sentences = re.split(r"(?<=[.!?。])\s+", normalized)
    first = next((sentence.strip() for sentence in sentences if sentence.strip()), normalized)
    if not first:
        return "승인된 학습 근거에서 확인된 개념입니다."
    if first[-1] not in ".!?。":
        first += "."
    return first


def _starts_with_topic(sentence: str, topic: str) -> bool:
    normalized_sentence = sentence.lower().replace("`", "").strip()
    normalized_topic = topic.lower().replace("`", "").strip()
    return normalized_sentence.startswith(f"{normalized_topic}는") or normalized_sentence.startswith(
        f"{normalized_topic}은"
    )


def _usage_hint(question: str, evidence_content: str) -> str:
    combined = f"{question} {evidence_content}".lower()
    if any(token in combined for token in ("list", "리스트", "목록", "index", "인덱스")):
        return "리스트에서는 항목을 안정적으로 식별할 수 있는 고유 ID 같은 값을 key로 사용하고, 삽입·삭제·정렬이 가능한 목록에서 배열 index처럼 매번 바뀔 수 있는 값은 피하는 것이 좋습니다."
    return ""


def _meaningful_tokens(text: str) -> set[str]:
    generic = {"그리고", "하지만", "따라서", "대한", "위한", "있습니다", "합니다", "기능", "설명"}
    return {
        token for token in re.findall(r"[a-z0-9+#.]+|[가-힣]{2,}", text.lower())
        if token not in generic
    }
