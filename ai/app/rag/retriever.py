from dataclasses import dataclass
import re

from app.rag.documents import ConceptCard, load_concept_cards

STOPWORDS = {
    "문제",
    "이유",
    "때문",
    "때문에",
    "완전히",
    "관계없는",
}

@dataclass(frozen=True)
class RetrievedContext:
    concept_id: str
    title: str
    content: str
    score: float
    metadata: dict[str, str]


def retrieve_context(query: str, limit: int = 5) -> list[RetrievedContext]:
    query_tokens = set(tokenize(query))
    if not query_tokens:
        return []

    scored: list[RetrievedContext] = []
    for card in load_concept_cards():
        score = score_card(card, query_tokens)
        if score <= 0:
            continue
        scored.append(
            RetrievedContext(
                concept_id=card.concept_id,
                title=card.title,
                content=_format_card_context(card),
                score=score,
                metadata=card.metadata,
            )
        )

    scored.sort(key=lambda item: item.score, reverse=True)
    return scored[:limit]


def tokenize(text: str) -> list[str]:
    tokens = re.findall(r"[A-Za-z0-9+#.]+|[가-힣]{2,}", text.lower())
    expanded: list[str] = []
    for token in tokens:
        if token in STOPWORDS:
            continue
        expanded.append(token)
        if token == "n":
            expanded.append("n+1")
    return expanded


def score_card(card: ConceptCard, query_tokens: set[str]) -> float:
    text_tokens = set(tokenize(card.searchable_text))
    overlap = query_tokens & text_tokens
    if not overlap:
        return 0.0

    keyword_text = card.sections.get("평가 키워드", "")
    keyword_tokens = set(tokenize(keyword_text))
    title_tokens = set(tokenize(card.title))
    score = float(len(overlap))
    score += 2.0 * len(query_tokens & keyword_tokens)
    score += 1.5 * len(query_tokens & title_tokens)
    return score


def _format_card_context(card: ConceptCard) -> str:
    sections = "\n\n".join(
        f"## {name}\n{content}" for name, content in card.sections.items()
    )
    return f"# {card.title}\n\n{sections}".strip()
