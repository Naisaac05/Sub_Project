from __future__ import annotations

import unittest

from app.scripts import prepare_course_balanced_batch_v212 as prepare


class PrepareFrozenBatchV212Test(unittest.TestCase):
    def test_build_question_index_uses_dynamic_course_and_test_id(self):
        questions = [
            prepare.Question(id=3, content="Java question", options=["A", "B"], correct_answer=1, test_id=1),
            prepare.Question(id=65, content="Frontend question", options=["A", "B"], correct_answer=0, test_id=8),
        ]

        index = prepare.build_question_index(questions)

        self.assertEqual(index["java:3"]["course_id"], "java")
        self.assertEqual(index["java:3"]["test_id"], 1)
        self.assertEqual(index["frontend:65"]["question_id"], 65)

    def test_select_balanced_candidates_excludes_previous_and_selects_two_per_course(self):
        cards = []
        for course in prepare.COURSES:
            for index in range(3):
                cards.append({
                    "card_id": f"{course}-{index}",
                    "category": course,
                    "source_question_ids": [f"{course}:{index}"],
                    "review": {"card_status": "approved"},
                })

        selected, skipped = prepare.select_balanced_candidates(
            cards,
            excluded_ids={f"{course}-0" for course in prepare.COURSES},
            per_course=2,
        )

        self.assertEqual({course: 2 for course in prepare.COURSES}, {
            course: sum(item["category"] == course for item in selected)
            for course in prepare.COURSES
        })
        self.assertTrue(all(item["card_id"] not in {f"{course}-0" for course in prepare.COURSES} for item in selected))
        self.assertEqual(skipped, {})

    def test_course_report_separates_missing_source_and_quality_failure(self):
        cards = [
            {
                "card_id": "java-good",
                "category": "java",
                "source_question_ids": ["java:3"],
                "review": {"card_status": "approved"},
                "payloads": {},
            },
            {
                "card_id": "java-missing",
                "category": "java",
                "source_question_ids": ["java:999"],
                "review": {"card_status": "approved"},
                "payloads": {},
            },
        ]
        selected = [
            {"card_id": card["card_id"], "category": card["category"], "source_id": card["source_question_ids"][0]}
            for card in cards
        ]
        questions = {
            "java:3": {
                "course_id": "java", "test_id": 1, "question_id": 3,
                "content": "질문", "options": ["오답", "정답"], "correct_answer": 1, "correct_text": "정답",
            }
        }

        artifact = prepare.build_course_artifact(selected, {card["card_id"]: card for card in cards}, questions)

        self.assertEqual(artifact["cards_by_course"]["java"]["source_missing"], 1)
        self.assertIn("java-missing", artifact["skip_reasons"])

    def test_preparation_contains_no_ready_or_locked_fields(self):
        card = {
            "card_id": "algorithm",
            "payloads": {"WRONG_ANSWER_REASON": {"per_option": {}}},
        }

        artifact = prepare.build_course_artifact([], {"algorithm": card}, {})

        self.assertNotIn("PATCHES_READY", artifact)
        self.assertFalse(artifact["execution_performed"])
        self.assertFalse(artifact["card_files_modified"])

    def test_build_payload_has_behavior_example_and_distinct_wrong_reasons(self):
        question = {
            "content": "시간 복잡도 O(n)의 의미는?",
            "options": ["O(1)", "O(n)", "O(n²)", "O(log n)"],
            "correct_answer": 1,
            "correct_text": "O(n)",
        }

        payloads = prepare.build_payload("algorithm-8", question, prepare.SPECS["algorithm-8"])
        reasons = [item["reason"] for item in payloads["WRONG_ANSWER_REASON"]["per_option"].values()]
        code = payloads["EXAMPLE_REQUEST"]["code_example"]

        self.assertEqual(len(set(reasons)), 3)
        self.assertGreaterEqual(len([line for line in code.splitlines() if line.strip()]), 3)
        self.assertIn("assert", code)
        self.assertNotEqual(
            payloads["CONCEPT_DEFINITION"]["examples"][0],
            payloads["EXAMPLE_REQUEST"]["explanation"],
        )


if __name__ == "__main__":
    unittest.main()
