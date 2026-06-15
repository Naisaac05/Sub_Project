from __future__ import annotations

import argparse
import json
import statistics
import sys
import time
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.rag.documents import load_concept_cards
from app.rag.retriever import LexicalRetrieverAdapter
from app.schemas.rag_card import RagCard
from app.schemas.rag_card import CardStatus, PayloadStatus
from scripts.audit_rag_cards_v2 import audit_cards


DEFAULT_ROOT = ROOT / "app" / "knowledge" / "concepts_v2"
GENERATED_INTENTS = ("CONCEPT_DEFINITION", "ANSWER_REASON", "WRONG_ANSWER_REASON")


@dataclass(frozen=True)
class ShadowSample:
    question: str
    expected_intent: str
    expected_card_id: str


SHADOW_SAMPLES = [
    ShadowSample("What is React key?", "CONCEPT_DEFINITION", "frontend-react-key"),
    ShadowSample("What is Java equals?", "CONCEPT_DEFINITION", "java-equals"),
    ShadowSample("What is Spring cache?", "CONCEPT_DEFINITION", "spring-spring-question-59"),
    ShadowSample("What is BFS breadth first search?", "CONCEPT_DEFINITION", "algorithm-breadth-first-search"),
    ShadowSample("What is JSX expression?", "CONCEPT_DEFINITION", "frontend-jsx-expression"),
    ShadowSample("Why is React key correct?", "ANSWER_REASON", "frontend-react-key"),
    ShadowSample("Why is Java equals correct?", "ANSWER_REASON", "java-equals"),
    ShadowSample("Why is Spring cache correct?", "ANSWER_REASON", "spring-spring-question-59"),
    ShadowSample("Why is BFS breadth first search correct?", "ANSWER_REASON", "algorithm-breadth-first-search"),
    ShadowSample("Why is JSX expression correct?", "ANSWER_REASON", "frontend-jsx-expression"),
    ShadowSample("Why is index wrong instead of React key?", "WRONG_ANSWER_REASON", "frontend-react-key"),
    ShadowSample("Why is == wrong for Java equals?", "WRONG_ANSWER_REASON", "java-equals"),
    ShadowSample("Why is no caching wrong for Spring cache?", "WRONG_ANSWER_REASON", "spring-spring-question-59"),
    ShadowSample("Why is stack wrong for BFS breadth first search?", "WRONG_ANSWER_REASON", "algorithm-breadth-first-search"),
    ShadowSample("Why are parentheses wrong for JSX expression?", "WRONG_ANSWER_REASON", "frontend-jsx-expression"),
]


def classify_shadow_intent(question: str) -> str:
    lowered = question.lower()
    if "wrong" in lowered:
        return "WRONG_ANSWER_REASON"
    if lowered.startswith("why") and "correct" in lowered:
        return "ANSWER_REASON"
    return "CONCEPT_DEFINITION"


def payload_available(card: RagCard, intent: str) -> bool:
    if card.review.card_status != CardStatus.APPROVED:
        return False
    if card.review.payload_status.get(intent) != PayloadStatus.APPROVED:
        return False
    payload = getattr(card.payloads, intent, None)
    if payload is None:
        return False
    if intent == "CONCEPT_DEFINITION":
        return bool(payload.content.strip())
    if intent == "ANSWER_REASON":
        return bool(payload.why_correct.strip())
    if intent == "WRONG_ANSWER_REASON":
        return payload.per_option is not None
    return False


def select_approval_candidates(cards: list[RagCard]) -> dict[str, dict[str, object]]:
    quality = audit_cards(cards)
    retriever = LexicalRetrieverAdapter(card_loader=lambda: cards)
    result: dict[str, dict[str, object]] = {}
    for card in cards:
        reasons = list(quality.risks_by_card.get(card.card_id, []))
        started = time.perf_counter()
        top = retriever.retrieve(card.term, limit=1)
        latency_ms = (time.perf_counter() - started) * 1000
        top1_id = top[0].concept_id if top else None
        top1_score = top[0].score if top else 0.0
        if top1_id != card.card_id:
            reasons.append("self_term_top1_mismatch")
        missing = [intent for intent in GENERATED_INTENTS if not payload_available(card, intent)]
        if missing:
            reasons.append("payload_missing")
        result[card.card_id] = {
            "approved_candidate": not reasons,
            "reasons": reasons,
            "top1_card_id": top1_id,
            "top1_score": top1_score,
            "retrieval_latency_ms": latency_ms,
            "missing_payloads": missing,
        }
    return result


def _score_distribution(scores: list[float]) -> dict[str, float]:
    if not scores:
        return {key: 0.0 for key in ("min", "p25", "median", "p75", "p90", "max")}
    ordered = sorted(scores)
    percentile = lambda value: ordered[min(len(ordered) - 1, round((len(ordered) - 1) * value))]
    return {
        "min": ordered[0],
        "p25": percentile(0.25),
        "median": statistics.median(ordered),
        "p75": percentile(0.75),
        "p90": percentile(0.90),
        "max": ordered[-1],
    }


def evaluate_shadow(cards: list[RagCard], samples: list[ShadowSample] = SHADOW_SAMPLES) -> dict[str, object]:
    by_id = {card.card_id: card for card in cards}
    retriever = LexicalRetrieverAdapter(card_loader=lambda: cards)
    results: list[dict[str, object]] = []
    latencies: list[float] = []
    scores: list[float] = []
    for sample in samples:
        intent = classify_shadow_intent(sample.question)
        started = time.perf_counter()
        retrieved = retriever.retrieve(sample.question, limit=5)
        latency_ms = (time.perf_counter() - started) * 1000
        latencies.append(latency_ms)
        top = retrieved[0] if retrieved else None
        top_card = by_id.get(top.concept_id) if top else None
        score = top.score if top else 0.0
        scores.append(score)
        intent_match = intent == sample.expected_intent
        card_match = bool(top and top.concept_id == sample.expected_card_id)
        has_payload = bool(top_card and payload_available(top_card, intent))
        fast_path = intent_match and card_match and has_payload
        results.append({
            "question": sample.question,
            "expected_intent": sample.expected_intent,
            "classified_intent": intent,
            "expected_card_id": sample.expected_card_id,
            "top1_card_id": top.concept_id if top else None,
            "top1_score": score,
            "top5": [
                {"rank": rank, "card_id": item.concept_id, "score": item.score}
                for rank, item in enumerate(retrieved, start=1)
            ],
            "payload_available": has_payload,
            "fast_path": fast_path,
            "fallback": not fast_path,
            "llm_called": not fast_path,
            "retrieval_latency_ms": latency_ms,
        })
    fast_count = sum(bool(item["fast_path"]) for item in results)
    fallback_count = len(results) - fast_count
    total = len(results) or 1
    return {
        "sample_count": len(results),
        "fast_path_count": fast_count,
        "fallback_count": fallback_count,
        "card_hit_rate": fast_count / total,
        "shadow_fast_path_success_rate": fast_count / total,
        "fallback_rate": fallback_count / total,
        "expected_ollama_call_reduction_rate": fast_count / total,
        "average_retrieval_latency_ms": statistics.mean(latencies) if latencies else 0.0,
        "top1_score_distribution": _score_distribution(scores),
        "results": results,
    }


def build_report(cards: list[RagCard]) -> dict[str, object]:
    candidates = select_approval_candidates(cards)
    shadow = evaluate_shadow(cards)
    candidate_count = sum(bool(item["approved_candidate"]) for item in candidates.values())
    candidate_problems = [
        {"card_id": card_id, **details}
        for card_id, details in candidates.items()
        if not details["approved_candidate"]
    ]
    shadow_problems = [
        {
            "expected_card_id": item["expected_card_id"],
            "question": item["question"],
            "top1_card_id": item["top1_card_id"],
            "top1_score": item["top1_score"],
        }
        for item in shadow["results"]
        if not item["fast_path"]
    ]
    problem_cards: list[dict[str, object]] = candidate_problems
    known_problem_ids = {str(item["card_id"]) for item in problem_cards}
    for item in shadow_problems:
        expected_id = str(item["expected_card_id"])
        if expected_id not in known_problem_ids:
            problem_cards.append({
                "card_id": expected_id,
                "approved_candidate": False,
                "reasons": ["shadow_top1_mismatch"],
                "top1_card_id": item["top1_card_id"],
                "top1_score": item["top1_score"],
            })
            known_problem_ids.add(expected_id)
    ready = candidate_count == len(cards) and shadow["fallback_count"] == 0
    return {
        "card_count": len(cards),
        "approved_candidate_count": candidate_count,
        "approved_candidates": candidates,
        "shadow": shadow,
        "problem_cards_top10": problem_cards[:10],
        "shadow_problem_cases": shadow_problems[:10],
        "production_readiness": "READY" if ready else "NOT_READY",
        "safety": {
            "card_status_changed": False,
            "payload_status_changed": False,
            "active_card_store_changed": False,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Read-only RAG cards v2 shadow Fast Path validation")
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = build_report(load_concept_cards(args.root))
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
