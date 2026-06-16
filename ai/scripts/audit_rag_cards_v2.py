from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.rag.documents import load_concept_cards
from app.rag.retriever import LexicalRetrieverAdapter
from app.schemas.rag_card import RagCard


DEFAULT_ROOT = ROOT / "app" / "knowledge" / "concepts_v2"
BROAD_TERMS = {
    "api", "key", "cache", "loading", "latency", "concept", "data", "test",
    "server", "java", "spring", "react", "frontend", "python", "algorithm",
    "function", "class", "method", "에서", "중", "다음",
    # 너무 일반적인 자료형/컨테이너 단어 — 단독 term이면 다른 주제와 검색 충돌을 일으킨다
    # (예: term="string" 인 java-string 이 파이썬 f-string 질문에 끼어든 0.5점 충돌)
    "string", "list", "array", "object", "value", "type",
    "variable", "number", "boolean", "integer",
}
GENERIC_TOKENS = BROAD_TERMS | {
    "역할", "방법", "설명", "올바른", "의미", "사용", "개념", "질문", "card",
}
PRIORITY_QUERIES = [
    "React key",
    "Java equals",
    "API",
    "key",
    "cache",
    "loading",
    "latency",
    "concept",
]
MOJIBAKE_MARKERS = ("筌", "吏", "媛", "뒗", "쓣", "섎", "댁", "쒕", "곗")
KNOWN_CROSS_CONCEPT_SOURCE_PAIRS = {
    frozenset({"frontend:69", "frontend:77"}),
    frozenset({"python:96", "python:104"}),
    frozenset({"python:96", "python:109"}),
}


@dataclass(frozen=True)
class AuditReport:
    approved_candidate_ids: list[str]
    held_card_ids: list[str]
    risks_by_card: dict[str, list[str]]
    broad_term_card_ids: list[str]


def normalize(value: str) -> str:
    return re.sub(r"[^a-z0-9가-힣]+", "", value.lower())


def tokens(value: str) -> list[str]:
    return re.findall(r"[a-z0-9+#@.-]+|[가-힣]{2,}", value.lower())


def audit_cards(cards: list[RagCard]) -> AuditReport:
    term_counts = Counter(normalize(card.term) for card in cards)
    risks_by_card: dict[str, list[str]] = {}
    broad_ids: list[str] = []
    for card in cards:
        risks: list[str] = []
        normalized_term = normalize(card.term)
        term_tokens = tokens(card.term)
        alias_tokens = {token for alias in card.aliases for token in tokens(alias)}
        keyword_tokens = {token for keyword in card.retrieval.boost_keywords for token in tokens(keyword)}
        embedding_tokens = set(tokens(card.retrieval.embedding_text))
        distinctive = (set(term_tokens) | alias_tokens | keyword_tokens) - GENERIC_TOKENS
        retrieval_fields = " ".join([
            card.term,
            *card.aliases,
            card.retrieval.embedding_text,
            *card.retrieval.boost_keywords,
        ])

        if normalized_term in {normalize(term) for term in BROAD_TERMS}:
            risks.append("broad_term")
            broad_ids.append(card.card_id)
        if term_counts[normalized_term] > 1:
            risks.append("duplicate_normalized_term")
        if not card.aliases or all(normalize(alias) == normalized_term for alias in card.aliases):
            risks.append("weak_aliases")
        if len(distinctive) < 2:
            risks.append("low_distinctive_retrieval_terms")
        if len(embedding_tokens - GENERIC_TOKENS) < 2:
            risks.append("generic_embedding_text")
        if len(set(card.retrieval.boost_keywords)) < 3:
            risks.append("weak_boost_keywords")
        if "??" in retrieval_fields or any(marker in retrieval_fields for marker in MOJIBAKE_MARKERS):
            risks.append("mojibake_retrieval_fields")
        source_ids = set(card.source_question_ids)
        if any(pair <= source_ids for pair in KNOWN_CROSS_CONCEPT_SOURCE_PAIRS):
            risks.append("suspicious_cross_concept_merge")
        if risks:
            risks_by_card[card.card_id] = risks

    held = sorted(risks_by_card)
    approved = sorted(card.card_id for card in cards if card.card_id not in risks_by_card)
    return AuditReport(approved, held, risks_by_card, sorted(broad_ids))


def audit_retrieval_queries(cards: list[RagCard], queries: list[str] = PRIORITY_QUERIES) -> dict[str, list[dict[str, object]]]:
    retriever = LexicalRetrieverAdapter(card_loader=lambda: cards)
    output: dict[str, list[dict[str, object]]] = {}
    for query in queries:
        output[query] = [
            {
                "rank": rank,
                "card_id": result.concept_id,
                "term": result.title,
                "score": result.score,
            }
            for rank, result in enumerate(retriever.retrieve(query, limit=5), start=1)
        ]
    return output


def card_details(cards: list[RagCard], ids: list[str]) -> list[dict[str, object]]:
    by_id = {card.card_id: card for card in cards}
    return [
        {
            "card_id": card_id,
            "category": by_id[card_id].category,
            "term": by_id[card_id].term,
            "aliases": by_id[card_id].aliases,
            "embedding_text": by_id[card_id].retrieval.embedding_text,
            "boost_keywords": by_id[card_id].retrieval.boost_keywords,
        }
        for card_id in ids
    ]


def build_payload(cards: list[RagCard]) -> dict[str, object]:
    report = audit_cards(cards)
    return {
        "card_count": len(cards),
        "approved_candidate_count": len(report.approved_candidate_ids),
        "held_count": len(report.held_card_ids),
        "broad_term_risk_count": len(report.broad_term_card_ids),
        "approved_candidate_ids": report.approved_candidate_ids,
        "held_cards": [
            {**detail, "risks": report.risks_by_card[detail["card_id"]]}
            for detail in card_details(cards, report.held_card_ids)
        ],
        "broad_term_cards": card_details(cards, report.broad_term_card_ids),
        "retrieval_top5": audit_retrieval_queries(cards),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Read-only concepts_v2 quality audit")
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    payload = build_payload(load_concept_cards(args.root))
    rendered = json.dumps(payload, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
