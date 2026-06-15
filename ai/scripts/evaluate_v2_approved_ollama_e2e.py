from __future__ import annotations

import json
import statistics
import sys
import time
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.rag.documents import CONCEPT_ROOT, load_concept_cards
from app.rag.retriever import LexicalRetrieverAdapter
from app.schemas.rag_card import CardStatus, PayloadStatus, RagCard
from app.scripts.migrate_rag_cards import Question, extract_questions


INTENT = "CONCEPT_DEFINITION"


def select_course_questions(limit_per_category: int = 10) -> list[Question]:
    selected: list[Question] = []
    counts: dict[str, int] = {}
    for question in extract_questions():
        if counts.get(question.category, 0) >= limit_per_category:
            continue
        selected.append(question)
        counts[question.category] = counts.get(question.category, 0) + 1
    return selected


def evaluate(cards: list[RagCard], questions: list[Question], log_path: Path | None = None) -> dict[str, object]:
    retriever = LexicalRetrieverAdapter(card_loader=lambda: cards)
    by_id = {card.card_id: card for card in cards}
    rows = []
    for question in questions:
        source_id = f"{question.category}:{question.id}"
        started = time.perf_counter()
        results = retriever.retrieve(question.content, limit=3)
        latency_ms = (time.perf_counter() - started) * 1000
        top = results[0] if results else None
        top_card = by_id.get(top.concept_id) if top else None
        top1_relevant = bool(top_card and source_id in top_card.source_question_ids)
        top3_relevant = any(source_id in by_id[item.concept_id].source_question_ids for item in results if item.concept_id in by_id)
        fast_path = bool(
            top1_relevant
            and top_card
            and top_card.review.card_status == CardStatus.APPROVED
            and top_card.review.payload_status.get(INTENT) == PayloadStatus.APPROVED
            and top_card.payloads.CONCEPT_DEFINITION
            and top_card.payloads.CONCEPT_DEFINITION.content.strip()
        )
        quality = _quality(top_card, top1_relevant)
        row = {
            "question_id": source_id,
            "question": question.content,
            "correct_answer": question.correct_text,
            "top1_relevant": top1_relevant,
            "top3_relevant": top3_relevant,
            "fast_path_hit": fast_path,
            "fallback_reason": None if fast_path else ("top1_miss" if not top1_relevant else "payload_not_approved"),
            "latency_ms": latency_ms,
            "response_quality_score": quality,
            "top3": [{"card_id": item.concept_id, "score": item.score} for item in results],
        }
        rows.append(row)
        if log_path:
            with log_path.open("a", encoding="utf-8") as handle:
                handle.write(json.dumps(row, ensure_ascii=False) + "\n")
    total = len(rows) or 1
    return {
        "question_count": len(rows),
        "top1_relevance_rate": sum(row["top1_relevant"] for row in rows) / total,
        "top3_relevance_rate": sum(row["top3_relevant"] for row in rows) / total,
        "fast_path_hit_rate": sum(row["fast_path_hit"] for row in rows) / total,
        "fallback_rate": sum(not row["fast_path_hit"] for row in rows) / total,
        "average_latency_ms": statistics.mean(row["latency_ms"] for row in rows),
        "average_response_quality_score": statistics.mean(row["response_quality_score"] for row in rows),
        "fallback_reasons": {
            reason: sum(row["fallback_reason"] == reason for row in rows)
            for reason in ("top1_miss", "payload_not_approved")
        },
        "rows": rows,
    }


def _quality(card: RagCard | None, relevant: bool) -> int:
    if not card or not relevant or not card.payloads.CONCEPT_DEFINITION:
        return 1
    text = card.payloads.CONCEPT_DEFINITION.content
    return 5 if len(text) >= 180 and any(char >= "가" and char <= "힣" for char in text) else 4 if len(text) >= 100 else 3


def main() -> int:
    questions = select_course_questions()
    date = datetime.now().strftime("%Y-%m-%d")
    log_path = ROOT / "logs" / f"course_shadow_test_{date}.log"
    log_path.write_text("", encoding="utf-8")
    v2 = [
        card for card in load_concept_cards(CONCEPT_ROOT)
        if isinstance(card, RagCard) and card.review.card_status == CardStatus.APPROVED
    ]
    report = {
        "evaluation_note": "v2 approved 카드가 답하지 못한 질문은 Ollama fallback 대상으로 집계합니다.",
        "runtime_policy": "v2_approved_then_ollama",
        "v2_approved_card_count": len(v2),
        "v2": evaluate(v2, questions, log_path),
    }
    output = ROOT / "reports" / f"v2_approved_ollama_e2e_{date}.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
