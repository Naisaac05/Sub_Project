from __future__ import annotations

import json
from pathlib import Path

from app.scripts.initialize_validation_policy_v212 import validate_payload_quality
from app.scripts.migrate_rag_cards import extract_questions
from app.scripts.patch_payload_batch_v214 import CARD_ROOT
from app.scripts.prepare_course_balanced_batch_v212 import (
    COURSES,
    build_question_index,
    select_balanced_candidates,
)


ROOT = Path(__file__).resolve().parents[2]
APPLIED_REPORT = ROOT / "reports" / "course_balanced_batch_v212_applied_2026-06-14.json"
REPORT = ROOT / "reports" / "course_balanced_next20_factcheck_preparation_2026-06-14.json"


def build_packet(cards: list[dict], questions: dict[str, dict], excluded_ids: set[str]) -> dict:
    selected, selection_skips = select_balanced_candidates(cards, excluded_ids, per_course=4)
    by_id = {card["card_id"]: card for card in cards}
    packets, skipped, skip_reasons = {}, [], {}
    by_course = {
        course: {"candidate": 0, "source_ready": 0, "source_missing": 0}
        for course in COURSES
    }
    for item in selected:
        card_id, course, source_id = item["card_id"], item["category"], item["source_id"]
        by_course[course]["candidate"] += 1
        question = questions.get(source_id)
        if question is None:
            skipped.append(card_id)
            skip_reasons[card_id] = "source_missing"
            by_course[course]["source_missing"] += 1
            continue
        card = by_id[card_id]
        packets[card_id] = {
            "course_id": question["course_id"],
            "test_id": question["test_id"],
            "question_id": question["question_id"],
            "source_question_id": source_id,
            "question": question["content"],
            "options": question["options"],
            "correct_answer_index": question["correct_answer"],
            "correct_answer": question["correct_text"],
            "current_quality": validate_payload_quality(card),
            "preparation_requirements": [
                "실제 테스트 문제와 정답을 기준으로 payload 결함을 확인한다.",
                "선택지별 오답 이유와 실제 동작 예제를 fact-check 후 작성한다.",
            ],
        }
        by_course[course]["source_ready"] += 1
    return {
        "candidate_count": len(selected),
        "source_packet_count": len(packets),
        "payload_draft_count": 0,
        "skipped_count": len(skipped),
        "FACTCHECK_SOURCE_PACKETS": packets,
        "PREPARATION_BACKLOG": list(packets),
        "cards_by_course": by_course,
        "skipped_cards": skipped,
        "skip_reasons": skip_reasons,
        "selection_skip_reasons": selection_skips,
        "execution_performed": False,
        "card_files_modified": False,
        "patches_ready_created": False,
        "json_validation_result": "pass",
    }


def main() -> int:
    cards = [json.loads(path.read_text(encoding="utf-8-sig")) for path in CARD_ROOT.rglob("*.json")]
    applied = json.loads(APPLIED_REPORT.read_text(encoding="utf-8"))
    report = build_packet(cards, build_question_index(extract_questions()), set(applied["patched_cards"]))
    serialized = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    json.loads(serialized)
    REPORT.write_text(serialized, encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "FACTCHECK_SOURCE_PACKETS"}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
