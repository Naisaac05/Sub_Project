from __future__ import annotations

import unittest

from app.scripts import prepare_next_course_balanced_batch_v212 as prepare


class PrepareNextCourseBalancedBatchV212Test(unittest.TestCase):
    def test_build_packet_selects_four_per_course_and_excludes_completed(self):
        cards = []
        questions = {}
        for course in prepare.COURSES:
            for index in range(5):
                source_id = f"{course}:{index}"
                cards.append({
                    "card_id": f"{course}-{index}",
                    "category": course,
                    "source_question_ids": [source_id],
                    "review": {"card_status": "approved"},
                    "payloads": {},
                })
                questions[source_id] = {
                    "course_id": course, "test_id": index, "question_id": index,
                    "content": "질문", "options": ["오답", "정답"], "correct_answer": 1, "correct_text": "정답",
                }

        report = prepare.build_packet(cards, questions, {f"{course}-0" for course in prepare.COURSES})

        self.assertEqual(report["candidate_count"], 20)
        self.assertEqual(report["source_packet_count"], 20)
        self.assertEqual(report["payload_draft_count"], 0)
        self.assertTrue(all(value["candidate"] == 4 for value in report["cards_by_course"].values()))
        self.assertFalse(report["card_files_modified"])


if __name__ == "__main__":
    unittest.main()
