import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch

from app.workflow.embedding_intent import intent_from_label
from app.workflow.v2_approved_fast_path import resolve_v2_approved_fast_path


CARD_ROOT = Path(__file__).resolve().parents[1] / "app" / "knowledge" / "concepts_v2"


class ApprovedCardCatalogTest(unittest.TestCase):
    def test_every_approved_definition_question_returns_its_approved_answer(self):
        approved_cards = []
        for path in CARD_ROOT.rglob("*.json"):
            card = json.loads(path.read_text(encoding="utf-8-sig"))
            if card.get("review", {}).get("card_status") == "approved":
                approved_cards.append(card)

        self.assertTrue(approved_cards)
        with patch.dict(os.environ, {"AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED": "true"}):
            for card in approved_cards:
                question = (
                    card.get("source_question")
                    or card.get("source_question_text")
                    or f"{card['term']}이란 무엇인가요?"
                )
                expected = card["payloads"]["CONCEPT_DEFINITION"]["content"].strip()
                decision = resolve_v2_approved_fast_path(
                    question,
                    intent_from_label("CONCEPT_DEFINITION", question, 0.99),
                    random_value=0.0,
                )

                with self.subTest(card_id=card["card_id"], question=question):
                    self.assertTrue(decision.hit, decision.reason)
                    self.assertEqual(decision.card_id, card["card_id"])
                    self.assertEqual(decision.answer, expected)


if __name__ == "__main__":
    unittest.main()
