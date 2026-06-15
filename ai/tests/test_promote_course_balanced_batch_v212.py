from __future__ import annotations

import unittest

from app.scripts import promote_course_balanced_batch_v212 as promote


class PromoteCourseBalancedBatchV212Test(unittest.TestCase):
    def test_review_promotes_all_fact_checked_executable_examples(self):
        drafts = {
            card_id: {
                "course_id": card_id.split("-")[0],
                "test_id": 1,
                "question_id": 1,
                "source_question_id": f"{card_id.split('-')[0]}:1",
                "payloads": {
                    "WRONG_ANSWER_REASON": {"per_option": {}},
                    "EXAMPLE_REQUEST": {"code_example": "values = [1]\nvalues.append(2)\nassert values[-1] == 2"},
                },
                "fact_check_notes": ["검증 완료"],
                "patch_reason": "품질 개선",
                "quality_review": {"reasons": []},
            }
            for card_id in promote.REVIEW_DECISIONS
        }

        report = promote.build_ready_report(drafts)

        self.assertEqual(report["ready_count"], 10)
        self.assertEqual(report["backlog_count"], 0)
        self.assertEqual(set(report["PATCHES_READY"]), set(promote.REVIEW_DECISIONS))
        self.assertFalse(report["execution_performed"])
        self.assertFalse(report["card_files_modified"])

    def test_repaired_examples_demonstrate_framework_and_algorithm_behavior(self):
        source = promote.load_source_drafts()
        examples = {
            card_id: draft["payloads"]["EXAMPLE_REQUEST"]["code_example"]
            for card_id, draft in source.items()
        }

        self.assertIn("@CacheEvict", examples["spring-spring-question-59"])
        self.assertIn("cache.get", examples["spring-spring-question-59"])
        self.assertIn("ProceedingJoinPoint", examples["spring-aop"])
        self.assertIn("TestRenderer", examples["frontend-react-key"])
        self.assertIn("mounts", examples["frontend-react-key"])
        self.assertIn("useRef", examples["frontend-useref"])
        self.assertIn("renders === 1", examples["frontend-useref"])
        self.assertIn("def merge", examples["algorithm-divide"])
        self.assertNotIn("sorted(", examples["algorithm-divide"])


if __name__ == "__main__":
    unittest.main()
