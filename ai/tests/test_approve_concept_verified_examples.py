from __future__ import annotations

import copy
import unittest

from app.scripts import approve_concept_verified_examples as approve


class ApproveConceptVerifiedExamplesTest(unittest.TestCase):
    def test_candidate_changes_only_example_and_review(self):
        original = _card()

        candidate = approve.build_approved_candidate(
            original,
            {"code_example": "line1\nline2\nassert true;", "explanation": "동작 검증"},
            approved_at="2026-06-15T00:00:00Z",
        )

        self.assertEqual(candidate["retrieval"], original["retrieval"])
        self.assertEqual(candidate["aliases"], original["aliases"])
        self.assertEqual(candidate["payloads"]["ANSWER_REASON"], original["payloads"]["ANSWER_REASON"])
        self.assertNotEqual(candidate["payloads"]["EXAMPLE_REQUEST"], original["payloads"]["EXAMPLE_REQUEST"])
        self.assertEqual(candidate["review"]["card_status"], "approved")
        self.assertEqual(candidate["review"]["payload_status"]["EXAMPLE_REQUEST"], "approved")

    def test_acceptance_rejects_failed_execution(self):
        reasons = approve.acceptance_reasons(
            execution={"passed": False, "reason": "dependency_not_available"},
            lock_reasons=[],
            retrieval_reasons=[],
        )

        self.assertEqual(reasons, ["dependency_not_available"])

    def test_acceptance_allows_verified_example(self):
        reasons = approve.acceptance_reasons(
            execution={"passed": True, "reason": None},
            lock_reasons=[],
            retrieval_reasons=[],
        )

        self.assertEqual(reasons, [])


def _card():
    return {
        "card_id": "sample",
        "category": "java",
        "term": "sample",
        "aliases": ["sample"],
        "source_question_ids": ["java:1"],
        "retrieval": {"embedding_text": "sample", "boost_keywords": ["sample"], "intent_types": []},
        "payloads": {
            "ANSWER_REASON": {"why_correct": "근거", "key_points": []},
            "EXAMPLE_REQUEST": {"code_example": "old", "explanation": "old"},
        },
        "review": {
            "card_status": "draft",
            "payload_status": {"ANSWER_REASON": "draft", "EXAMPLE_REQUEST": "draft"},
            "approved_at": None,
            "reviewer": None,
            "rejected_reason": None,
        },
        "related_card_ids": [],
        "created_at": "2026-06-12T00:00:00Z",
        "updated_at": "2026-06-12T00:00:00Z",
    }


if __name__ == "__main__":
    unittest.main()
