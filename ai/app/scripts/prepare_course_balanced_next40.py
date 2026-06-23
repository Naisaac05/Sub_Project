from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from app.scripts.concept_example_verifiers import verifier_readiness
from app.scripts.initialize_validation_policy_v212 import validate_payload_quality
from app.scripts.migrate_rag_cards import extract_questions
from app.scripts.patch_payload_batch_v214 import CARD_ROOT
from app.scripts.prepare_course_balanced_batch_v212 import COURSES, build_question_index


ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "reports" / "course_balanced_next40_preparation_2026-06-15.json"
ELIGIBLE = {"draft", "approved", "approved_locked", "needs_revision"}


def _checksums() -> dict[str, str]:
    return {str(path.relative_to(CARD_ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in CARD_ROOT.rglob("*.json")}


def _priority(card: dict) -> tuple:
    quality = validate_payload_quality(card)
    return _priority_from_quality(card, quality)


def _priority_from_quality(card: dict, quality: dict) -> tuple:
    return (
        len(quality["reasons"]),
        quality["fake_example_score"],
        quality["same_reason_ratio"],
        -quality["payload_length"],
        card["card_id"],
    )


def build_report(
    cards: list[dict],
    questions: dict[str, dict],
    excluded_ids: set[str],
    *,
    per_course: int = 8,
) -> dict:
    prepared: dict[str, dict] = {}
    skipped: dict[str, str] = {}
    by_course = {course: {"candidate": 0, "prepared": 0, "source_missing": 0, "factcheck_not_ready": 0} for course in COURSES}
    for course in COURSES:
        ranked = sorted((c for c in cards if c.get("category") == course and c["card_id"] not in excluded_ids), key=_priority)
        for card in ranked:
            if by_course[course]["prepared"] >= per_course:
                break
            if card.get("review", {}).get("card_status") != "draft":
                skipped[card["card_id"]] = "ineligible_status"
                continue
            by_course[course]["candidate"] += 1
            source_id = next(iter(card.get("source_question_ids") or []), None)
            question = questions.get(source_id)
            if question is None:
                skipped[card["card_id"]] = "source_missing"
                by_course[course]["source_missing"] += 1
                continue
            quality = validate_payload_quality(card)
            prepared[card["card_id"]] = {
                "course_id": question["course_id"],
                "test_id": question["test_id"],
                "question_id": question["question_id"],
                "source_question_id": source_id,
                "current_quality": quality,
                "execution_verifier_readiness": verifier_readiness(card["card_id"]),
                "preparation_scope": "payloads.* only",
                "factcheck_status": "preparation_backlog",
            }
            by_course[course]["prepared"] += 1
    return {
        "candidate_count": sum(item["candidate"] for item in by_course.values()),
        "prepared_count": len(prepared),
        "skipped_count": len(skipped),
        "PREPARATION_BACKLOG": prepared,
        "skip_reasons": skipped,
        "cards_by_course": by_course,
        "execution_performed": False,
        "card_files_modified": False,
        "patches_ready_created": False,
        "json_validation_result": "pass",
        "production_hit_diff": 0.0,
        "production_loo_diff": 0.0,
        "content_hit_diff": 0.0,
        "content_loo_diff": 0.0,
        "retrieval_changed": 0,
    }


def processed_ids_from_report(report: dict) -> set[str]:
    ids: set[str] = set()
    for key in ("approved_cards", "approved_card_ids"):
        value = report.get(key, [])
        ids.update(value if isinstance(value, list) else value.keys() if isinstance(value, dict) else [])
    return ids


def _previous_ids(current_report: Path = REPORT) -> set[str]:
    ids: set[str] = set()
    for path in (ROOT / "reports").glob("*.json"):
        if path.resolve() == current_report.resolve():
            continue
        try:
            report = json.loads(path.read_text(encoding="utf-8-sig"))
        except (json.JSONDecodeError, UnicodeDecodeError):
            continue
        ids.update(processed_ids_from_report(report))
    return ids


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepare a balanced, read-only RAG card candidate batch")
    parser.add_argument("--per-course", type=int, default=8)
    parser.add_argument("--output", type=Path, default=REPORT)
    args = parser.parse_args()
    if args.per_course <= 0:
        parser.error("--per-course must be positive")

    before = _checksums()
    cards = [json.loads(path.read_text(encoding="utf-8-sig")) for path in CARD_ROOT.rglob("*.json")]
    report = build_report(
        cards,
        build_question_index(extract_questions()),
        _previous_ids(args.output),
        per_course=args.per_course,
    )
    serialized = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    json.loads(serialized)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(serialized, encoding="utf-8")
    report["card_files_modified"] = before != _checksums()
    if report["card_files_modified"]:
        raise RuntimeError("concepts_v2 modified during preparation")
    print(json.dumps({k: v for k, v in report.items() if k != "PREPARATION_BACKLOG"}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
