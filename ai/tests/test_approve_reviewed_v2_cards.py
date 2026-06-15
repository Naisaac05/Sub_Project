import copy
import unittest

from app.scripts.approve_reviewed_v2_cards import review_errors


class ApproveReviewedV2CardsTest(unittest.TestCase):
    def test_review_rejects_incomplete_wrong_answer_reasons(self):
        card = _card()
        card["payloads"]["WRONG_ANSWER_REASON"]["per_option"]["option_0"]["reason"] = ""

        self.assertIn("incomplete_wrong_answer_reasons", review_errors(card))

    def test_review_accepts_complete_draft(self):
        self.assertEqual(review_errors(_card()), [])


def _card():
    return {
        "card_id": "java-primitive",
        "source_question_ids": ["java:3"],
        "aliases": ["primitive", "기본 자료형", "primitive type"],
        "retrieval": {
            "embedding_text": "primitive 기본 자료형 primitive type java int String",
            "boost_keywords": ["primitive", "기본 자료형", "String"],
        },
        "payloads": {
            "CONCEPT_DEFINITION": {"content": "Java 기본 자료형을 구체적으로 설명합니다."},
            "ANSWER_REASON": {"why_correct": "String은 참조 자료형이므로 정답입니다."},
            "WRONG_ANSWER_REASON": {
                "common_mistakes": ["String을 기본 자료형으로 혼동합니다."],
                "per_option": {"option_0": {"text": "int", "reason": "int는 기본 자료형입니다."}},
            },
        },
        "review": {
            "card_status": "draft",
            "payload_status": {
                "CONCEPT_DEFINITION": "draft",
                "ANSWER_REASON": "draft",
                "WRONG_ANSWER_REASON": "draft",
            },
        },
    }


if __name__ == "__main__":
    unittest.main()
