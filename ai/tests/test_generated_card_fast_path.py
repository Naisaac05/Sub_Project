import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from app.rag.documents import load_concept_cards
from app.rag.retriever import RetrievedContext
from app.workflow.intent import FreeQuestionIntent
from app.schemas import AiGenerateRequest
from app.workflow.nodes import generate_answer_node
from app.workflow.lightweight_answers import resolve_lightweight_answer
from app.workflow.runner import run_review_workflow
from app.workflow.state import ReviewWorkflowState


class GeneratedCardFastPathTest(unittest.TestCase):
    def test_generated_card_uses_korean_core_section(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            card = root / "auto-review-circuit-breaker.md"
            card.write_text(
                """---
id: auto-review-circuit-breaker
category: auto-review
difficulty: intermediate
version: admin-approved-candidate
last_updated: 2026-05-20
---

# circuit breaker

## 핵심 설명
Circuit breaker approved by admin.

## 검색 키워드
- circuit breaker
""",
                encoding="utf-8",
            )
            cards = load_concept_cards(root)
            intent = FreeQuestionIntent("concept_definition", "latest_question_only", "circuit breaker")

            with patch(
                "app.workflow.lightweight_answers._concept_cards_by_id",
                return_value={cards[0].concept_id: cards[0]},
            ):
                answer = resolve_lightweight_answer(
                    "circuit breaker가 뭐야?",
                    intent,
                    matched_concept_id="auto-review-circuit-breaker",
                )

        self.assertIsNotNone(answer)
        self.assertEqual(answer.answer, "Circuit breaker approved by admin.")
        self.assertEqual(answer.route, "generated_card_fast_path")

    def test_low_confidence_static_alias_with_unbacked_concept_id_returns_none(self):
        intent = FreeQuestionIntent(
            "concept_definition",
            "latest_question_only",
            "API",
            confidence=0.7,
        )

        answer = resolve_lightweight_answer(
            "API가 뭐야?",
            intent,
            matched_concept_id="missing-generated-card",
        )

        self.assertIsNone(answer)

    def test_generation_node_uses_retrieved_generated_card_without_calling_generator(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            card = root / "auto-review-circuit-breaker.md"
            card.write_text(
                """---
id: auto-review-circuit-breaker
category: auto-review
difficulty: intermediate
version: admin-approved-candidate
last_updated: 2026-05-20
---

# circuit breaker

## 핵심 설명
Circuit breaker approved by admin.

## 검색 키워드
- circuit breaker
""",
                encoding="utf-8",
            )
            cards = load_concept_cards(root)
            state = ReviewWorkflowState(
                mode="free-question",
                request=AiGenerateRequest(user_answer="circuit breaker가 뭐야?"),
                contexts=[
                    RetrievedContext(
                        concept_id="auto-review-circuit-breaker",
                        title="circuit breaker",
                        content="Circuit breaker approved by admin.",
                        score=5.0,
                        metadata={"version": "admin-approved-candidate"},
                    )
                ],
                free_question_intent=FreeQuestionIntent(
                    "concept_definition",
                    "latest_question_only",
                    "circuit breaker",
                ),
            )

            with patch(
                "app.workflow.lightweight_answers._concept_cards_by_id",
                return_value={cards[0].concept_id: cards[0]},
            ):
                result = generate_answer_node(
                    state,
                    generator=lambda **kwargs: (_ for _ in ()).throw(
                        AssertionError("generator should not be called")
                    ),
                )

        self.assertEqual(result.answer, "Circuit breaker approved by admin.")
        self.assertEqual(result.model_used, "lightweight-template")
        self.assertEqual(result.route, "generated_card_fast_path")

    def test_response_metadata_keeps_retrieved_generated_card_id(self):
        retrieved = RetrievedContext(
            concept_id="auto-review-circuit-breaker",
            title="circuit breaker",
            content="circuit breaker approved card.",
            score=5.0,
            metadata={"version": "admin-approved-candidate"},
        )

        with (
            patch("app.workflow.nodes.retrieve_context", return_value=[retrieved]),
            patch(
                "app.workflow.lightweight_answers._concept_card_answer_for",
                return_value="circuit breaker\ub294 \uc7a5\uc560 \uc804\ud30c\ub97c \uc904\uc774\ub294 \ud328\ud134\uc785\ub2c8\ub2e4.",
            ),
        ):
            response = run_review_workflow(
                "free-question",
                AiGenerateRequest(user_answer="What is circuit breaker?"),
                generator=lambda **kwargs: (_ for _ in ()).throw(
                    AssertionError("generator should not be called")
                ),
            )

        self.assertEqual(response.route, "generated_card_fast_path")
        self.assertIn("auto-review-circuit-breaker", response.retrieved_concept_ids)


if __name__ == "__main__":
    unittest.main()
