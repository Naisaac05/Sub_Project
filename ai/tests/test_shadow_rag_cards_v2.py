import unittest

from app.schemas.rag_card import (
    AnswerReasonPayload,
    ConceptDefinitionPayload,
    RagCard,
    RagPayloads,
    RagRetrieval,
    WrongAnswerReasonPayload,
    RagReview,
    CardStatus,
    PayloadStatus,
)
from scripts.shadow_rag_cards_v2 import (
    ShadowSample,
    build_report,
    classify_shadow_intent,
    evaluate_shadow,
    select_approval_candidates,
)


def card(card_id: str, term: str, aliases: list[str] | None = None, complete: bool = True) -> RagCard:
    payloads = RagPayloads(
        CONCEPT_DEFINITION=ConceptDefinitionPayload(content=f"{term} definition"),
        ANSWER_REASON=AnswerReasonPayload(why_correct=f"{term} correct"),
        WRONG_ANSWER_REASON=WrongAnswerReasonPayload(),
    )
    if not complete:
        payloads.ANSWER_REASON = None
    aliases = aliases or [term.replace("-", " "), f"specific {term}"]
    return RagCard(
        card_id=card_id,
        category="test",
        term=term,
        aliases=aliases,
        retrieval=RagRetrieval(
            embedding_text=" ".join([term, *aliases, "test"]),
            boost_keywords=[term, *aliases[:2]],
        ),
        payloads=payloads,
        review=RagReview(
            card_status=CardStatus.APPROVED,
            payload_status={
                "CONCEPT_DEFINITION": PayloadStatus.APPROVED,
                "ANSWER_REASON": PayloadStatus.APPROVED,
                "WRONG_ANSWER_REASON": PayloadStatus.APPROVED,
            },
        ),
    )


class ShadowRagCardsV2Test(unittest.TestCase):
    def test_classifies_generated_intents(self):
        self.assertEqual(classify_shadow_intent("What is React key?"), "CONCEPT_DEFINITION")
        self.assertEqual(classify_shadow_intent("Why is Java equals correct?"), "ANSWER_REASON")
        self.assertEqual(classify_shadow_intent("Why is using == wrong for Java equals?"), "WRONG_ANSWER_REASON")

    def test_candidate_requires_self_top1_and_complete_payloads(self):
        good = card("test-react-key", "react-key")
        incomplete = card("test-java-equals", "java-equals", complete=False)

        result = select_approval_candidates([good, incomplete])

        self.assertTrue(result["test-react-key"]["approved_candidate"])
        self.assertFalse(result["test-java-equals"]["approved_candidate"])
        self.assertIn("payload_missing", result["test-java-equals"]["reasons"])

    def test_shadow_fast_path_and_fallback_metrics(self):
        cards = [card("test-react-key", "react-key")]
        samples = [
            ShadowSample("What is React key?", "CONCEPT_DEFINITION", "test-react-key"),
            ShadowSample("What is missing concept?", "CONCEPT_DEFINITION", "test-missing"),
        ]

        report = evaluate_shadow(cards, samples)

        self.assertEqual(report["sample_count"], 2)
        self.assertEqual(report["fast_path_count"], 1)
        self.assertEqual(report["fallback_count"], 1)
        self.assertEqual(report["expected_ollama_call_reduction_rate"], 0.5)
        self.assertFalse(report["results"][0]["llm_called"])
        self.assertTrue(report["results"][1]["llm_called"])

    def test_draft_card_payload_is_not_a_fast_path_hit(self):
        draft = card("test-react-key", "react-key")
        draft.review.card_status = CardStatus.DRAFT
        samples = [ShadowSample("What is React key?", "CONCEPT_DEFINITION", "test-react-key")]

        report = evaluate_shadow([draft], samples)

        self.assertEqual(report["fast_path_count"], 0)

    def test_report_includes_shadow_missing_card_in_problem_top10(self):
        report = build_report([card("test-react-key", "react-key")])

        problem_ids = {item["card_id"] for item in report["problem_cards_top10"]}

        self.assertIn("spring-spring-question-59", problem_ids)

    def test_shadow_samples_use_existing_spring_cache_card(self):
        from scripts.shadow_rag_cards_v2 import SHADOW_SAMPLES

        spring_samples = [sample for sample in SHADOW_SAMPLES if "Spring cache" in sample.question]

        self.assertEqual(len(spring_samples), 3)
        self.assertEqual({sample.expected_card_id for sample in spring_samples}, {"spring-spring-question-59"})


if __name__ == "__main__":
    unittest.main()
