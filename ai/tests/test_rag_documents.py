import unittest

from app.rag.documents import load_concept_cards


class RagDocumentsTest(unittest.TestCase):
    def test_loads_concept_cards_with_sections(self):
        cards = load_concept_cards()
        n_plus_one = next(card for card in cards if card.concept_id == "spring-n-plus-one")

        self.assertEqual(n_plus_one.metadata["category"], "spring-jpa")
        self.assertIn("핵심 설명", n_plus_one.sections)
        self.assertIn("추가 쿼리", n_plus_one.sections["핵심 설명"])


if __name__ == "__main__":
    unittest.main()

