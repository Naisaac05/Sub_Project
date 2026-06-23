from __future__ import annotations

import unittest

from app.scripts.prepare_course_balanced_next40 import (
    _priority_from_quality,
    build_report,
    processed_ids_from_report,
)


class PrepareCourseBalancedNext40Test(unittest.TestCase):
    def test_priority_prefers_fewer_quality_reasons(self):
        clean = _priority_from_quality(
            {"card_id": "clean"},
            {"reasons": [], "same_reason_ratio": 0.0, "fake_example_score": 0.0, "payload_length": 500},
        )
        flawed = _priority_from_quality(
            {"card_id": "flawed"},
            {
                "reasons": ["fake_example", "duplicate_score_over_0_25"],
                "same_reason_ratio": 1.0,
                "fake_example_score": 1.0,
                "payload_length": 700,
            },
        )

        self.assertLess(clean, flawed)

    def test_processed_ids_exclude_only_completed_approvals(self):
        report = {
            "patched_cards": ["java-equals"],
            "approved_cards": ["frontend-react-key"],
            "PREPARATION_BACKLOG": {"python-mro": {"factcheck_status": "preparation_backlog"}},
        }

        processed = processed_ids_from_report(report)

        self.assertEqual(processed, {"frontend-react-key"})

    def test_report_selects_four_drafts_per_course_and_excludes_processed_cards(self):
        cards = []
        questions = {}
        excluded_ids = set()
        for course in ("java", "spring", "frontend", "python", "algorithm"):
            for index in range(6):
                card_id = f"{course}-{index}"
                source_id = f"{course}:{index}"
                status = "approved" if index == 0 else "draft"
                cards.append({
                    "card_id": card_id,
                    "category": course,
                    "term": card_id,
                    "aliases": [],
                    "source_question_ids": [source_id],
                    "retrieval": {"embedding_text": card_id, "boost_keywords": [], "intent_types": []},
                    "payloads": {},
                    "review": {"card_status": status},
                })
                questions[source_id] = {
                    "course_id": course,
                    "test_id": "test",
                    "question_id": str(index),
                    "content": "question",
                    "options": [],
                    "correct_answer": 0,
                    "correct_text": "answer",
                }
            excluded_ids.add(f"{course}-1")

        report = build_report(cards, questions, excluded_ids=excluded_ids, per_course=4)

        selected_ids = set(report["PREPARATION_BACKLOG"])
        self.assertEqual(report["prepared_count"], 20)
        self.assertFalse(selected_ids & excluded_ids)
        self.assertFalse(any(card_id.endswith("-0") for card_id in selected_ids))
        self.assertEqual(
            {course: values["prepared"] for course, values in report["cards_by_course"].items()},
            {"java": 4, "spring": 4, "frontend": 4, "python": 4, "algorithm": 4},
        )

    def test_report_selects_eight_per_course_without_writing_cards(self):
        cards = []
        questions = {}
        for course in ("java", "spring", "frontend", "python", "algorithm"):
            for index in range(10):
                card_id = f"{course}-{index}"
                source_id = f"{course}:{index}"
                cards.append({
                    "card_id": card_id,
                    "category": course,
                    "term": card_id,
                    "aliases": [],
                    "source_question_ids": [source_id],
                    "retrieval": {"embedding_text": card_id, "boost_keywords": [], "intent_types": []},
                    "payloads": {},
                    "review": {"card_status": "draft"},
                })
                questions[source_id] = {
                    "course_id": course, "test_id": "test", "question_id": str(index),
                    "content": "question", "options": [], "correct_answer": 0, "correct_text": "answer",
                }

        report = build_report(cards, questions, excluded_ids=set())

        self.assertEqual(report["candidate_count"], 40)
        self.assertEqual(report["prepared_count"], 40)
        self.assertEqual({k: v["prepared"] for k, v in report["cards_by_course"].items()}, {
            "java": 8, "spring": 8, "frontend": 8, "python": 8, "algorithm": 8,
        })
        self.assertFalse(report["execution_performed"])
        self.assertFalse(report["card_files_modified"])
        self.assertFalse(report["patches_ready_created"])
        self.assertEqual(report["retrieval_changed"], 0)


if __name__ == "__main__":
    unittest.main()
