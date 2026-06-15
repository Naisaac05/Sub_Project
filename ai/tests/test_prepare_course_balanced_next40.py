from __future__ import annotations

import unittest

from app.scripts.prepare_course_balanced_next40 import build_report


class PrepareCourseBalancedNext40Test(unittest.TestCase):
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
