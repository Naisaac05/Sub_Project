import unittest

from app.schemas import AiGenerateRequest
from app.schemas.rag_card import CardStatus, PayloadStatus, RagCard, RagReview
from app.workflow.course_scope import (
    cards_for_course,
    normalize_course_id,
    resolve_course_scope,
)
from app.workflow.intent import FreeQuestionIntent


def _card(card_id, category, source_ids, aliases=None):
    return RagCard(
        card_id=card_id,
        category=category,
        term=card_id,
        aliases=aliases or [],
        source_question_ids=source_ids,
        review=RagReview(
            card_status=CardStatus.APPROVED,
            payload_status={"CONCEPT_DEFINITION": PayloadStatus.APPROVED},
        ),
    )


class CourseScopeGateTest(unittest.TestCase):
    def test_ai_generate_request_accepts_course_scope_metadata(self):
        request = AiGenerateRequest(
            user_answer="@Transactional이 뭐야?",
            course_id="frontend",
            test_id="10",
            question_id="4",
            source_question_id="frontend:4",
        )

        self.assertEqual(request.course_id, "frontend")
        self.assertEqual(request.test_id, "10")
        self.assertEqual(request.question_id, "4")
        self.assertEqual(request.source_question_id, "frontend:4")

    def test_normalize_course_id_maps_existing_course_names(self):
        self.assertEqual(normalize_course_id("JAVA-BASIC"), "java")
        self.assertEqual(normalize_course_id("spring-backend"), "spring")
        self.assertEqual(normalize_course_id("react"), "frontend")
        self.assertEqual(normalize_course_id("coding-test"), "algorithm")

    def test_cards_for_course_uses_category_and_source_question_prefix(self):
        cards = [
            _card("frontend-useeffect", "frontend", ["frontend:4"]),
            _card("spring-transactional", "spring", ["spring:2"]),
        ]

        self.assertEqual(
            [card.card_id for card in cards_for_course(cards, "frontend")],
            ["frontend-useeffect"],
        )

    def test_out_of_course_technical_card_match_is_blocked(self):
        cards = [
            _card("spring-transactional", "spring", ["spring:2"], aliases=["@Transactional", "Transactional"]),
        ]
        decision = resolve_course_scope(
            query="@Transactional이 뭐야?",
            course_id="frontend",
            intent=FreeQuestionIntent("concept_definition", "latest_question_only", "@Transactional", 0.95, False, "definition"),
            approved_cards=cards,
        )

        self.assertEqual(decision.scope, "out_of_course_tech")
        self.assertEqual(decision.matched_card_id, "spring-transactional")


if __name__ == "__main__":
    unittest.main()
