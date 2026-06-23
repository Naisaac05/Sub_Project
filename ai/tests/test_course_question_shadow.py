import unittest
from collections import Counter

from app.rag.documents import CONCEPT_ROOT, load_concept_cards
from app.schemas.rag_card import CardStatus, RagCard
from scripts.evaluate_v2_approved_ollama_e2e import evaluate, select_course_questions


class CourseQuestionShadowTest(unittest.TestCase):
    def test_selects_ten_questions_per_course(self):
        questions = select_course_questions()
        self.assertEqual(len(questions), 50)
        self.assertEqual(Counter(question.category for question in questions), {
            "java": 10,
            "spring": 10,
            "frontend": 10,
            "python": 10,
            "algorithm": 10,
        })

    def test_approved_shadow_average_response_quality_reaches_target(self):
        cards = [
            card for card in load_concept_cards(CONCEPT_ROOT)
            if isinstance(card, RagCard) and card.review.card_status == CardStatus.APPROVED
        ]

        report = evaluate(cards, select_course_questions())

        self.assertGreaterEqual(report["average_response_quality_score"], 4.5)


if __name__ == "__main__":
    unittest.main()
