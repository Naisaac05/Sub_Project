import copy
import unittest

from app.scripts.enrich_mapped_draft_cards import enrich_card
from app.scripts.migrate_rag_cards import Question


def card(status="draft", card_id="java-primitive"):
    return {
        "card_id": card_id,
        "category": "java",
        "term": "primitive",
        "aliases": ["primitive"],
        "source_question_ids": ["java:3"],
        "retrieval": {"embedding_text": "primitive", "boost_keywords": ["primitive"], "intent_types": []},
        "payloads": {},
        "review": {"card_status": status, "payload_status": {"CONCEPT_DEFINITION": status}},
        "created_at": "2026-06-12T00:00:00Z",
        "updated_at": "2026-06-12T00:00:00Z",
    }


QUESTION = Question(
    id=3,
    content="다음 중 Java의 기본 자료형(primitive type)이 아닌 것은?",
    options=["int", "boolean", "String", "double"],
    correct_answer=2,
    test_id=1,
)


class EnrichMappedDraftCardsTest(unittest.TestCase):
    def test_approved_card_is_unchanged(self):
        original = card(status="approved", card_id="java-equals")
        result, changed = enrich_card(copy.deepcopy(original), [QUESTION], "2026-06-13T00:00:00Z")
        self.assertFalse(changed)
        self.assertEqual(result, original)

    def test_unmapped_draft_is_unchanged(self):
        original = card()
        result, changed = enrich_card(copy.deepcopy(original), [], "2026-06-13T00:00:00Z")
        self.assertFalse(changed)
        self.assertEqual(result, original)

    def test_mapped_draft_gets_korean_payloads_without_changing_review(self):
        original = card()
        result, changed = enrich_card(copy.deepcopy(original), [QUESTION], "2026-06-13T00:00:00Z")
        self.assertTrue(changed)
        self.assertEqual(result["review"], original["review"])
        self.assertEqual(result["created_at"], original["created_at"])
        self.assertIn("기본 자료형", result["payloads"]["CONCEPT_DEFINITION"]["content"])
        self.assertIn("String", result["payloads"]["ANSWER_REASON"]["why_correct"])
        self.assertTrue(all(key.startswith("option_") for key in result["payloads"]["WRONG_ANSWER_REASON"]["per_option"]))
        self.assertLessEqual(len(result["retrieval"]["embedding_text"]), 150)
        expected = " ".join(dict.fromkeys([
            result["term"],
            *result["aliases"][:3],
            result["category"],
            *result["retrieval"]["boost_keywords"],
        ]))
        self.assertEqual(result["retrieval"]["embedding_text"], expected)


if __name__ == "__main__":
    unittest.main()
