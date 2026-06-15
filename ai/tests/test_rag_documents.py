import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from app.rag.documents import ConceptCard, load_concept_cards


class RagDocumentsTest(unittest.TestCase):
    def test_loads_concept_cards(self):
        cards = load_concept_cards()
        self.assertTrue(len(cards) > 0, "No cards loaded. Migration script may not have finished or directories are empty.")

        card = cards[0]
        self.assertIsNotNone(card.concept_id)
        self.assertIsNotNone(card.metadata)
        self.assertIsNotNone(card.payloads)
        self.assertIsNotNone(card.review)

    def test_loads_legacy_markdown_and_v2_json_cards_together(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "legacy.md").write_text(
                "---\nid: legacy-card\ncategory: legacy\n---\n\n# Legacy\n\n## 핵심 설명\nLegacy body.",
                encoding="utf-8",
            )
            (root / "v2.json").write_text(
                """{
  "card_id": "v2-card",
  "category": "v2",
  "term": "V2",
  "aliases": [],
  "source_question_ids": [],
  "retrieval": {},
  "payloads": {},
  "review": {},
  "related_card_ids": [],
  "tags": []
}""",
                encoding="utf-8",
            )

            cards = load_concept_cards(root)

        self.assertEqual({card.concept_id for card in cards}, {"legacy-card", "v2-card"})
        legacy = next(card for card in cards if card.concept_id == "legacy-card")
        self.assertIsInstance(legacy, ConceptCard)
        self.assertEqual(legacy.sections["핵심 설명"], "Legacy body.")


if __name__ == "__main__":
    unittest.main()
