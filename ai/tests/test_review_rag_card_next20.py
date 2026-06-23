import copy
import unittest

from app.scripts.review_rag_card_next20 import build_approved_card, review_candidate


class ReviewRagCardNext20Tests(unittest.TestCase):
    def setUp(self):
        self.card = {
            "card_id": "java-sample",
            "category": "java",
            "term": "sample",
            "aliases": ["sample"],
            "source_question_ids": ["java:1"],
            "retrieval": {"embedding_text": "sample", "embedding_hash": "", "boost_keywords": ["sample"], "intent_types": []},
            "payloads": {
                "CONCEPT_DEFINITION": {"content": "충분히 구체적인 개념 설명입니다. 실행 맥락과 동작 원리를 함께 설명합니다.", "examples": []},
                "ANSWER_REASON": {"why_correct": "정답인 이유를 동작 원리에 따라 설명합니다.", "key_points": ["핵심"]},
                "WRONG_ANSWER_REASON": {"common_mistakes": ["혼동"], "per_option": {"1": {"text": "오답", "reason": "동작 원리와 다릅니다."}}},
            },
            "review": {"card_status": "draft", "payload_status": {"CONCEPT_DEFINITION": "draft", "ANSWER_REASON": "draft", "WRONG_ANSWER_REASON": "draft"}, "approved_at": None, "reviewer": None, "rejected_reason": None},
            "related_card_ids": [], "tags": [], "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-01-01T00:00:00Z",
        }

    def test_review_requires_execution_success(self):
        result = review_candidate(self.card, {"passed": False, "status": "unavailable", "reason": "dependency_missing"})
        self.assertFalse(result["eligible"])
        self.assertIn("execution_not_verified", result["reasons"])

    def test_approval_changes_only_review_fields(self):
        approved = build_approved_card(self.card, approved_at="2026-06-21T00:00:00Z")
        before = copy.deepcopy(self.card)
        before.pop("review")
        after = copy.deepcopy(approved)
        after.pop("review")
        self.assertEqual(before, after)
        self.assertEqual(approved["review"]["card_status"], "approved")
        self.assertTrue(all(value == "approved" for value in approved["review"]["payload_status"].values()))


if __name__ == "__main__":
    unittest.main()
